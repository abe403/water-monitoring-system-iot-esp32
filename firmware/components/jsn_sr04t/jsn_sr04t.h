#pragma once

#include <cstdint>
#include <vector>

#include "esphome/core/component.h"
#include "esphome/components/sensor/sensor.h"
#include "esphome/components/uart/uart.h"

namespace esphome {
namespace jsn_sr04t {

enum Model {
  JSN_SR04T,
  AJ_SR04M,
};

enum class FrameResult {
  OK,
  CHECKSUM_MISMATCH,
};

// Pure, host-testable frame parser — no UART, no ESPHome state, no ESP_LOG.
// buf must point at exactly 4 bytes: [0xFF header, dist_hi, dist_lo, checksum].
// Kept free-standing (not a method) so `firmware/test/` can link and unit-test
// it on the CI host without pulling in the ESPHome runtime. See jsn_sr04t.cpp
// for why the two Model variants use different checksum formulas.
FrameResult parse_frame(const uint8_t buf[4], Model model, uint16_t *out_distance_mm);

class Jsnsr04tComponent : public sensor::Sensor, public PollingComponent, public uart::UARTDevice {
 public:
  void set_model(Model model) { this->model_ = model; }
  // Optional: wire a diagnostic sensor to make checksum failures visible instead
  // of silently logged (ESP_LOGW alone is invisible in production — see the
  // project gap report, firmware bug #3). Reports a running total, not a rate;
  // compute a rate server-side from the interarrival of updates.
  void set_checksum_error_sensor(sensor::Sensor *s) { this->checksum_error_sensor_ = s; }

  // ========== INTERNAL METHODS ==========
  void update() override;
  void loop() override;
  void dump_config() override;

 protected:
  void check_buffer_();

  Model model_{JSN_SR04T};
  sensor::Sensor *checksum_error_sensor_{nullptr};
  uint32_t checksum_errors_{0};

  std::vector<uint8_t> buffer_;
};

}  // namespace jsn_sr04t
}  // namespace esphome
