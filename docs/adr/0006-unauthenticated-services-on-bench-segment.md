# ADR 0006: Unauthenticated network services on an isolated bench segment

## Context

OpenOCD (GDB, telnet, Tcl), ser2net and socketcand have no authentication.
The operating model puts benches on their own network segment and has agents
use a typed tool layer rather than raw access.

## Decision

- v0.1 exposes these services on all interfaces by default so workstations,
  CI and the future tool layer can reach them directly.
- They run as unprivileged dynamic users (ser2net in dialout, OpenOCD in
  plugdev, socketcand and its TCP proxy with no extra groups). socketcand is
  built without libsocketcan, so clients cannot stop can0 or change its bit
  rate.
- OpenOCD can be limited to localhost (`OPENOCD_BIND=127.0.0.1`) and reached
  through SSH tunnels.
- SSH is the only authenticated entry point; release images accept keys only.

## Consequences

- Stations must only be connected to a bench network that is isolated from
  the office LAN and the internet, or be firewalled. Bring-up on the office or
  home LAN is acceptable for development only.
- A host firewall (nftables) and bind options for ser2net/socketcand are open
  work items.
