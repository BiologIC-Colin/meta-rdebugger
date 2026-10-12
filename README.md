# meta-rdebugger

Yocto layer for **rdebugger**: a remote debug station (bench node) that lets
people and agents on the network program, debug and talk to embedded targets.
It is the software half of a bench node in the
[Agentic Hardware Development operating model](https://claude.ai/code/artifact/756932dc-5d3b-49b7-ab2d-43a147fa8de7)
(a thin, deterministic instrument host on the bench network) and is the
starting point for our own carrier board.

| Item | Value |
| --- | --- |
| Distro | `rdebugger` 0.1.0 (standalone, systemd, ipk) |
| Machine | `waveshare-cm4-poe-4g`: Raspberry Pi CM4 (eMMC, Wi-Fi) on the Waveshare CM4 PoE 4G Board |
| Image | `rdebugger-image` |
| Yocto | 6.0 "wrynose" (BitBake 2.18), meta-raspberrypi, meta-openembedded/meta-oe |
| Targets | STM32H747XI (dual core), STM32F767ZI, STM32F411RE (e.g. NUCLEO-F411RE), through an ST-LINK (V2-ISOL, or a Nucleo's on-board V2-1) |

## What the station does

| Function | How | Default port / device |
| --- | --- | --- |
| SWD debug and flashing | OpenOCD, started automatically while an ST-LINK is plugged in | GDB 3333 (H747 M7) / 3334 (H747 M4), telnet 4444, Tcl 6666 |
| DUT console (UART) | FTDI USB-UART shared by ser2net | raw TCP 3000, RFC 2217 3001; `/dev/dut-uart` |
| CAN | on-board MCP2515 (`can0`) via SocketCAN; socketcand for remote access | `can0` at 500 kbit/s; socketcand TCP 29536 (all addresses) |
| 1-Wire | kernel w1 master on GPIO19 (test pad P7; module ID chips, sensors) | `/sys/bus/w1/devices/` |
| Network | PoE Ethernet (DHCP, link-local fallback), optional Wi-Fi, mDNS | `<station>.local`, SSH 22 |
| Time | PCF85063A RTC + NTP (systemd-timesyncd) | `rtc0` |

Everything is configured from one file, `/etc/rdebugger/station.conf`
(see [docs/provisioning.md](docs/provisioning.md)).

## Quick start

```bash
# 1. Create the build (once)
cd ~/yocto
./bitbake/bin/bitbake-setup init -L meta-rdebugger ~/yocto/meta-rdebugger \
    ~/yocto/meta-rdebugger/setup/rdebugger-wrynose.conf.json rdebugger-dev

# 2. Build
. ~/yocto/bitbake-builds/rdebugger-dev/build/init-build-env
bitbake rdebugger-image
```

3. Flash `tmp/deploy/images/waveshare-cm4-poe-4g/rdebugger-image-waveshare-cm4-poe-4g.rootfs.wic.bz2`
   to the CM4 eMMC: [docs/flashing.md](docs/flashing.md).
4. Optionally put `rdebugger.conf`, `wpa_supplicant.conf` and `authorized_keys`
   on the boot partition: [docs/provisioning.md](docs/provisioning.md).
5. Boot on PoE and connect: `ssh root@rdebugger-<serial>.local`, then run
   `rdebugger-status` and `rdebugger-selftest`. Developer workflows: [docs/usage.md](docs/usage.md).

## Documentation

| Document | Contents |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | Design, boot and service flow, how configuration becomes running services |
| [docs/hardware.md](docs/hardware.md) | Board, pin map, config.txt, wiring of ST-LINK, FTDI, CAN, 1-Wire |
| [docs/build.md](docs/build.md) | Build host, bitbake-setup, configurations, fragments, adding packages |
| [docs/flashing.md](docs/flashing.md) | rpiboot, usbipd-win over SSH, bmaptool / Etcher |
| [docs/provisioning.md](docs/provisioning.md) | Boot-partition files, every station.conf key, hostname |
| [docs/usage.md](docs/usage.md) | GDB, VS Code, west, ser2net, CAN/socketcand, 1-Wire, GPIO |
| [docs/testing.md](docs/testing.md) | Bring-up test plan, one check per subsystem |
| [docs/troubleshooting.md](docs/troubleshooting.md) | Symptoms and fixes |
| [docs/open-questions.md](docs/open-questions.md) | Decisions still needed from the team |
| [docs/adr/](docs/adr/) | Architecture decision records |
| [CLAUDE.md](CLAUDE.md) | Instructions for agents working in this layer |

## Layer layout

```
meta-rdebugger/
├── conf/
│   ├── layer.conf                      collection "rdebugger"
│   ├── distro/rdebugger.conf           distro policy
│   ├── machine/waveshare-cm4-poe-4g.conf   board: config.txt, console, drivers
│   └── fragments/dev-tools.conf        rdebugger/dev-tools (gdb, strace)
├── setup/rdebugger-wrynose.conf.json   bitbake-setup: sources, layers, configurations
├── recipes-core/
│   ├── images/rdebugger-image.bb
│   ├── packagegroups/packagegroup-rdebugger.bb
│   └── rdebugger-base/                 station.conf, rdebugger-setup, growfs, status
├── recipes-connectivity/
│   ├── rdebugger-network/              networkd (eth0, wlan0), Wi-Fi, mDNS
│   └── ser2net/                        ser2net bbappend (generated config, unprivileged)
├── recipes-devtools/
│   ├── rdebugger-debug/                OpenOCD target configs, service, USB udev rules
│   └── openocd/                        openocd bbappend (ST-LINK, CMSIS-DAP v2)
├── recipes-extended/socketcan/         socketcand bbappend (service, TCP 29536 socket proxy)
├── tests/test-rdebugger-setup.sh       rdebugger-setup on the image's busybox (qemu chroot)
└── docs/
```

## Licence

MIT for the layer's own files (recipes declare their own licences).
Maintainer: Colin Barker <colin.barker@biologic-tech.com>
