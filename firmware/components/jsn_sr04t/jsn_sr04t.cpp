#include "jsn_sr04t.h"
#include "esphome/core/helpers.h"
#include "esphome/core/log.h"

// Very basic support for JSN_SR04T V3.0 distance sensor in mode 2, and the
// AJ-SR04M module used on the WaterTank PCB (see hardware/WaterTank.kicad_sch).
//
// Frame layout (both variants): [0xFF header][dist_hi][dist_lo][checksum]
// distance is millimetres, big-endian across the two data bytes.
//
// The two variants disagree on what the checksum covers:
//   JSN_SR04T: checksum = header + dist_hi + dist_lo   (sum includes 0xFF)
//   AJ_SR04M:  checksum = dist_hi + dist_lo              (sum excludes header)
// `model:` defaults to JSN_SR04T in sensor.py. Do not switch it to aj_sr04m
// purely because the BOM says AJ-SR04M — many AJ-SR04M units in "mode 2" speak
// the same header-inclusive checksum as the JSN-SR04T. Determine the correct
// value empirically: flash with the default, watch `checksum_errors` (or the
// verbose log) for a few minutes, and only switch if failures dominate.

namespace esphome {
namespace jsn_sr04t {

static const char *const TAG = "jsn_sr04t.sensor";

FrameResult parse_frame(const uint8_t buf[4], Model model, uint16_t *out_distance_mm) {
  uint8_t checksum = 0;
  switch (model) {
    case JSN_SR04T:
      checksum = buf[0] + buf[1] + buf[2];
      break;
    case AJ_SR04M:
      checksum = buf[1] + buf[2];
      break;
  }

  if (buf[3] != checksum)
    return FrameResult::CHECKSUM_MISMATCH;

  *out_distance_mm = encode_uint16(buf[1], buf[2]);
  return FrameResult::OK;
}

void Jsnsr04tComponent::update() {
  this->write_byte(0x55);
  ESP_LOGV(TAG, "Request read out from sensor");
}

void Jsnsr04tComponent::loop() {
  while (this->available() > 0) {
    uint8_t data;
    this->read_byte(&data);

    ESP_LOGV(TAG, "Read byte from sensor: %x", data);

    if (this->buffer_.empty() && data != 0xFF)
      continue;

    this->buffer_.push_back(data);
    if (this->buffer_.size() == 4)
      this->check_buffer_();
  }
}

void Jsnsr04tComponent::check_buffer_() {
  uint16_t distance_mm = 0;
  FrameResult result = parse_frame(this->buffer_.data(), this->model_, &distance_mm);

  if (result == FrameResult::OK) {
    float meters = distance_mm / 1000.0f;
    ESP_LOGV(TAG, "Distance from sensor: %umm, %.3fm", distance_mm, meters);
    this->publish_state(meters);
  } else {
    this->checksum_errors_++;
    ESP_LOGW(TAG, "checksum failed (total failures: %u)", this->checksum_errors_);
    if (this->checksum_error_sensor_ != nullptr)
      this->checksum_error_sensor_->publish_state(this->checksum_errors_);
  }
  this->buffer_.clear();
}

void Jsnsr04tComponent::dump_config() {
  LOG_SENSOR("", "JSN-SR04T Sensor", this);
  switch (this->model_) {
    case JSN_SR04T:
      ESP_LOGCONFIG(TAG, "  sensor model: jsn_sr04t");
      break;
    case AJ_SR04M:
      ESP_LOGCONFIG(TAG, "  sensor model: aj_sr04m");
      break;
  }
  ESP_LOGCONFIG(TAG, "  checksum errors so far: %u", this->checksum_errors_);
  LOG_UPDATE_INTERVAL(this);
}

}  // namespace jsn_sr04t
}  // namespace esphome
