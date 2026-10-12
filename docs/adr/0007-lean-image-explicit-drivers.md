# ADR 0007: Lean image from packagegroup-core-boot with explicit drivers

## Context

core-image's default `packagegroup-base-extended` pulls in
`MACHINE_EXTRA_RRECOMMENDS`, which on meta-raspberrypi includes every kernel
module and Bluetooth firmware, and (with default distro features) ofono and
neard.

## Decision

- `rdebugger-image` installs `packagegroup-core-boot` +
  `packagegroup-rdebugger` (base, debug, serial, can, onewire, tools) + OpenSSH.
- Board drivers are in the machine file's
  `MACHINE_ESSENTIAL_EXTRA_RRECOMMENDS` (MCP2515, SPI, I2C mux and RTC, w1-gpio,
  Wi-Fi). The ADS1113 ADC has no device-tree node, so it is read from user
  space over `/dev/i2c-1` rather than through the ti-ads1015 IIO driver. Function drivers (CAN protocols, USB serial, 1-Wire slaves)
  are RRECOMMENDS of the packagegroup.

## Consequences

- Small image, short boot, little to patch.
- A driver missing from these lists is simply absent at runtime: check new
  hardware with `lsmod` and `dmesg`, then add its `kernel-module-*` package.
- RRECOMMENDS of a non-existent package name is silently ignored; check the
  image manifest after changing these lists.
