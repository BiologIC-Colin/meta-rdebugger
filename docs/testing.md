# Bring-up test plan

Start with the automatic checks on the station:

```bash
ssh root@rdebugger-<serial>.local rdebugger-selftest
```

It covers the non-interactive checks below (boot, network, services, USB,
OpenOCD and ser2net state, CAN bit rate, 1-Wire master, RTC, watchdog, I2C,
persistent journal), prints PASS/FAIL/SKIP per check and exits non-zero on any
failure; checks needing absent equipment are skipped. Then run the rest by hand.

Run after flashing a new image, in order. Each check names the command, what a
pass looks like, and where to look if it fails. Record the result per check
(pass / fail + note) in the bench log. `ST` = on the station over SSH;
`WS` = on a workstation on the same network.

Hardware for the full run: station on PoE, an ST-LINK/V2-ISOL wired to a
powered STM32H747 or STM32F767 board, an FTDI USB-UART wired to the target's
console, a CAN partner (second CAN node, USB-CAN adapter, or the target) with
120 Ω termination, and a 1-Wire device (DS18B20 or DS2431) with a 4.7 kΩ
pull-up on test pad P7 (GPIO19).

## 1. Boot and identity

| # | Check | Pass |
| --- | --- | --- |
| 1.1 | Serial console (ttyAMA0, 115200) shows kernel and systemd boot, ends in a login prompt | prompt `rdebugger-xxxxxxxx login:` |
| 1.2 | `WS: ping rdebugger-<serial>.local` | replies (mDNS works) |
| 1.3 | `WS: ssh root@rdebugger-<serial>.local` (dev image: no password) | shell |
| 1.4 | `ST: cat /etc/os-release` | `ID=rdebugger`, `VERSION_ID=0.1.0` |
| 1.5 | `ST: systemctl --failed` | `0 loaded units listed` |
| 1.6 | `ST: journalctl -b -p warning --no-pager` | no unexplained errors (note any) |
| 1.7 | `ST: journalctl -u rdebugger-setup -b` | hostname set, configuration written |
| 1.8 | `ST: df -h /` and `journalctl -u rdebugger-growfs -b` (first boot) | `/` is roughly the eMMC size; growfs "done" |
| 1.9 | `ST: cat /proc/cmdline; tr -d '\0' </proc/device-tree/model` | `root=/dev/mmcblk0p2`; "Raspberry Pi Compute Module 4 ..." |
| 1.10 | `ST: rdebugger-status` | report prints without errors |

## 2. Network

| # | Check | Pass |
| --- | --- | --- |
| 2.1 | `ST: networkctl` | eth0 `routable`/`configured`; can0 `configured` or `carrier`; wlan0 `no-carrier` until Wi-Fi is provisioned |
| 2.2 | `ST: ip route` | default via eth0 (metric 10) |
| 2.3 | `ST: timedatectl` | `System clock synchronized: yes`, NTP service active |
| 2.4 | `ST: resolvectl query github.com` | resolves |
| 2.5 | `WS: avahi-browse -rt _rdebugger._tcp` | station listed with TXT `target=...`, ports |
| 2.6 | Wi-Fi (optional): put `wpa_supplicant.conf` on /boot, reboot; `ST: networkctl status wlan0` | wlan0 `routable`, eth0 still default route |
| 2.7 | Link-local (optional): cable a laptop straight to the station, no DHCP | both get 169.254.x.x after ~10 s; `ssh root@<name>.local` works |

## 3. USB host

| # | Check | Pass |
| --- | --- | --- |
| 3.1 | `ST: dmesg \| grep -i dwc2` | dwc2 in host mode, no errors |
| 3.2 | `ST: lsusb` with ST-LINK and FTDI plugged into the USB-A ports | both listed (`0483:3748` ST-LINK/V2-ISOL or `0483:374b` Nucleo ST-LINK/V2-1, `0403:60xx`); `1366:xxxx` = J-Link OB firmware, not supported |

## 4. Debug (OpenOCD)

| # | Check | Pass |
| --- | --- | --- |
| 4.1 | Plug in the ST-LINK; `ST: systemctl status rdebugger-openocd` | active (running), started by udev |
| 4.2 | `ST: journalctl -u rdebugger-openocd -b` | `stlink` firmware version V2J24+ reported; target examined (`stm32h7x.cpu0` / `.cpu1`; F411: `stm32f4x.cpu`), no "examination failed" |
| 4.3 | `WS: telnet <station> 4444`, then `targets` | targets listed, state halted/running |
| 4.4 | `WS: arm-none-eabi-gdb <elf> -ex "target extended-remote <station>:3333" -ex "monitor reset halt" -ex load` | load completes, verify ok |
| 4.5 | Breakpoint on `main`, `continue` | stops at `main` |
| 4.6 | H747 only: GDB on port 3334 | attaches to the Cortex-M4 |
| 4.7 | Unplug the ST-LINK | `rdebugger-openocd` stops (inactive) |
| 4.8 | Replug | service starts again |
| 4.9 | Power off the target with the probe attached | OpenOCD logs errors and restarts every 5 s; recovers when power returns |

## 5. DUT UART (ser2net)

| # | Check | Pass |
| --- | --- | --- |
| 5.1 | Plug in the FTDI; `ST: ls -l /dev/dut-uart /dev/serial/by-id/` | symlink to `ttyUSB0` |
| 5.2 | `WS: nc <station> 3000` while the target prints | target output appears; typing reaches the target |
| 5.3 | `WS: python3 -m serial.tools.miniterm rfc2217://<station>:3001 115200` | works; change baud with Ctrl-T Ctrl-B |
| 5.4 | Second client on 3000 while the first is connected | second client takes over (kickolduser); a client on 3001 meanwhile gets `Device open failure` |
| 5.5 | `ST: ps \| grep [s]er2net` (with a client connected) | USER column is not root (dynamic user) |

## 6. CAN

| # | Check | Pass |
| --- | --- | --- |
| 6.1 | `ST: dmesg \| grep -i mcp251x` | `mcp251x spi0.0 can0: MCP2515 successfully initialized` |
| 6.2 | `ST: ip -details link show can0` | `state ERROR-ACTIVE`, `bitrate 500000` (or station.conf value), `restart-ms 100` |
| 6.3 | `ST: candump can0` with the partner sending | frames shown |
| 6.4 | `ST: cansend can0 123#DEADBEEF` | partner receives; TX counter rises |
| 6.5 | `WS: python-can socketcand` example from usage.md, over Ethernet and (if provisioned) Wi-Fi | frames sent and received over TCP 29536 |
| 6.6 | Set `CAN_BITRATE="250000"`, `rdebugger-setup apply` | `ip -details link show can0` shows 250000 |
| 6.7 | Disconnect the bus partner, send repeatedly | error counters rise, bus-off then automatic restart |

## 7. 1-Wire

| # | Check | Pass |
| --- | --- | --- |
| 7.1 | `ST: ls /sys/bus/w1/devices/` | `w1_bus_master1` and the device (`28-...` / `2d-...`) |
| 7.2 | `ST: cat /sys/bus/w1/devices/28-*/temperature` | plausible millidegrees C |
| 7.3 | Unplug, then replug the device | disappears after ~100 s (10 missed searches); reappears within ~10 s |

## 8. RTC, watchdog, GPIO, I2C

| # | Check | Pass |
| --- | --- | --- |
| 8.1 | `ST: dmesg \| grep -i rtc` | `rtc-pcf85063 ...: registered as rtc0`, system clock set from rtc0 |
| 8.2 | `ST: cat /sys/class/rtc/rtc0/time` vs `date -u` | within a second or two after NTP sync |
| 8.3 | Disconnect Ethernet, reboot, `date -u` | time correct from the RTC (battery fitted) |
| 8.4 | `ST: systemctl show -p RuntimeWatchdogUSec` and `dmesg \| grep -i watchdog` | 30 s; bcm2835-wdt active |
| 8.5 | `ST: ls /dev/i2c-*` and `i2cdetect -y 1` | i2c-1 and i2c-10 present; ADS1113 at 0x48 on bus 1; RTC (0x51) on bus 10 shows `UU` |
| 8.6 | `ST: gpioset -t0 GPIO20=0` then `gpioset -t0 GPIO20=1` | green user LED on, then off (active-low) |
| 8.7 | `ST: gpioget GPIO23` with the isolated input driven / not driven | value follows the input |

## 9. Persistence and recovery

| # | Check | Pass |
| --- | --- | --- |
| 9.1 | `ST: journalctl --list-boots` after two reboots | several boots listed (persistent journal) |
| 9.2 | Change `TARGET`, `rdebugger-setup apply` | OpenOCD restarts with the new target config |
| 9.3 | Pull PoE during operation, restore | boots cleanly, filesystem check clean (`journalctl -b -u systemd-fsck*`) |

## Reporting

For each failure, attach `rdebugger-status`, `journalctl -b --no-pager`,
`dmesg` and the deployed `config.txt`. The open questions this run should
answer are in [open-questions.md](open-questions.md).
