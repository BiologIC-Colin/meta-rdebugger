# Flashing the CM4 eMMC

The CM4's eMMC is not removable. To write it, put the CM4 into USB boot mode
so that `rpiboot` turns it into a USB mass-storage device, then write the image
to that device.

## 1. Put the CM4 into rpiboot mode

On the Waveshare CM4 PoE 4G board, the CM4's single USB 2.0 port goes through
a switch (FSUSB42) to either the **USB SLAVE** Type-C or the on-board hub
(USB-A ports, M.2). Power on the USB SLAVE port does two things: it switches
the CM4 USB to that port, and it pulls nRPIBOOT low, so the CM4 boots into USB
boot mode instead of the eMMC.

1. Connect the board's **USB SLAVE** Type-C to the computer that runs rpiboot.
   (Some board revisions also have a BOOT switch; if yours does, set it to ON.
   A jumper on the "Multifunction" header P14 pins 1-2, GND-BOOT, does the same.)
2. Power the board from PoE or the 7-36 V DC input. The USB SLAVE port does not
   power it.
3. Run **rpiboot** in mass-storage-gadget mode:
   - Windows: *Raspberry Pi USB boot* installer 1.1 from
     <https://github.com/raspberrypi/usbboot/releases>, then
     `rpiboot -d mass-storage-gadget64` (the installer's shortcut does this).
     Tested and working with this CM4.
   - Linux: build usbboot from source, then `sudo ./rpiboot -d mass-storage-gadget64`.

The eMMC then appears as a USB disk (Windows: `0a5c:0104` "USB Mass Storage
Device" plus a gadget serial console, e.g. COM5). Windows may offer to format
it: **cancel**.

## 2. Write the image

Image: `tmp/deploy/images/waveshare-cm4-poe-4g/rdebugger-image-waveshare-cm4-poe-4g.rootfs.wic.bz2`
in the build directory (with its `.wic.bmap` next to it).

### Option A: balenaEtcher on the computer with the USB connection

```powershell
scp -P 2222 colin@10.0.100.164:/home/colin/yocto/bitbake-builds/rdebugger-dev/build/tmp/deploy/images/waveshare-cm4-poe-4g/rdebugger-image-waveshare-cm4-poe-4g.rootfs.wic.bz2 .
```

Open Etcher, choose the `.wic.bz2` (no need to decompress), select the
~8/16/32 GB "RPi-MSD" / mass-storage drive, Flash.

### Option B: bmaptool on P8 over usbipd (USB forwarded through SSH)

This lets the build host write the eMMC while the board is plugged into a
laptop elsewhere (e.g. at home on the VPN). bmaptool writes only the blocks
that hold data and verifies them, so it is much faster than a full write.

On the laptop (once): install usbipd-win, `winget install --exact dorssel.usbipd-win`.

On the laptop, each time:

1. Share the CM4 (admin PowerShell; find the BUSID of the mass-storage device
   with `usbipd list`):
   ```powershell
   usbipd bind --busid 4-1
   ```
2. Forward usbipd's port to P8 through SSH (ordinary PowerShell, leave open):
   ```powershell
   ssh -N -R 3240:127.0.0.1:3240 -p 2222 colin@10.0.100.164
   ```

On P8 (WSL): the `vhci-hcd` and `usb-storage` modules load at boot
(`/etc/modules-load.d/usbip-client.conf`); `usbip` comes from
`linux-tools-generic`.

1. Attach the device and find its block device:
   ```bash
   sudo /usr/lib/linux-tools/*/usbip list -r 127.0.0.1
   ```
   ```bash
   sudo /usr/lib/linux-tools/*/usbip attach -r 127.0.0.1 -b 4-1
   ```
   ```bash
   lsblk -o NAME,SIZE,MODEL,TRAN
   ```
   The eMMC is the new `sdX` disk of 8/16/32 GB. **Check the size**: writing to
   the wrong disk destroys it.
2. Write and verify:
   ```bash
   sudo bmaptool copy tmp/deploy/images/waveshare-cm4-poe-4g/rdebugger-image-waveshare-cm4-poe-4g.rootfs.wic.bz2 /dev/sdX
   ```
3. Detach (port number from `usbip port`):
   ```bash
   sudo /usr/lib/linux-tools/*/usbip detach -p 0
   ```

On the laptop, `usbipd unbind --busid 4-1` gives the device back to Windows.

## 3. Boot the new image

1. Optionally copy provisioning files onto the boot partition first
   ([provisioning.md](provisioning.md)): with Option A it shows up as a drive
   after flashing; with Option B, mount `/dev/sdX1` on P8.
2. **Disconnect the USB SLAVE cable** (and set the BOOT switch to OFF if your
   board has one). While that cable carries power the CM4 boots into rpiboot
   again, and the USB-A ports (ST-LINK, FTDI) stay disconnected.
3. Power-cycle the board (unplug PoE or DC).
4. First boot takes a little longer: SSH host keys are generated and the root
   filesystem is grown to fill the eMMC.

## 4. Find the station

- mDNS: `ssh root@rdebugger-<serial>.local` (the serial is printed on the CM4
  label; `avahi-browse -rt _rdebugger._tcp` lists all stations).
- DHCP: look for a Raspberry Pi MAC (`d8:3a:dd`, `dc:a6:32`, `e4:5f:01`,
  `2c:cf:67`, `88:a2:9e`) with hostname `rdebugger-...` in the router's lease list.
- No DHCP (laptop cabled straight to the station): both ends fall back to
  169.254.x.x after a few seconds; use the `.local` name.
- Serial console: the board's **USB TO UART** Type-C (CP2102, jumper block J1
  in its default position) is GPIO14/15 = ttyAMA0, 115200 8N1. The dev image
  logs in as root without a password.

After a reflash the station has new SSH host keys:
`ssh-keygen -R rdebugger-<serial>.local`.

## Reflashing remotely (not set up)

Today a reflash needs the USB SLAVE cable connected and a power cycle, so
someone (or a USB power switch) has to be at the board. For unattended
benches, the options are:

- an A/B update system (RAUC, SWUpdate or Mender) so images are installed over
  the network;
- a CM4 bootloader `BOOT_ORDER` ending in `3` (RPIBOOT), e.g. `0x31`, so a
  station whose eMMC no longer boots falls back to USB boot by itself. The
  EEPROM is only writable through rpiboot (`rpiboot -d recovery` with a custom
  `boot.conf`).

See [open-questions.md](open-questions.md).
