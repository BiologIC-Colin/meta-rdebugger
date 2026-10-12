# Hardware: CM4 on the Waveshare CM4 PoE 4G Board

Machine `waveshare-cm4-poe-4g`. Sources: Waveshare wiki
<https://www.waveshare.com/wiki/Compute_Module_4_PoE_4G_Board>, the board
schematic (<https://files.waveshare.com/upload/7/78/Compute_Module_4_PoE_4G_Board_SchDoc.pdf>),
and the Raspberry Pi kernel 6.18 device trees. Waveshare ships several board
revisions and its wiki has errors (noted below); check against your board.

## Compute module

Raspberry Pi CM4 with Wi-Fi and eMMC (the SD slot is ignored on eMMC CM4s).
BCM2711, 64-bit kernel `linux-raspberrypi` 6.18, device tree
`bcm2711-rpi-cm4.dtb`. The eMMC is `/dev/mmcblk0` (root `mmcblk0p2`); Wi-Fi is
on a separate SDIO controller.

## Board connectors used by rdebugger

| Connector | Use | Notes |
| --- | --- | --- |
| RJ45 PoE | eth0, power | 802.3af, PoE jumper on EN; ~12.5 W at 5 V (budget below) |
| DC jack / terminal | power | 7-36 V; Waveshare recommends 12 V / 2 A |
| USB-A x2 | ST-LINK, FTDI | via the on-board hub; needs DWC2 host mode |
| USB SLAVE Type-C | flashing (rpiboot) | power on this port forces rpiboot and disconnects the hub; keep it unplugged in service |
| USB TO UART Type-C | serial console | CP2102 on GPIO14/15 via jumper block J1 (default position) |
| Isolated terminal P5 | CAN, RS485, I2C, ADC, DIO | labels: `SGND IN2 IN1 OUT2 OUT1 5V`, `SDA SCL SGND`, `AD+ AD- SGND`, `H L COM`, `A+ B- COM` |
| DB9 male | RS232 | TX/RX only |
| Test pads P4/P7/P8 | GPIO18/19/21 | the only free GPIOs; P7 = 1-Wire |
| Multifunction header P14 (2x10) | control signals | pin 1-2 = GND-nRPIBOOT, 3-4 = GND-EEPROM_WP, RUN, GLOBAL_EN; no GPIOs |

There is **no 40-pin GPIO header** on this board.

## GPIO map

| GPIO | Function | rdebugger |
| --- | --- | --- |
| 0, 1 | ID EEPROM (CAT24C32), CAM0/DISP0 I2C | unused |
| 2, 3 | I2C1 to NSi8100 isolator: isolated SDA/SCL, ADS1113 ADC @ 0x48 | `ENABLE_I2C`, `/dev/i2c-1` (ADC read from user space, e.g. `i2cget`/smbus; no kernel driver bound) |
| 4, 5 | UART3 TX/RX to isolator, MAX3232, DB9 (RS232) | `dtoverlay=uart3`, `/dev/ttyAMA3` |
| 6 | M.2 FULL_CARD_POWER_OFF# (high = 4G module off) | unused (module powers on by default if fitted) |
| 7 | unconnected (SPI0 CE1) | - |
| 8, 9, 10, 11 | SPI0 CE0/MISO/MOSI/SCLK to MCP2515 | `ENABLE_SPI_BUS`, `ENABLE_CAN` |
| 12, 13 | UART5 TX/RX to isolator, SP3485 (RS485, automatic direction) | `dtoverlay=uart5`, `/dev/ttyAMA5` |
| 14, 15 | UART0 to J1 jumper, CP2102 USB-UART | console `ttyAMA0` (with `disable-bt`) |
| 16 | RTC interrupt (open-drain buffer) | unused |
| 17, 27 | isolated OUT1, OUT2 (ADuM1412, non-inverting push-pull at IO_VDD, J8: 3.3 V default / 5 V) | libgpiod |
| 18 | test pad P4 | free |
| 19 | test pad P7 | **1-Wire** (`w1-gpio`) |
| 20, 26 | user LEDs green, red (active-low) | libgpiod |
| 21 | test pad P8 | free (the wiki wrongly calls it the red LED) |
| 22 | buzzer (active-low) | libgpiod |
| 23, 24 | isolated IN1, IN2 (logic level, non-inverting, no pulls) | libgpiod |
| 25 | MCP2515 INT | `ENABLE_CAN` (`interrupt=25`) |
| 44, 45 | I2C0 (i2c_csi_dsi): PCF85063A RTC @ 0x51, EMC2301 fan @ 0x2F | `/dev/i2c-10` |

Raw GPIO bank voltage is 3.3 V (0R on the 3V3 side of the IO-VREF selector H1).

## Peripherals and their config.txt

All set in `conf/machine/waveshare-cm4-poe-4g.conf`; the generated file is
`tmp/deploy/images/waveshare-cm4-poe-4g/bootfiles/config.txt`.

| Peripheral | Variable / line | Device |
| --- | --- | --- |
| Console | `ENABLE_UART = "1"`, `dtoverlay=disable-bt` | `ttyAMA0` 115200 8N1 (`console=serial0`) |
| USB host (hub, USB-A, M.2) | `ENABLE_DWC2_HOST = "1"` -> `dtoverlay=dwc2,dr_mode=host` | `otg_mode=1` (XHCI) does not work with this hub |
| CAN | `ENABLE_SPI_BUS`, `ENABLE_CAN` -> `dtoverlay=mcp2515-can0,oscillator=16000000,interrupt=25` | `can0` |
| Isolated I2C + ADC | `ENABLE_I2C` -> `dtparam=i2c1=on`, `dtparam=i2c_arm=on` | `/dev/i2c-1`, ADS1113 @ 0x48 |
| RTC | `dtparam=i2c_vc=on`, `dtoverlay=i2c-rtc,pcf85063a,i2c_csi_dsi` | `rtc0` on `/dev/i2c-10` |
| RS232 | `dtoverlay=uart3` | `/dev/ttyAMA3` |
| RS485 | `dtoverlay=uart5` (no `rs485`/`ctsrts` params: they would take GPIO14/15) | `/dev/ttyAMA5` |
| 1-Wire | `dtoverlay=w1-gpio,gpiopin=19` (`RDEBUGGER_W1_GPIO`) | `/sys/bus/w1/devices/` |
| Bluetooth | `dtoverlay=disable-bt` | none |

Not configured: the EMC2301 fan controller (in-tree option:
`dtoverlay=i2c-fan,emc2301,i2c_csi_dsi`, needs `overlays/i2c-fan.dtbo` added to
`RPI_KERNEL_DEVICETREE_OVERLAYS`), the 4G module, DSI/CSI (they share i2c-10
with the RTC).

### CAN

MCP2515 with a 16 MHz crystal, isolated (Si8642 isolator + SN65HVD230 3.3 V
transceiver, SM24CANB TVS). Termination: 120 Ω jumper on the board
(silkscreen `120R`), off by default; fit it if the station is at a bus end.
Classic CAN only (no CAN FD). Terminal `H L COM`.

Only `BitRate` (and `SamplePoint`, `RestartSec`, `ListenOnly`,
`TripleSampling`, `Loopback`) may be used in networkd's `[CAN]` section for
this chip: `FDMode`, `Termination`, `BusErrorReporting`, `OneShot`,
`PresumeACK` and `ClassicDataLengthCode` are rejected by the mcp251x driver
and leave can0 in the `failed` state.

### 1-Wire

Bit-banged `w1-gpio` master on GPIO19 = test pad **P7** (CM4 connector pin
26). Raw 3.3 V CMOS: not isolated, no ESD protection.

```
 3.3 V ──┬─────────────── (VDD of the 1-Wire device, or leave for parasite power)
         4.7 kΩ
 P7 ─────┴── 100 Ω ──────── DQ
 GND ────────────────────── GND
```

For cables that leave the bench add a TVS on DQ. For long buses, parasite
power or isolation, use a DS2482-100 I2C-to-1-Wire bridge on the isolated
I2C1 terminal instead (shares the bus with the ADS1113; the `ds2482` driver
is in the image; instantiate it with
`echo ds2482 0x18 > /sys/bus/i2c/devices/i2c-1/new_device`). The DS2482 route
is the recommended design for our own carrier board.

### RTC

PCF85063A with a backup cell (wiki: CR1220). It sets the system clock when its
driver probes (kernel `RTC_HCTOSYS`), and the kernel writes NTP time back
every 11 minutes while synchronised (`RTC_SYSTOHC`). Needs
`kernel-module-i2c-mux-pinctrl`, because i2c-10 is a pinctrl mux of I2C0.

### Power budget

PoE (802.3af) gives about 5 V / 2.5 A; the board's total 5 V budget is 4 A
including the CM4. CM4 + Wi-Fi + ST-LINK + FTDI fits. The isolated side (CAN,
RS485, RS232, ADC, DIO and the terminal's 5 V pin) runs from one 1 W isolated
DC-DC (about 200 mA at 5 V in total). A 4G module or a fan on 12 V needs the
DC input.

## Debug probe: ST-LINK/V2-ISOL

- USB `0483:3748` (ST-LINK/V2 family; confirm with `lsusb`). No virtual COM
  port: the DUT console needs the FTDI.
- Firmware: **V2J24 or later** for OpenOCD's `st-link` (dapdirect) driver;
  **V2J29 minimum, V2J32 or later recommended** for the STM32H747's second
  core (AP3) and AP2. Update with STM32CubeProgrammer or STSW-LINK007 on a PC.
- 20-pin connector (UM1075): 1 VAPP = target VDD reference (**must be
  connected**), 7 SWDIO, 9 SWCLK, 13 SWO (optional), 15 NRST, GND on 12/18/20.
  On the ISOL, pins 4/6/8/16/19 are not connected and 10/14 carry SWIM: do not
  rely on them as ground.
- Max SWD clock 4000 kHz; the target scripts start at 1800 kHz and raise it
  after reset-init. Lower it for long ribbons if you see WAIT/FAULT errors.

### Alternative: a Nucleo board's on-board ST-LINK/V2-1

- USB `0483:374b` (covered by the udev rule). The Nucleo-64's ST-LINK NRST is
  wired to the target's NRST, so `OPENOCD_RESET_CONFIG="srst_only srst_nogate"`
  works. Firmware V2J24 or later applies here too.
- It also has a virtual COM port (`/dev/ttyACM0`, cdc-acm is in the image) and
  a mass-storage drive. On Nucleo-64 boards (UM1724) the target's USART2
  (PA2/PA3) goes to that VCP by default and the Arduino D0/D1 pins are **not**
  connected (SB62/SB63 open). So an FTDI on D0/D1 sees nothing from USART2:
  wire it to the USART the firmware uses, or set
  `DUT_UART_DEVICE="/dev/serial/by-id/usb-STMicroelectronics_STM32_STLink_<serial>-if02"`.
- Some Nucleo ST-LINKs have been converted to SEGGER J-Link OB (USB vendor
  `1366`). This image's OpenOCD has no J-Link driver: `rdebugger-status` and
  `rdebugger-selftest` report it. Restore the ST-LINK firmware on a Windows PC
  with SEGGER's STLinkReflash, then update it with STSW-LINK007.

## DUT UART: FTDI USB-UART

Any FTDI (FT232R/FT232H/FT2232/FT4232) on a USB-A port: the first port becomes
`/dev/dut-uart`. Match the cable's I/O voltage to the target (3.3 V for the
STM32 boards). Cross TX/RX and connect GND.

## Bootloader (CM4 EEPROM)

The pinned rpi-eeprom default config has no `BOOT_ORDER` (that is, `0xf41`:
SD/eMMC, then USB mass storage, then restart), `WAKE_ON_GPIO=1` and
`POWER_OFF_ON_HALT=0`: after `poweroff` the board stays powered in a low-power
state and wakes if GPIO3 (isolated I2C SCL) is pulled low. Check the actual
values with `vcgencmd bootloader_config` (needs the userland tools) or by
reading the EEPROM via rpiboot. Changing them needs `rpiboot -d recovery`.
