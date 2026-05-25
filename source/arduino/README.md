# AquaDose ESP32 Firmware Source

Open `AquaDose/AquaDose.ino` in the Arduino IDE.

## Board And Libraries

The sketch targets ESP32 boards and uses standard ESP32 Arduino libraries:

- `WiFi.h`
- `WebServer.h`
- `Preferences.h`
- `time.h`
- ESP-IDF watchdog headers included with the ESP32 Arduino core

Install the ESP32 board support package in Arduino IDE before compiling.

## Default Hardware Mapping

The firmware defaults to 8 pumps on these GPIO pins:

| Pump | ESP32 GPIO |
| --- | --- |
| 1 | GPIO25 |
| 2 | GPIO26 |
| 3 | GPIO27 |
| 4 | GPIO14 |
| 5 | GPIO32 |
| 6 | GPIO33 |
| 7 | GPIO18 |
| 8 | GPIO19 |

Use a proper low-side logic-level N-MOSFET driver circuit for each pump channel: ESP32 GPIO through a 100 ohm series gate resistor, 10k ohm Gate-to-Source/GND pull-down resistor, flyback diode across the pump with stripe to +12V, external pump power supply, and shared common ground between ESP32 GND, buck GND, MOSFET Source, and pump supply negative.
