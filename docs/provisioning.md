# Provisioning and station configuration

Every rdebugger image is identical. What makes a station *this* station (its
name, target, Wi-Fi, SSH keys, CAN bit rate, ports) is data:

- `/usr/lib/rdebugger/station.defaults`: shipped defaults (read-only, documented)
- `/etc/rdebugger/station.conf`: this station's overrides (absent = all defaults)

`rdebugger-setup` reads both at every boot and generates the service
configuration ([architecture](architecture.md#runtime-from-one-config-file-to-running-services)).

## Provisioning from the boot partition

The first partition of the eMMC is FAT (`/boot` on the station, a normal drive
on Windows when the CM4 is in rpiboot mode). Drop any of these files there
before the first boot, or later; each is picked up at the next boot:

| File on the boot partition | Effect | Afterwards |
| --- | --- | --- |
| `rdebugger.conf` | Copied to `/etc/rdebugger/station.conf` (replaces it) | Renamed `rdebugger.conf.applied` |
| `wpa_supplicant.conf` | Installed as `/etc/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf` (mode 600); enables Wi-Fi | **Deleted** (it holds a secret) |
| `authorized_keys` | Public keys appended to `/root/.ssh/authorized_keys` (duplicates skipped) | Renamed `authorized_keys.applied` |

Windows line endings are stripped. Release images have no root password, so
`authorized_keys` is how you get in.

### Example `rdebugger.conf`

```sh
# Bench 7: H747 module, J1939 backplane at 250 kbit/s
STATION_NAME="bench-07"
TARGET="stm32h747"
CAN_BITRATE="250000"
```

### Example `wpa_supplicant.conf`

Store a PSK hash, not the passphrase. Generate it on any Linux machine with
`wpa_passphrase "<ssid>" "<passphrase>"` and delete the commented `#psk=` line.

```
country=GB
network={
    ssid="LabNet"
    psk=4f1c...e9a2
}
```

Any `ctrl_interface=` line in the file is replaced by
`ctrl_interface=/run/wpa_supplicant` (Raspberry Pi OS examples use
`GROUP=netdev`, a group this image does not have), so `wpa_cli -i wlan0 status`
works. The Wi-Fi client is started by udev when `wlan0` appears and only runs
once provisioned; Wi-Fi never delays boot, and Ethernet stays the preferred
route (metric 10 vs 600). Unprovisioned, `networkctl` shows wlan0 as
`no-carrier`.

## station.conf keys

Syntax: one `KEY="value"` (or `KEY='value'` or `KEY=value`) per line; `#`
starts a comment, on its own line or after the value. The file is parsed, not
executed. Unknown keys are ignored and invalid or out-of-range values fall back
to the default; both are logged (`journalctl -u rdebugger-setup`).

| Key | Default | Meaning |
| --- | --- | --- |
| `STATION_NAME` | *(empty)* | Hostname and mDNS name (`<name>.local`). Lower-case letters, digits, `-`. Empty: `rdebugger-<last 8 hex digits of the CM4 serial>`. |
| `TARGET` | `stm32h747` | `stm32h747` (dual core: GDB 3333 = M7, 3334 = M4), `stm32f767`, `stm32f411`, `none` (OpenOCD never starts), or any `<name>` (`[a-z0-9_-]`) for which `/etc/rdebugger/openocd/<name>.cfg` exists. An unknown name without a config falls back to the default. |
| `OPENOCD_RESET_CONFIG` | *(empty)* | OpenOCD `reset_config`. Empty = the target script's default: cores reset through SYSRESETREQ over SWD. With the probe's RESET (pin 15) wired to NRST: `srst_only srst_nogate` (hardware reset) or `srst_only srst_nogate connect_assert_srst` (connect under reset). |
| `OPENOCD_BIND` | `0.0.0.0` | Address OpenOCD listens on. `127.0.0.1` = SSH tunnels only. |
| `OPENOCD_ADAPTER_KHZ` | *(empty)* | SWD clock limit in kHz. Empty = the target scripts' clocks (H747: 1800 kHz, 4000 after reset-init; F767 and F411: 2000 kHz requested (an ST-LINK runs 1800), 4000 after reset-init (8000 requested, capped at 4000)). Set e.g. `950` (1-4000 on an ST-LINK/V2) for long or noisy cables: it is applied at start and appended to the target scripts' reset-start, reset-init and reset-end handlers, so it holds through every reset. |
| `OPENOCD_GDB_PORT` | `3333` | First GDB port; further cores get the next ports. |
| `OPENOCD_TELNET_PORT` | `4444` | OpenOCD telnet console. |
| `OPENOCD_TCL_PORT` | `6666` | OpenOCD Tcl RPC (scripted tools). |
| `DUT_UART_DEVICE` | `/dev/dut-uart` | Serial device ser2net shares. `/dev/dut-uart` = first FTDI port; `/dev/ttyAMA3` = isolated RS232; `/dev/ttyAMA5` = isolated RS485. |
| `DUT_UART_BAUD` | `115200` | Initial baud rate (8N1). RFC 2217 clients can change it per session. |
| `SER2NET_RAW_PORT` | `3000` | Raw TCP port for the DUT UART. |
| `SER2NET_RFC2217_PORT` | `3001` | RFC 2217 (telnet COM-port control) port. |
| `CAN_INTERFACE` | `can0` | SocketCAN interface. |
| `CAN_BITRATE` | `500000` | CAN bit rate in bit/s (J1939: 250000 or 500000). |
| `CAN_RESTART_MS` | `100` | Automatic restart delay after bus-off, in ms. |

## Changing configuration on a running station

```sh
ssh root@bench-07.local
nano /etc/rdebugger/station.conf
rdebugger-setup apply      # regenerate, restart ser2net/socketcand/OpenOCD/avahi/Wi-Fi, reconfigure CAN
rdebugger-setup show       # effective values
```

A hostname change takes effect immediately for SSH and mDNS; open sessions keep
the old prompt. `rdebugger-setup` exits non-zero (and `rdebugger-setup.service`
shows as failed) if it could not write a file; a provisioning file it could not
install is left on the boot partition.

### Custom OpenOCD target configuration

To change more than station.conf offers (e.g. another chip, a board-specific
reset or clock setup), copy `/usr/share/rdebugger/openocd/<target>.cfg` to
`/etc/rdebugger/openocd/<target>.cfg`, edit it, and run `rdebugger-setup apply`.
A file in `/etc/rdebugger/openocd/` takes precedence over the shipped one.

## Hostname and identity

- Default: `rdebugger-<serial>`, from `/sys/firmware/devicetree/base/serial-number`
  (the CM4's unique serial). It is unique per CM4 and stable across reflashes.
- The name is written to `/etc/hostname` and advertised by avahi:
  `ssh root@rdebugger-1a2b3c4d.local`.
- `avahi-browse -rt _rdebugger._tcp` on any Linux machine lists all stations
  with their target and ports (TXT records).

## Re-provisioning

- To change Wi-Fi: drop a new `wpa_supplicant.conf` and reboot, or edit
  `/etc/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf` and
  `systemctl restart wpa_supplicant-nl80211@wlan0`.
- To remove Wi-Fi: delete that file and reboot.
- A reflash replaces both partitions, so `/etc/rdebugger/station.conf`, Wi-Fi
  and SSH keys are gone. Keep each bench's `rdebugger.conf`,
  `wpa_supplicant.conf` and `authorized_keys` with the bench records and copy
  them onto the boot partition again right after flashing.
