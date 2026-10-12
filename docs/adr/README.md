# Architecture decision records

Decisions that cannot be enforced by code or CI, recorded so that people and
agents working on the layer make the same choices. Format: context, decision,
consequences. Supersede a record with a new one rather than editing history.

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-standalone-systemd-distro.md) | Standalone `rdebugger` distro on systemd | Accepted 2026-10-11 |
| [0002](0002-machine-per-carrier-board.md) | One MACHINE per carrier board, derived from raspberrypi4-64 | Accepted 2026-10-11 |
| [0003](0003-runtime-station-configuration.md) | One station.conf, generated runtime configuration | Accepted 2026-10-11 |
| [0004](0004-openocd-dapdirect-dual-core.md) | OpenOCD st-link (dapdirect) driver, both H747 cores, started by udev | Accepted 2026-10-11 |
| [0005](0005-onewire-kernel-sysfs.md) | 1-Wire through the kernel w1 subsystem on GPIO19, no owfs | Accepted 2026-10-11 |
| [0006](0006-unauthenticated-services-on-bench-segment.md) | Unauthenticated network services, isolated bench segment | Accepted 2026-10-11 |
| [0007](0007-lean-image-explicit-drivers.md) | Lean image from packagegroup-core-boot with explicit drivers | Accepted 2026-10-11 |
| [0008](0008-persistent-journal-watchdog.md) | Persistent, capped journal and hardware watchdog | Accepted 2026-10-11 |
