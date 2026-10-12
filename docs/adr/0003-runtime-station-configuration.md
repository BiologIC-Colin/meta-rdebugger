# ADR 0003: One station.conf, generated runtime configuration

## Context

All benches should run the same image, but each bench differs in name, target
MCU, CAN bit rate, Wi-Fi and SSH keys. The operating model says "configuring a
test becomes data, not wiring": a scheduler or tool layer should be able to
reconfigure a bench by writing data, not by rebuilding.

## Decision

- Shipped defaults in `/usr/lib/rdebugger/station.defaults`; per-station
  overrides in `/etc/rdebugger/station.conf` (KEY="value").
- `rdebugger-setup` (oneshot, before networking) parses both (never sources
  them), validates values, and generates the runtime files under `/run`
  (ser2net YAML, OpenOCD entry config, networkd CAN file, an environment file
  for units) plus the avahi advertisement. `rdebugger-setup apply` does the
  same at runtime and restarts the affected services.
- Provisioning files on the FAT boot partition (`rdebugger.conf`,
  `wpa_supplicant.conf`, `authorized_keys`) are imported once at boot, then
  renamed (or deleted, for the Wi-Fi secret).
- The hostname defaults to `rdebugger-<CM4 serial>`; no hostname is baked into
  the image.

## Consequences

- One image for every bench; the bench's data travels with the bench records.
- Generated files are in `/run` and rebuilt every boot, so hand edits there are
  lost; edit station.conf instead.
- New per-station settings need a key in station.defaults, the KNOWN_KEYS list
  and validation in rdebugger-setup, and documentation in provisioning.md.
