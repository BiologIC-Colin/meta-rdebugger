# ADR 0002: One MACHINE per carrier board

## Context

The Waveshare CM4 PoE 4G board is the first carrier; our own carrier board
will follow. Board facts (overlays, console UART, which GPIO does what, which
drivers are needed) must not leak into distro policy or the image recipe.
meta-raspberrypi keys kernel defconfig, firmware and EEPROM recipes on the
`raspberrypi4-64` / `raspberrypi4` / `rpi` overrides.

## Decision

- Each carrier board gets a machine file in this layer. The first is
  `conf/machine/waveshare-cm4-poe-4g.conf`:
  `MACHINEOVERRIDES =. "raspberrypi4-64:"` then
  `require conf/machine/raspberrypi4-64.conf`, the same pattern as
  meta-raspberrypi's own `raspberrypi-cm5-io-board.conf`.
- config.txt settings (ENABLE_* variables and RPI_EXTRA_CONFIG), the console,
  the device tree list and the board's driver packages
  (`MACHINE_ESSENTIAL_EXTRA_RRECOMMENDS`) live in the machine file.

## Consequences

- Deploy directory and image names carry the machine name
  (`tmp/deploy/images/waveshare-cm4-poe-4g/`).
- Our carrier board becomes `conf/machine/<board>.conf` plus, when needed, a
  device-tree overlay for its pinout; distro, image and services are reused.
- Variables that raspberrypi4-64.conf hard-assigns (e.g. RPI_KERNEL_DEVICETREE)
  must be set after the `require`.
