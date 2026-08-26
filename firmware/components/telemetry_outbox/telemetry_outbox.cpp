#include "telemetry_outbox.h"

#include "esphome/core/application.h"
#include "esphome/core/hal.h"
#include "esphome/core/log.h"
#include "esphome/components/mqtt/mqtt_client.h"

#if defined(USE_ESP32)
#include "esp_attr.h"
#include "esp_random.h"
#endif

namespace esphome {
namespace telemetry_outbox {

static const char *const TAG = "telemetry_outbox";
static const uint8_t WIRE_VERSION = 1;

// RTC_NOINIT_ATTR: placed in RTC slow memory, preserved across deep sleep,
// not re-initialized by the C runtime on wake (a plain `static` here would be
// zeroed every wake by crt0 startup code). Content is only trustworthy after
// checking `magic` in setup(); a real power loss (not deep sleep) leaves this
// as garbage, which the magic check correctly detects and reinitializes from.
static RTC_NOINIT_ATTR OutboxRing g_ring;

static uint32_t random_boot_id_() {
#if defined(USE_ESP32)
  return esp_random();
#else
  return micros() ^ 0xA5A5A5A5u;
#endif
}

void TelemetryOutbox::setup() {
  this->topic_telemetry_ = "wtm/v1/" + this->device_id_ + "/tel";
  this->topic_ack_ = "wtm/v1/" + this->device_id_ + "/ack";
  this->wake_started_ms_ = millis();

  if (g_ring.magic != OUTBOX_MAGIC) {
    ESP_LOGW(TAG, "RTC ring uninitialized or lost (power cycle) - starting a new boot epoch");
    g_ring.magic = OUTBOX_MAGIC;
    g_ring.boot_id = random_boot_id_();
    g_ring.head = 0;
    g_ring.acked_through = 0;
    g_ring.overflow_dropped = 0;
  } else {
    ESP_LOGI(TAG, "RTC ring resumed: boot_id=%u head=%u acked_through=%u pending=%u overflow_dropped=%u",
             g_ring.boot_id, g_ring.head, g_ring.acked_through,
             g_ring.head - g_ring.acked_through, g_ring.overflow_dropped);
  }

  if (this->temperature_sensor_ != nullptr) {
    this->temperature_sensor_->add_on_state_callback([this](float v) {
      this->last_temp_c_ = v;
      this->have_temp_ = true;
    });
  }
  if (this->rssi_sensor_ != nullptr) {
    this->rssi_sensor_->add_on_state_callback([this](float v) {
      this->last_rssi_dbm_ = v;
      this->have_rssi_ = true;
    });
  }
  if (this->distance_sensor_ != nullptr) {
    this->distance_sensor_->add_on_state_callback([this](float v) { this->append_record_(); });
  }

  mqtt::global_mqtt_client->subscribe(
      this->topic_ack_,
      [this](const std::string &topic, const std::string &payload) {
        this->on_ack_payload(reinterpret_cast<const uint8_t *>(payload.data()), payload.size());
      },
      1 /* QoS */);
}

void TelemetryOutbox::update() {
  // PollingComponent tick - nothing to do here; records are appended from the
  // distance sensor's on_state callback so a record always carries a real
  // measurement. Kept as a PollingComponent (rather than plain Component)
  // only so this shows up with a sensible interval in dump_config/logs.
}

void TelemetryOutbox::append_record_() {
  if (this->distance_sensor_ == nullptr || !this->distance_sensor_->has_state())
    return;

  uint32_t seq = g_ring.head;
  if (seq - g_ring.acked_through >= RING_CAPACITY) {
    g_ring.overflow_dropped++;
    ESP_LOGE(TAG, "outbox full and oldest record still unacked - overwriting (overflow_dropped=%u)",
              g_ring.overflow_dropped);
  }

  TelemetryRecord &rec = g_ring.records[seq % RING_CAPACITY];
  rec.boot_id = g_ring.boot_id;
  rec.seq = seq;
  rec.epoch_s = static_cast<uint32_t>(time(nullptr));  // 0 if SNTP has not synced yet
  rec.distance_mm = static_cast<int16_t>(this->distance_sensor_->state * 1000.0f);
  rec.temp_c_x10 = this->have_temp_ ? static_cast<int16_t>(this->last_temp_c_ * 10.0f) : INT16_MIN;
  rec.rssi_dbm = this->have_rssi_ ? static_cast<int16_t>(this->last_rssi_dbm_) : 0;
  rec.checksum_errors_total =
      this->checksum_error_sensor_ != nullptr && this->checksum_error_sensor_->has_state()
          ? static_cast<uint16_t>(this->checksum_error_sensor_->state)
          : 0;

  g_ring.head = seq + 1;
  ESP_LOGD(TAG, "buffered record boot=%u seq=%u dist=%dmm (pending=%u)", rec.boot_id, rec.seq,
           rec.distance_mm, g_ring.head - g_ring.acked_through);
}

void TelemetryOutbox::publish_unacked_() {
  if (!mqtt::global_mqtt_client->is_connected())
    return;

  uint32_t pending = g_ring.head - g_ring.acked_through;
  if (pending == 0)
    return;

  // One MQTT publish per unacked record, oldest first, capped per loop() tick
  // so a large backlog cannot monopolize the wake window. QoS 1 guarantees
  // delivery to the broker, which is necessary but not sufficient -
  // retirement from the ring happens only on the application-level ack from
  // the gateway (on_ack_payload), never on QoS 1 alone. See
  // docs/ARCHITECTURE.md, "the durability chain".
  constexpr uint32_t MAX_PER_TICK = 20;
  uint32_t sent = 0;
  for (uint32_t s = g_ring.acked_through; s < g_ring.head && sent < MAX_PER_TICK; s++, sent++) {
    const TelemetryRecord &rec = g_ring.records[s % RING_CAPACITY];
    if (rec.seq != s)
      continue;  // overwritten by the ring before it could be sent; skip - the
                 // gap is real and will show up as a hole in the gateway's
                 // contiguity check, which is the intended, auditable failure
                 // mode (see overflow_dropped).
    mqtt::global_mqtt_client->publish(this->topic_telemetry_,
                                       reinterpret_cast<const char *>(&rec), sizeof(rec),
                                       1 /* QoS */, false /* retain */);
  }
  this->last_publish_attempt_ms_ = millis();
}

void TelemetryOutbox::on_ack_payload(const uint8_t *payload, size_t len) {
  // Wire format: 4-byte little-endian uint32 = highest contiguous seq the
  // gateway has durably committed to Kafka. Deliberately not JSON - this
  // path runs on a microcontroller with no parser already loaded, and a
  // fixed 4-byte payload is trivial to validate.
  if (len != 4) {
    ESP_LOGW(TAG, "malformed ack payload (%u bytes, expected 4)", (unsigned) len);
    return;
  }
  uint32_t through = payload[0] | (payload[1] << 8) | (payload[2] << 16) | (payload[3] << 24);
  if (through > g_ring.acked_through && through <= g_ring.head) {
    g_ring.acked_through = through;
    ESP_LOGD(TAG, "ack received: acked_through=%u (pending now %u)", g_ring.acked_through,
             g_ring.head - g_ring.acked_through);
  }
}

void TelemetryOutbox::update_sleep_gate_() {
  bool caught_up = g_ring.acked_through >= g_ring.head;
  bool budget_exceeded = (millis() - this->wake_started_ms_) >= this->wake_budget_ms_;

  bool should_prevent = !caught_up && !budget_exceeded;
  if (should_prevent && !this->sleep_currently_prevented_) {
    this->deep_sleep_->prevent_deep_sleep();
    this->sleep_currently_prevented_ = true;
    ESP_LOGD(TAG, "holding wake: %u record(s) still unacked", g_ring.head - g_ring.acked_through);
  } else if (!should_prevent && this->sleep_currently_prevented_) {
    this->deep_sleep_->allow_deep_sleep();
    this->sleep_currently_prevented_ = false;
    if (budget_exceeded && !caught_up) {
      ESP_LOGW(TAG, "wake budget exceeded with %u record(s) still unacked - sleeping anyway, "
                    "data stays buffered for next wake", g_ring.head - g_ring.acked_through);
    }
  }
}

void TelemetryOutbox::loop() {
  if (this->deep_sleep_ == nullptr)
    return;

  // Retry roughly once a second rather than flooding publish() every loop().
  if (millis() - this->last_publish_attempt_ms_ >= 1000) {
    this->publish_unacked_();
  }
  this->update_sleep_gate_();
}

void TelemetryOutbox::dump_config() {
  ESP_LOGCONFIG(TAG, "Telemetry Outbox:");
  ESP_LOGCONFIG(TAG, "  Device ID: %s", this->device_id_.c_str());
  ESP_LOGCONFIG(TAG, "  Telemetry topic: %s", this->topic_telemetry_.c_str());
  ESP_LOGCONFIG(TAG, "  Ack topic: %s", this->topic_ack_.c_str());
  ESP_LOGCONFIG(TAG, "  Ring capacity: %u records", (unsigned) RING_CAPACITY);
  ESP_LOGCONFIG(TAG, "  Wake budget: %u ms", this->wake_budget_ms_);
  ESP_LOGCONFIG(TAG, "  Wire version: %u, record size: %u bytes", WIRE_VERSION, (unsigned) sizeof(TelemetryRecord));
}

}  // namespace telemetry_outbox
}  // namespace esphome
