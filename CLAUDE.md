# meta-rdebugger: instructions for agents

Yocto 6.0 "wrynose" layer for the rdebugger remote debug station (CM4 on the
Waveshare CM4 PoE 4G board). Read `README.md`, then the doc for the area you
touch (`docs/`). Decisions are in `docs/adr/`; follow them or propose a new ADR.

## Environment

- Build host: P8, WSL distro `Yocto`, `~/yocto`. Build dir:
  `~/yocto/bitbake-builds/rdebugger-dev` (this layer is symlinked in with
  `bitbake-setup -L`). Source the env: `. ~/yocto/bitbake-builds/rdebugger-dev/build/init-build-env`.
- Build: `bitbake rdebugger-image`. Long builds: `bitbake -k ... > build.log 2>&1`
  in the background or in tmux.
- A Claude session "on the WSL" runs on P8. Hardware (the CM4) is attached to
  Colin's laptop, usually at home on VPN: P8 cannot reach it directly. Flashing
  from P8 needs the usbipd + `ssh -R 3240` tunnel in `docs/flashing.md`.

## Rules

- Never edit files of this layer while BitBake is running (taskhash mismatch).
- Durable configuration lives in this layer: machine file (hardware), distro
  file (policy), packagegroup (content), `setup/rdebugger-wrynose.conf.json`
  (layers, fragments). Never rely on `build/conf/local.conf` or hand-edit
  `toolcfg.conf`; `bitbake-setup update` regenerates them.
- Fragments are named by collection: `rdebugger/<name>`, not `meta-rdebugger/<name>`.
  Every fragment needs `BB_CONF_FRAGMENT_SUMMARY` and `BB_CONF_FRAGMENT_DESCRIPTION`;
  a broken fragment breaks every `bitbake-config-build` call.
- Fragments are parsed before the machine and distro files: use `?=`, `+=`,
  `:append` there for anything a fragment may set; never hard-assign
  `EXTRA_IMAGE_FEATURES` in the distro.
- In the machine file, set variables that `raspberrypi4-64.conf` hard-assigns
  (e.g. `RPI_KERNEL_DEVICETREE`) after the `require`. `RPI_EXTRA_CONFIG` goes
  through printf: `\n` separators, no `%`, lines up to 80 characters.
- Board drivers go in `MACHINE_ESSENTIAL_EXTRA_RRECOMMENDS` (the image does not
  use packagegroup-base); function drivers in `packagegroup-rdebugger`. After
  changing either, check the image manifest: missing names are silently ignored.
- Per-station settings belong in `station.defaults` + `rdebugger-setup`
  (KNOWN_KEYS, validation, generation) + `docs/provisioning.md`, never in the image.
- Shell scripts: `/bin/sh` is bash in this image (gensio pulls it in), but the
  utilities are busybox applets only (no `install`, no `xxd`, busybox
  `ps`/`date`; `head`/`tail` need `-n N`, not `-N`). Write POSIX sh. Test with `dash -n`
  and `sudo tests/test-rdebugger-setup.sh <image rootfs>`, which runs
  rdebugger-setup on the image's own busybox (qemu-user chroot). Never test only
  against the host's GNU tools.
- systemd units: services run as `DynamicUser=yes` with the minimum groups
  (dialout for UARTs, plugdev for the ST-LINK). Units consuming generated config
  order `After=rdebugger-setup.service`.
- udev rules: match on every action except remove (systemd re-checks the
  `systemd` tag on each uevent); all `ATTRS{}` in one rule must match the same
  parent device, so use `ENV{ID_USB_*}` for tty devices.
- MCP2515 (`can0`): in networkd `[CAN]` only BitRate, SamplePoint, RestartSec,
  ListenOnly, TripleSampling, Loopback. Others put the link in `failed`.
- OpenOCD (0.12+git 66ea461): `interface/stlink.cfg` is the dapdirect driver,
  transport `swd`, commands `gdb port N` style. There is no
  `target/stm32h7x_dual_core.cfg`: use `DUAL_CORE 1` + `stm32h7x_dual_bank.cfg`.
- No secrets in the layer (Wi-Fi, keys): they arrive via the boot partition.
- Board pin facts come from the schematic (see `docs/hardware.md`); the
  Waveshare wiki has errors (red LED is GPIO26, not 21). There is no 40-pin header.

## Change checklist

1. Edit, then `dash -n` scripts and validate JSON (`python3 -m json.tool`).
2. `bitbake rdebugger-image`; read every WARNING.
3. Check `tmp/deploy/images/waveshare-cm4-poe-4g/rdebugger-image-*.manifest`
   and `bootfiles/config.txt` for the expected change.
4. Update the docs and, for a decision, an ADR.
5. Commit with a conventional commit message (`feat:`, `fix:`, `docs:` ...).
6. On hardware: the relevant section of `docs/testing.md`.
