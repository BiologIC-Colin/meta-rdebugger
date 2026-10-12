# ADR 0004: OpenOCD st-link (dapdirect) driver, both H747 cores, started by udev

## Context

Targets are STM32H747XI (Cortex-M7 + Cortex-M4) and STM32F767ZI, through an
ST-LINK/V2-ISOL. OpenOCD in meta-oe wrynose is 0.12+dev (git 66ea461, 2025-08).
At that revision `interface/stlink.cfg` uses the `st-link` (dapdirect) driver;
the HLA driver is deprecated and cannot debug more than one core.
`target/stm32h7x_dual_core.cfg` does not exist any more: dual core is
`DUAL_CORE`/`USE_CTI` in `target/stm32h7x.cfg`.

## Decision

- Target configs in `/usr/share/rdebugger/openocd/`: `stm32h747.cfg` follows
  OpenOCD's `board/st_nucleo_h745zi.cfg` (stlink.cfg, `transport select swd`,
  `DUAL_CORE 1`, `USE_CTI 1`, `stm32h7x_dual_bank.cfg`); `stm32f767.cfg` uses
  `target/stm32f7x.cfg`; `stm32f411.cfg` (added 2026-10-12 for a NUCLEO-F411RE
  bench) follows `board/st_nucleo_f4.cfg` with `target/stm32f4x.cfg`.
- `TARGET` accepts any name with a shipped or station-local `<name>.cfg`, so a
  new chip needs only a config file.
- `rdebugger-openocd.service` runs as a dynamic user in group plugdev,
  `Restart=always` (5 s backing off to 60 s). A udev rule tags ST-LINK devices with a fixed systemd alias
  (`sys-subsystem-usb-devices-stlink.device`) and SYSTEMD_WANTS the service; the
  service BindsTo that device, so plugging the probe starts OpenOCD and
  unplugging stops it.
- An openocd bbappend requests `--enable-stlink --enable-cmsis-dap-v2` and
  libusb1 explicitly so a missing driver fails the build.
- Reset and SWD speed default to the target scripts' own settings (SYSRESETREQ;
  1800 kHz, 4000 after reset-init), which work when only SWDIO/SWCLK are
  wired; hardware reset and connect-under-reset are station.conf options.
- `-rtos auto` (Zephyr thread awareness) and resume-on-GDB-detach on every core.

## Consequences

- GDB: 3333 = M7 (cpu0), 3334 = M4 (cpu1); the H7 APB-AP mem_ap gets no port.
- ST-LINK firmware must be V2J24 or later; V2J29+ (V2J32+ recommended) for the
  H747's M4 on AP3.
- One probe per station (the alias device unit is unique).
- OpenOCD's own `contrib/60-openocd.rules` are not installed: they would move
  FTDI tty nodes into group plugdev and break ser2net's dialout access.
