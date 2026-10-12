# Open questions

Decisions and facts still needed. Each has a working default so the station
runs today; answer them and update station.conf, the machine file or an ADR.
As of 2026-10-11.

| # | Question | Default today | Who / how to settle |
| --- | --- | --- | --- |
| 1 | CAN bit rate of the module backplane (J1939-style: 250 or 500 kbit/s)? | `CAN_BITRATE="500000"` | Check the existing J1939-style library and DBC; record it in the CAN profile (contracts repo) |
| 2 | Is the ST-LINK's RESET (pin 15) wired to NRST on our target debug connectors? | `OPENOCD_RESET_CONFIG` empty = target script default (SYSRESETREQ) | Check the custom board schematics; if wired, enable connect-under-reset |
| 3 | ST-LINK/V2-ISOL firmware version? | needs V2J29 minimum, V2J32+ recommended, for the H747 M4 | Read the "STLINK V2J.." line in `journalctl -u rdebugger-openocd`; upgrade on a PC if older |
| 4 | 1-Wire: solder to test pad P7 (GPIO19) on this board, or add a DS2482 on the isolated I2C terminal? | w1-gpio on GPIO19 | Hardware decision; DS2482 recommended for our own carrier board |
| 5 | CAN FD or J1939 transport protocol for multi-frame data (operating model: "one choice, not both")? | MCP2515 = classic CAN only | If CAN FD: the carrier board needs an MCP2518FD (driver `mcp251xfd` is built) |
| 6 | Where do stations live on the network (isolated bench segment, VLAN, firewall)? | services open on all interfaces | IT / Role B; see ADR 0006. Bring-up on the office or home LAN only |
| 7 | NTP source on the bench segment (evidence timestamps)? | DHCP-provided NTP, else systemd's fallback pool | Provide a local NTP server on the bench segment |
| 8 | What do the isolated outputs drive (DUT power relay, NRST, BOOT0)? | unused | Define per bench; add GPIO line names (DT overlay) and a small control service |
| 9 | Remote reflash: A/B updates (RAUC/SWUpdate/Mender) or CM4 `BOOT_ORDER` fallback to rpiboot? | manual: USB SLAVE cable + power cycle | Needed before benches are unattended |
| 10 | Is a 4G module or a fan fitted? Does your board revision have a BOOT switch? | not configured | Look at the board; 4G powers on by default if fitted (GPIO6) |
| 11 | Wi-Fi on bench nodes at all? | supported, off until provisioned | Disable for production benches if not needed (smaller attack surface) |
| 12 | Python on the node (python-can, cantools) for the future tool layer and CI runner? | not installed (thin node) | Adds meta-python; decide with the tool-layer design |
| 13 | Hostname scheme: `rdebugger-<serial>` or bench names (`bench-07`)? | serial-based | Set `STATION_NAME` per bench if names are wanted |
| 14 | GitHub repository for this layer (`BiologIC-Colin/meta-rdebugger`, private)? | local git only | Create the empty repo; then push `main` |
| 15 | Release image access: root with keys only, or a non-root operator account? | root + `authorized_keys` | Decide with the tool-layer design (agents never get a raw shell) |
