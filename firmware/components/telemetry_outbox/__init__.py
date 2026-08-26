import esphome.codegen as cg
import esphome.config_validation as cv
from esphome.components import sensor
from esphome.components.deep_sleep import DeepSleepComponent
from esphome.const import CONF_ID, CONF_TRIGGER_ID

CODEOWNERS = ["@abe403"]
DEPENDENCIES = ["mqtt", "deep_sleep"]
AUTO_LOAD = ["sensor"]

CONF_DISTANCE_SENSOR = "distance_sensor"
CONF_TEMPERATURE_SENSOR = "temperature_sensor"
CONF_RSSI_SENSOR = "rssi_sensor"
CONF_CHECKSUM_ERROR_SENSOR = "checksum_error_sensor"
CONF_DEEP_SLEEP_ID = "deep_sleep_id"
CONF_DEVICE_ID = "device_id"
CONF_WAKE_BUDGET = "wake_budget"

telemetry_outbox_ns = cg.esphome_ns.namespace("telemetry_outbox")
TelemetryOutbox = telemetry_outbox_ns.class_("TelemetryOutbox", cg.PollingComponent)

CONFIG_SCHEMA = cv.COMPONENT_SCHEMA.extend(
    {
        cv.GenerateID(): cv.declare_id(TelemetryOutbox),
        cv.Required(CONF_DEVICE_ID): cv.string_strict,
        cv.Required(CONF_DISTANCE_SENSOR): cv.use_id(sensor.Sensor),
        cv.Optional(CONF_TEMPERATURE_SENSOR): cv.use_id(sensor.Sensor),
        cv.Optional(CONF_RSSI_SENSOR): cv.use_id(sensor.Sensor),
        cv.Optional(CONF_CHECKSUM_ERROR_SENSOR): cv.use_id(sensor.Sensor),
        cv.Required(CONF_DEEP_SLEEP_ID): cv.use_id(DeepSleepComponent),
        cv.Optional(CONF_WAKE_BUDGET, default="45s"): cv.positive_time_period_milliseconds,
    }
).extend(cv.polling_component_schema("3s"))


async def to_code(config):
    var = cg.new_Pvariable(config[CONF_ID])
    await cg.register_component(var, config)

    cg.add(var.set_device_id(config[CONF_DEVICE_ID]))
    cg.add(var.set_wake_budget_ms(config[CONF_WAKE_BUDGET]))

    distance = await cg.get_variable(config[CONF_DISTANCE_SENSOR])
    cg.add(var.set_distance_sensor(distance))

    if CONF_TEMPERATURE_SENSOR in config:
        temp = await cg.get_variable(config[CONF_TEMPERATURE_SENSOR])
        cg.add(var.set_temperature_sensor(temp))

    if CONF_RSSI_SENSOR in config:
        rssi = await cg.get_variable(config[CONF_RSSI_SENSOR])
        cg.add(var.set_rssi_sensor(rssi))

    if CONF_CHECKSUM_ERROR_SENSOR in config:
        err = await cg.get_variable(config[CONF_CHECKSUM_ERROR_SENSOR])
        cg.add(var.set_checksum_error_sensor(err))

    deep_sleep = await cg.get_variable(config[CONF_DEEP_SLEEP_ID])
    cg.add(var.set_deep_sleep(deep_sleep))
