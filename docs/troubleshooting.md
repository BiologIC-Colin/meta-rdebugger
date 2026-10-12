# Troubleshooting

Start with `rdebugger-status` and `journalctl -b -p warning`. Service logs:
`journalctl -u <unit> -b`.

## Build

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Taskhash mismatch ... versus ...` errors | A recipe or config file in the layer changed while BitBake was running | Let the build finish, then run it again. Do not edit the layer during a build |
| openocd `do_fetch: Failed to fetch URL git://repo.or.cz/...;protocol=http` warnings | repo.or.cz now redirects http to https | Harmless while the Yocto source mirror has the tarballs (it does); if a fetch fails, add a PREMIRROR to https |
| `Files/directories were installed but not shipped` | A recipe installs to a path not in its `FILES` | Add the path to `FILES:${PN}` |
| `Nothing PROVIDES ...` for a package | Package name wrong, or its layer is not in `bb-layers` | `oe-pkgdata-util list-pkgs | grep <name>`; add the layer to the setup JSON |
| `Could not find fragment rdebugger/...` | Fragment named with the directory instead of the collection, or a missing `.conf` | Fragments are `rdebugger/<file>`; check `bitbake-config-build list-fragments` |
| `rdebugger-setup.service` failed | A file could not be written (disk full, read-only /etc) | `journalctl -u rdebugger-setup -b` shows `ERROR:` lines; provisioning files stay on /boot |
| Build dies when the P8 terminal is closed | WSL stops idle distros | `.wslconfig`: `[general] instanceIdleTimeout=-1`; run builds in tmux |

## Flashing and boot

| Symptom | Cause | Fix |
| --- | --- | --- |
| Board never boots the eMMC, appears as a USB device again | USB SLAVE cable still connected (forces rpiboot) | Unplug it and power-cycle |
| No eMMC drive after rpiboot | Wrong rpiboot mode | Use `rpiboot -d mass-storage-gadget64` |
| Nothing on the serial console | Wrong port, or jumper block J1 moved | Use the USB TO UART Type-C; J1 in its default (CM4) position; 115200 8N1 |
| `.local` name does not resolve | No mDNS on the client (WSL/Linux needs `libnss-mdns`), or different subnet | Use the IP from the DHCP leases; `avahi-browse -art` |
| SSH: "REMOTE HOST IDENTIFICATION HAS CHANGED" | New host keys after a reflash | `ssh-keygen -R <name>.local` |
| `/` is only a few hundred MB | `rdebugger-growfs` failed | `journalctl -u rdebugger-growfs -b`; run `/usr/sbin/rdebugger-growfs` by hand |

## USB, debug probe, UART

| Symptom | Cause | Fix |
| --- | --- | --- |
| `lsusb` shows nothing on the USB-A ports | DWC2 not in host mode, or the USB SLAVE port is connected | Check `dtoverlay=dwc2,dr_mode=host` in `/boot/config.txt`; unplug USB SLAVE |
| `rdebugger-openocd` inactive with the probe plugged in | `TARGET=none`, or the udev rule did not fire | `rdebugger-setup show`; `udevadm info /dev/bus/usb/...`; `systemctl status sys-subsystem-usb-devices-stlink.device` |
| OpenOCD: "firmware ... only supports deprecated HLA" | ST-LINK firmware older than V2J24 | Upgrade the probe firmware on a PC (STSW-LINK007 / STM32CubeProgrammer). Stopgap for single-core targets: a station-local `/etc/rdebugger/openocd/<target>.cfg` that sources `interface/stlink-hla.cfg` instead of `interface/stlink.cfg`, then `rdebugger-setup apply` |
| `lsusb` shows `1366:xxxx` (SEGGER) instead of an ST-LINK | Nucleo ST-LINK converted to J-Link OB; no J-Link driver in this OpenOCD | Restore the ST-LINK firmware with SEGGER STLinkReflash on a Windows PC, then update it |
| Selftest "target examined" FAILs while OpenOCD runs | Wrong `TARGET` for the chip (e.g. stm32h747 on an F411), or target not powered | `rdebugger-setup show`; set `TARGET`; `journalctl -u rdebugger-openocd -b` |
| OpenOCD: M4 (cpu1) examine fails, AP errors | Firmware older than V2J29/J32, or the M4 is not booted | Upgrade firmware; check the BCM4 option bit / M7 app |
| OpenOCD: "init mode failed (unable to connect to the target)", restarting every 5 s | Target unpowered, VAPP (pin 1) not connected, wiring | Power the target, connect VAPP, check SWDIO/SWCLK/GND |
| OpenOCD: WAIT/FAULT, DP errors | SWD too fast for the cable/isolator | `OPENOCD_ADAPTER_KHZ="950"`, `rdebugger-setup apply` |
| GDB cannot connect from a workstation | `OPENOCD_BIND=127.0.0.1`, firewall, or wrong port | Check `ss -ltnp` on the station |
| `nc <station> 3000` prints "Device open failure" | No FTDI (`/dev/dut-uart` missing), or the other ser2net port is in use | `ls -l /dev/dut-uart`; close the client on the other port |

## CAN

| Symptom | Cause | Fix |
| --- | --- | --- |
| No `can0` | MCP2515 not probed | `dmesg | grep -i mcp251x`; check `dtoverlay=mcp2515-can0,...` in config.txt and that `kernel-module-mcp251x` is installed |
| `networkctl` shows can0 `failed` | Unsupported option in a CAN .network file | Only BitRate/SamplePoint/RestartSec/ListenOnly/TripleSampling/Loopback on the MCP2515 |
| `state ERROR-PASSIVE` / `BUS-OFF`, TX errors rising | No other node acknowledging, wrong bit rate, missing termination | Check bit rate on both sides; 120 Ω at each bus end (board jumper `120R`) |
| python-can socketcand cannot connect | socketcand not running, or the proxy socket is down | `systemctl status socketcand socketcand-proxy.socket`; `ss -ltn \| grep 29536` |

## 1-Wire, RTC, Wi-Fi

| Symptom | Cause | Fix |
| --- | --- | --- |
| No `w1_bus_master1` | Overlay missing | `grep w1-gpio /boot/config.txt`; `lsmod | grep w1` |
| Master present, no devices | No pull-up, wiring, wrong pad | 4.7 kΩ from P7 (GPIO19) to 3.3 V; check continuity to CM4 pin 26 |
| No `rtc0` | i2c-10 missing (`i2c-mux-pinctrl` not loaded) or selector moved | `ls /dev/i2c-10`; `i2cdetect -y 10` (expect `UU` at 0x51) |
| Wrong time after boot without network | No RTC battery, or RTC never set | Fit the cell; let NTP sync once (kernel writes the RTC) |
| Wi-Fi does not come up | Not provisioned, wrong file name, country missing | `ls -l /etc/wpa_supplicant/`; `journalctl -u wpa_supplicant-nl80211@wlan0`; add `country=GB` |
