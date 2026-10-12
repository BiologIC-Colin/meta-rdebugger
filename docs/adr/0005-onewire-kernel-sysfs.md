# ADR 0005: 1-Wire through the kernel w1 subsystem on GPIO19, no owfs

## Context

Every module carries a 1-Wire ID chip that gives node identity (J1939 NAME,
traceability), so the station needs a 1-Wire master. On this board GPIO4 (the
default w1-gpio pin and meta-raspberrypi's ENABLE_W1) is the isolated RS232 TXD
(uart3). owfs would need meta-filesystems, meta-networking and meta-python, and
its systemd wiring in the wrynose recipe is broken.

## Decision

- Kernel w1 master `dtoverlay=w1-gpio,gpiopin=19` via RPI_EXTRA_CONFIG;
  `RDEBUGGER_W1_GPIO ?= "19"` in the machine file. The board has no 40-pin
  header; the only free GPIOs (18, 19, 21) are bare test pads (P4, P7, P8).
  GPIO19 / pad P7 (CM4 connector pin 26) has the fewest overlay collisions
  (GPIO18 is the default for IR, PWM and audremap). An external 4.7 kΩ pull-up
  to 3.3 V is required.
- Slave drivers for ID/EEPROM chips and DS18B20 (w1-ds2431, w1-ds2433,
  w1-ds28e04, w1-therm), plus the DS2482 I2C-to-1-Wire bridge driver for the
  future carrier board. Access is through sysfs.
- No owfs.

## Consequences

- Wiring means soldering to pad P7: raw 3.3 V CMOS, not isolated, no ESD
  protection. Locate P7 and check continuity to CM4 pin 26 before use.
- The cleaner route on this board is a DS2482-100 on the isolated I2C1
  terminal (shares the bus with the ADS1113 at 0x48); the driver is built.
- GPIO26, an earlier choice, is the red user LED (Waveshare schematic).
- Bit-banged 1-Wire has weak drive and no strong pull-up; for long buses or
  parasite-powered parts the carrier board should use a DS2482 on I2C.
- The isolated RS232 (uart3) remains usable.
