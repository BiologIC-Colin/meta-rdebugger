# Using a station

Examples use `bench-07.local`; replace with your station's name
(`rdebugger-<serial>.local` by default). Ports are the defaults from
[provisioning.md](provisioning.md#stationconf-keys).

## Quick look

```bash
ssh root@bench-07.local rdebugger-status
```

Shows release, IP addresses, time sync and RTC, service states, whether an
ST-LINK and the FTDI are plugged in, CAN state and counters, 1-Wire devices and
I2C buses. `rdebugger-selftest` runs the automatic bring-up checks
([testing.md](testing.md)) and exits non-zero if any fail. Logs: `journalctl -u rdebugger-openocd -f` (or `ser2net`,
`socketcand`, `rdebugger-setup`, `systemd-networkd`).

## Debugging and flashing (OpenOCD + ST-LINK)

OpenOCD starts by itself when the ST-LINK is plugged in and keeps retrying
every 5 s while the target is unpowered or disconnected. The target is set by
`TARGET` in station.conf.

| Target | GDB port | Core |
| --- | --- | --- |
| `stm32h747` | 3333 | Cortex-M7 (`stm32h7x.cpu0`) |
| `stm32h747` | 3334 | Cortex-M4 (`stm32h7x.cpu1`) |
| `stm32f767` | 3333 | Cortex-M7 |

Telnet console (OpenOCD commands): `telnet bench-07.local 4444`. Tcl RPC for
scripts: port 6666 (each command ends with byte `0x1a`).

Thread-aware debugging of Zephyr works when the firmware is built with
`CONFIG_DEBUG_THREAD_INFO=y` (`-rtos auto`). After GDB detaches, OpenOCD
resumes the target so the firmware keeps running.

### Flashing without GDB

Copy the image to the station's upload directory (OpenOCD has a private
`/tmp`, so it cannot see files there), then send OpenOCD's `program` command
to the Tcl port and wait for its reply (an empty reply means success; a
failure returns the error text):

```bash
scp build/zephyr/zephyr.hex root@bench-07.local:/var/lib/rdebugger/upload/
```

```python
import socket
s = socket.create_connection(("bench-07.local", 6666))
s.sendall(b"program /var/lib/rdebugger/upload/zephyr.hex verify reset\x1a")
reply = b""
while not reply.endswith(b"\x1a"):
    reply += s.recv(4096)
print(reply[:-1].decode() or "OK")
```

### GDB from a workstation

Use an Arm GDB (`arm-none-eabi-gdb` from the Zephyr SDK, or `gdb-multiarch`):

```bash
arm-none-eabi-gdb build/zephyr/zephyr.elf
(gdb) target extended-remote bench-07.local:3333
(gdb) monitor reset halt
(gdb) load
(gdb) break main
(gdb) continue
```

Flash and run without an interactive session:

```bash
arm-none-eabi-gdb -batch build/zephyr/zephyr.elf \
  -ex "target extended-remote bench-07.local:3333" \
  -ex "monitor reset halt" -ex "load" -ex "monitor reset run"
```

STM32H747 dual core: build the M7 and M4 images separately; load and debug the
M7 image on 3333 and the M4 image on 3334. `USE_CTI` is on, so halting one core
halts the other.

### VS Code (Cortex-Debug)

```json
{
  "name": "H747 M7 on bench-07",
  "type": "cortex-debug",
  "request": "launch",
  "servertype": "external",
  "gdbTarget": "bench-07.local:3333",
  "executable": "${workspaceFolder}/build/zephyr/zephyr.elf",
  "gdbPath": "arm-none-eabi-gdb",
  "runToEntryPoint": "main",
  "overrideLaunchCommands": ["monitor reset halt", "load", "monitor reset halt"],
  "overrideRestartCommands": ["monitor reset halt"]
}
```

For the M4, copy it with port 3334 and the M4 ELF, and use `"request": "attach"`
once the M7 has started it.

### Zephyr west

`west flash` and `west debug` with the openocd runner start OpenOCD on the local
machine, so they cannot reach a probe on a station. Build with `west build`,
then flash and debug with the GDB commands above. A wrapper for remote
flashing is a candidate for the tool layer.

### Probe notes

- ST-LINK/V2-ISOL (`0483:3748`) firmware: **V2J24 or later** for the
  dapdirect driver at all; **V2J29 minimum, V2J32 or later recommended** for
  the H747's Cortex-M4 (AP3). The version is in the OpenOCD log line
  `STLINK V2J...`. Update with STM32CubeProgrammer on a workstation.
- `OPENOCD_ADAPTER_KHZ` (default: the target script's 1800/4000 kHz) is the
  SWD clock; set e.g. 950 for long or noisy cables.
- Connect-under-reset needs the probe's RESET (pin 15) wired to the target's
  NRST and `OPENOCD_RESET_CONFIG="srst_only srst_nogate connect_assert_srst"`.
- The probe is opened exclusively: one OpenOCD (one target) per probe.

## DUT console (ser2net)

ser2net opens the UART only while a client is connected. On the same port a
new client takes over from an old one (`kickolduser`). Ports 3000 and 3001
share the one UART: while one is in use, a client on the other gets
`Device open failure`.

| Port | Protocol | Use |
| --- | --- | --- |
| 3000 | raw TCP | `nc`, `socat`, scripts reading logs |
| 3001 | telnet with RFC 2217 | clients that change baud rate or control lines |

```bash
nc bench-07.local 3000
```

```bash
python3 -m serial.tools.miniterm rfc2217://bench-07.local:3001 115200
```

Local virtual serial port on Linux (for tools that want a device):

```bash
socat pty,link=$HOME/dut-bench07,raw,echo=0 tcp:bench-07.local:3000
```

Python (pyserial) with remote baud changes:

```python
import serial
s = serial.serial_for_url("rfc2217://bench-07.local:3001", baudrate=115200, timeout=1)
s.write(b"help\r\n")
print(s.read(256))
```

On the station itself (only while no network client is connected):
`picocom -b 115200 /dev/dut-uart`.

The isolated RS232 (`/dev/ttyAMA3`) or RS485 (`/dev/ttyAMA5`) port can be
shared instead: set `DUT_UART_DEVICE` and run `rdebugger-setup apply`.

## CAN

`can0` comes up at boot at `CAN_BITRATE` (default 500 kbit/s) and restarts by
itself 100 ms after a bus-off. The MCP2515 is classic CAN only (no CAN FD).

On the station:

```bash
ip -details -statistics link show can0
candump -tA can0
cansend can0 18FF0101#0102030405060708
j1939spy can0
```

Change the bit rate: set `CAN_BITRATE` in station.conf, then
`rdebugger-setup apply`.

### Remote access with python-can (socketcand)

socketcand serves `CAN_INTERFACE` on TCP 29536 on every address of the station
(Ethernet, Wi-Fi, IPv4 and IPv6). There is no UDP discovery beacon; find
stations with `avahi-browse -rt _rdebugger._tcp` instead.

```python
import can

bus = can.Bus(interface="socketcand", host="bench-07.local", port=29536, channel="can0")
bus.send(can.Message(arbitration_id=0x18FF0101, is_extended_id=True, data=[1, 2, 3]))
for msg in bus:
    print(msg)
```

### Remote candump decoded with a DBC

```bash
ssh root@bench-07.local candump -L can0 | python3 -m cantools decode qudos.dbc
```

## 1-Wire

The kernel w1 master is on GPIO19. The board has no 40-pin header: GPIO19 is
the single-pin test pad **P7** (CM4 connector pin 26), raw 3.3 V, not isolated.
Solder a lead to P7 and fit an external 4.7 kΩ pull-up to 3.3 V; add ~100 Ω in
series and a TVS for cables that leave the bench. The master scans the bus
every 10 s. A new device appears within about 10 s as `<family>-<serial>`; a
removed one disappears only after about 100 s (10 missed searches):

```bash
ls /sys/bus/w1/devices/
cat /sys/bus/w1/devices/w1_bus_master1/w1_master_slaves
cat /sys/bus/w1/devices/28-*/temperature          # DS18B20, millidegrees C
hexdump -C /sys/bus/w1/devices/2d-*/eeprom | head  # DS2431 1 kbit EEPROM
```

Family codes: `01` DS2401/DS1990 (serial number only), `2d` DS2431, `23`
DS2433, `1c` DS28E04, `28` DS18B20. Module ID chips identify the device under
test; the ID-to-J1939-NAME mapping belongs in the contracts repo.

## GPIO (libgpiod v2)

| GPIO | Board function |
| --- | --- |
| 17, 27 | isolated outputs OUT1, OUT2 (non-inverting, level set by jumper J8: 3.3 V default or 5 V) |
| 23, 24 | isolated inputs IN1, IN2 (logic level, no pull resistors) |
| 20, 26 | user LEDs green, red (active-low: 0 = on) |
| 22 | buzzer (active-low) |
| 16 | RTC interrupt (input) |
| 6 | M.2 power: high = 4G module off |
| 18, 21 | free, on test pads P4, P8 (19 = pad P7 is the 1-Wire bus) |

The CM4 device tree names the lines `GPIO0`..`GPIO27`, so they can be used by
name:

```bash
gpioinfo | head -40
gpioget GPIO23
gpioset -t0 GPIO17=1    # OUT1 high
gpioset -t0 GPIO20=0    # green LED on
```

A value set with `gpioset` is only guaranteed while the line is held; use a
service for anything that must stay set (e.g. a DUT power relay).

## Time

```bash
timedatectl
cat /sys/class/rtc/rtc0/time
```

The PCF85063A RTC sets the clock at boot; systemd-timesyncd corrects it from
NTP and the kernel writes it back to the RTC every 11 minutes while synchronised.

## Keeping services off the network

Set `OPENOCD_BIND="127.0.0.1"` and reach OpenOCD through SSH:

```bash
ssh -N -L 3333:127.0.0.1:3333 -L 3334:127.0.0.1:3334 root@bench-07.local
```

ser2net and socketcand listen on all addresses and have no bind option here;
firewall them or rely on an isolated bench network ([ADR 0006](adr/0006-unauthenticated-services-on-bench-segment.md)).
