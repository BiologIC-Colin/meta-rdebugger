# ADR 0008: Persistent, capped journal and hardware watchdog

## Context

Stations run unattended. After a hang or power loss we need to know what
happened, and a hung station should recover by itself. OE puts /var/log on
tmpfs by default.

## Decision

- `FILESYSTEM_PERMS_TABLES` without the volatile-log table, so /var/log is on
  the eMMC; journald `Storage=persistent`, `SystemMaxUse=128M`,
  `SystemKeepFree=512M`, no syslog daemon.
- systemd drives the BCM2711 watchdog: `RuntimeWatchdogSec=30s`,
  `RebootWatchdogSec=2min`.

## Consequences

- More eMMC writes than a volatile log; acceptable at this size. Revisit for a
  read-only rootfs.
- A PID 1 hang longer than 30 s resets the board.
