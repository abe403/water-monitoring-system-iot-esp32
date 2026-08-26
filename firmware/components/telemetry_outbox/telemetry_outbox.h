#pragma once

#include <cstdint>
#include <string>

#include "esphome/core/component.h"
#include "esphome/components/sensor/sensor.h"
#include "esphome/components/deep_sleep/deep_sleep_component.h"

namespace esphome {
namespace telemetry_outbox {

// ---------------------------------------------------------------------------
// On-device store-and-forward buffer. This is the firmware half of the
// project's "zero data loss under ingestion spikes" claim — see
// docs/ARCHITECTURE.md ("The durability chain") for the full hop-by-hop
// argument and platform/ingest-gateway for the server side of the same
// contract.
//
// Deliberately POD, deliberately not object-oriented: TelemetryRecord and
// OutboxRing live in RTC_NOINIT_ATTR memory, which survives deep sleep but is
// restored by the linker as raw bytes with no constructor ever running. A
// vtable pointer, a std::string member, or anything with a non-trivial
// constructor in this memory is undefined behaviour the moment firmware is
// OTA-updated between two sleeps (the vtable layout can change out from under
// already-resident data). See docs/DECISIONS/0005-pod-structs-in-rtc-memory.md.
// This is the one place in the whole system where "no OOD" is the correct
// call, not a shortcut — contrast with platform/domain, which is OOD-heavy on
// purpose. Component here refers only to the *component wrapper* below, which
// is ordinary ESPHome heap-allocated state and is fine to be a class.
// ---------------------------------------------------------------------------

struct __attribute__((packed)) TelemetryRecord {
  uint32_t boot_id;
  uint32_t seq;
  uint32_t epoch_s;      // best-effort wall clock (SNTP); 0 if not yet synced.
                         // Ordering truth is (boot_id, seq), never epoch_s.
  int16_t distance_mm;   // raw ultrasonic reading, uncalibrated
  int16_t temp_c_x10;    // battery NTC temperature * 10; INT16_MIN = unknown
  int16_t rssi_dbm;
  uint16_t checksum_errors_total;  // jsn_sr04t running counter at record time
};
static_assert(sizeof(TelemetryRecord) == 20,
              "record size changed: bump WIRE_VERSION in telemetry_outbox.cpp "
              "and re-check RING_CAPACITY against the RTC memory budget");

constexpr uint32_t OUTBOX_MAGIC = 0x574f5431;  // "WOT1"
// ESP32-C3 has 8KB of RTC slow memory. 300 records * 20B = 6000B, leaving
// headroom for the ring header and other RTC_NOINIT_ATTR users. At the
// nominal 150s wake/sleep cycle that's ~12.5h of buffered outage tolerance —
// see docs/ARCHITECTURE.md for the arithmetic and what happens beyond it
// (detected gap, not silent loss).
constexpr size_t RING_CAPACITY = 300;

struct __attribute__((packed)) OutboxRing {
  uint32_t magic;
  uint32_t boot_id;
  uint32_t head;              // next seq to assign; monotonic, never reset
  uint32_t acked_through;     // highest seq the gateway confirmed durable in
                              // Kafka (application-level ack, stronger than
                              // MQTT PUBACK — see the ADR referenced above)
  uint32_t overflow_dropped;  // unacked records overwritten because the ring
                              // filled; a non-zero value here is real,
                              // reportable loss and must surface as a metric,
                              // never be silent
  TelemetryRecord records[RING_CAPACITY];
};

class TelemetryOutbox : public PollingComponent {
 public:
  void set_distance_sensor(sensor::Sensor *s) { this->distance_sensor_ = s; }
  void set_temperature_sensor(sensor::Sensor *s) { this->temperature_sensor_ = s; }
  void set_rssi_sensor(sensor::Sensor *s) { this->rssi_sensor_ = s; }
  void set_checksum_error_sensor(sensor::Sensor *s) { this->checksum_error_sensor_ = s; }
  void set_deep_sleep(deep_sleep::DeepSleepComponent *ds) { this->deep_sleep_ = ds; }
  void set_device_id(const std::string &id) { this->device_id_ = id; }
  void set_wake_budget_ms(uint32_t ms) { this->wake_budget_ms_ = ms; }

  void setup() override;
  void update() override;
  void loop() override;
  void dump_config() override;
  float get_setup_priority() const override { return setup_priority::AFTER_WIFI; }

  // Called by the MQTT ack-topic subscription callback registered in setup().
  void on_ack_payload(const uint8_t *payload, size_t len);

 protected:
  void append_record_();
  void publish_unacked_();
  void update_sleep_gate_();

  sensor::Sensor *distance_sensor_{nullptr};
  sensor::Sensor *temperature_sensor_{nullptr};
  sensor::Sensor *rssi_sensor_{nullptr};
  sensor::Sensor *checksum_error_sensor_{nullptr};
  deep_sleep::DeepSleepComponent *deep_sleep_{nullptr};
  std::string device_id_;
  std::string topic_telemetry_;
  std::string topic_ack_;

  uint32_t wake_budget_ms_{45000};
  uint32_t wake_started_ms_{0};
  uint32_t last_publish_attempt_ms_{0};
  bool sleep_currently_prevented_{false};

  // Cached latest readings, folded into the next distance-triggered record.
  float last_temp_c_{-1000.0f};
  float last_rssi_dbm_{0.0f};
  bool have_temp_{false};
  bool have_rssi_{false};
};

}  // namespace telemetry_outbox
}  // namespace esphome
