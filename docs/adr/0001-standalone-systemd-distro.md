# ADR 0001: Standalone rdebugger distro on systemd

## Context

Yocto 6.0 "wrynose" has no Poky repository; builds are assembled with
bitbake-setup. The earlier tutorial distro (blrd) was standalone on sysvinit
with ifupdown. The station needs services that start and stop with USB devices
(probe hot-plug), several network links (eth0, wlan0, can0), a hardware
watchdog and reliable logs.

## Decision

- A standalone distro `rdebugger` (`conf/distro/rdebugger.conf`) that does not
  require poky.conf, with `TARGET_VENDOR = "-rdebugger"`.
- `INIT_MANAGER = "systemd"` (adds `systemd` and `usrmerge`), systemd-networkd
  for all links, systemd-timesyncd, journald only (no syslog daemon).
- Default distro features minus what a headless node never needs, via
  `DISTRO_FEATURES_OPTED_OUT` (x11, wayland, graphics, audio, bluetooth, 3g,
  nfc, nfs, ptest...). `zeroconf` is kept for avahi.
- `package_ipk`, hash equivalence on.

## Consequences

- udev + systemd device units drive OpenOCD start/stop with no custom daemon.
- A distro change rebuilds the target toolchain (new vendor string), so the
  first build does not reuse blrd's sstate for target recipes.
- Keeping `zeroconf` also builds systemd-resolved without mDNS/LLMNR so it does
  not fight avahi for UDP 5353; removing it would break mDNS.
