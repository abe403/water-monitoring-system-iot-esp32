# IoT Water Monitoring and Pump Control System

This project is a professional-grade IoT solution for remote water tank level monitoring and automated pump control. Built on the ESP32-C3 architecture and powered by ESPHome, it provides seamless integration with Home Assistant, real-time data visualization, and energy-efficient operation.

## System Overview

The system consists of two primary nodes:
1. **Water Level Node**: Located at the tank, utilizing ultrasonic technology to measure water depth.
2. **Pump Controller Node**: Located at the pump site, providing manual and automated switching with visual status feedback.

## Hardware Specifications

### Water Level Node
- **Microcontroller**: ESP32-C3 (DevKitM-1)
- **Sensor**: JSN-SR04T (Waterproof Ultrasonic Sensor)
- **Temperature Sensing**: 100k NTC Thermistor (Battery Temperature Monitoring)
- **Power Management**: Deep Sleep enabled (2-minute cycle / 30-second run duration)
- **Communication**: 2.4GHz WiFi / Home Assistant Native API

### Pump Controller Node
- **Microcontroller**: ESP32-C3 (DevKitM-1)
- **Control**: GPIO-based relay switching
- **Visual Feedback**: WS2812 NeoPixel (Status Indicator)
- **Interface**: Web Server (Port 80) and Home Assistant API

## Key Features

### Precision Monitoring
The system uses a JSN-SR04T waterproof sensor to measure distance, which is then processed through a median filter and an exponential moving average to eliminate noise. The raw distance is converted to a percentage (0-100%) using custom linear calibration.

### Power Efficiency
The Water Level node is designed for battery operation, utilizing ESP32 Deep Sleep. A remote trigger from Home Assistant can prevent deep sleep temporarily for maintenance or high-frequency monitoring.

### Safety and Diagnostics
- **Battery Health**: Integrated NTC thermistor monitoring for battery temperature safety.
- **Signal Quality**: Real-time WiFi signal strength (RSSI) reported as a diagnostic sensor.
- **Fallback Access**: Captive portal support for local configuration if WiFi connectivity is lost.

## Software Configuration

The system is managed via ESPHome. The configuration files are split into logical modules:
- `water-level.yaml`: Handles ultrasonic sensing, battery diagnostics, and sleep logic.
- `pump-controller.yaml`: Manages the relay output and NeoPixel status light.

### Calibration Logic
The water level percentage is calculated using the following linear transformation:
`level = -192.31 * distance + 138.46`
(Calculated based on the min/max depth of the target tank).

## Installation and Deployment

1. **Hardware Assembly**: Refer to the `water-tank_pcb` directory for circuit diagrams and PCB layout files.
2. **ESPHome Setup**: Install ESPHome on your host machine or via Home Assistant Add-on.
3. **Flashing**:
   - Navigate to the `water-tank_esphome` directory.
   - Run `esphome run water-level.yaml` to flash the monitoring node.
   - Run `esphome run pump-controller.yaml` to flash the control node.
4. **Home Assistant Integration**: Both nodes will be automatically discovered via the ESPHome integration. Use the provided API passwords for initial pairing.

## Project Structure

- `water-tank_esphome/`: ESPHome YAML configuration files and custom components.
- `water-tank_pcb/`: Hardware design files, schematics, and manufacturing data.

## License
Refer to the LICENSE file for distribution and usage rights.
