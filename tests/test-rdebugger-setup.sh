#!/bin/sh
# Test rdebugger-setup on the image's own busybox, without hardware.
#
# Copies a built rootfs, drops the layer's current rdebugger-setup and
# station.defaults into it, provisions fake boot-partition files, and runs
# "rdebugger-setup boot" in a chroot (aarch64 binaries run through qemu-user
# binfmt) inside private mount and UTS namespaces, so the host's hostname,
# /run and /boot are untouched. Then checks the generated files.
#
# Needs: qemu-user-static with binfmt_misc (flag F), unshare, sudo.
# Usage:
#   sudo tests/test-rdebugger-setup.sh \
#     ~/yocto/bitbake-builds/rdebugger-dev/build/tmp/work/waveshare_cm4_poe_4g-rdebugger-linux/rdebugger-image/1.0/rootfs

set -eu

ROOTFS=${1:?usage: $0 <image rootfs directory>}
LAYER=$(cd "$(dirname "$0")/.." && pwd)
FILES=$LAYER/recipes-core/rdebugger-base/files
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
R=$WORK/rootfs

cp -a "$ROOTFS" "$R"
cp "$FILES/rdebugger-setup" "$R/usr/sbin/rdebugger-setup"
chmod 0755 "$R/usr/sbin/rdebugger-setup"
cp "$FILES/station.defaults" "$R/usr/lib/rdebugger/station.defaults"
# The layer's current OpenOCD target configs (TARGET validation checks they exist).
mkdir -p "$R/usr/share/rdebugger/openocd"
cp "$LAYER"/recipes-devtools/rdebugger-debug/files/*.cfg "$R/usr/share/rdebugger/openocd/"

# Fake CM4 serial number (/sys is not mounted in the chroot).
mkdir -p "$R/sys/firmware/devicetree/base"
printf '10000000a1b2c3d4\0' > "$R/sys/firmware/devicetree/base/serial-number"

# Provisioning files as Windows would write them (UTF-8 BOM, CRLF).
mkdir -p "$R/boot"
printf '\357\273\277TARGET=stm32f767   # F7 board\r\n# bench test\r\nCAN_BITRATE="250000"  # J1939\r\nOPENOCD_ADAPTER_KHZ=950\r\nOPENOCD_RESET_CONFIG="srst_only srst_nogate" # NRST wired\r\nBOGUS=1\r\nDUT_UART_BAUD=fast\r\n' > "$R/boot/rdebugger.conf"
printf 'ctrl_interface=DIR=/var/run/wpa_supplicant GROUP=netdev\r\nupdate_config=1\r\ncountry=GB\r\nnetwork={\r\n ssid="lab"\r\n psk=0123\r\n}\r\n' > "$R/boot/wpa_supplicant.conf"
printf 'ssh-ed25519 AAAAtest test@example\r\n' > "$R/boot/authorized_keys"

cat > "$WORK/inner.sh" <<EOF
set -e
mount -t proc proc "$R/proc"
mount -t tmpfs tmpfs "$R/run"
chroot "$R" /usr/sbin/rdebugger-setup boot
chroot "$R" /usr/sbin/rdebugger-setup boot   # second run: idempotent, no rewrites
echo "exit status of a clean run: \$?" > "$WORK/rc"
chroot "$R" /bin/sh -c 'cat /run/rdebugger/station.env' > "$WORK/station.env"
chroot "$R" /bin/sh -c 'cat /run/rdebugger/openocd.cfg' > "$WORK/openocd.cfg"
chroot "$R" /bin/sh -c 'cat /run/rdebugger/ser2net.yaml' > "$WORK/ser2net.yaml"
chroot "$R" /bin/sh -c 'cat /run/systemd/network/60-rdebugger-can.network' > "$WORK/can.network"
chroot "$R" /bin/sh -c 'hostname' > "$WORK/hostname.kernel"
cp "$R/etc/avahi/services/rdebugger.service" "$WORK/avahi.service"
# A third target, then an unknown one without a config (falls back to the default).
printf 'TARGET=stm32f411\r\n' > "$R/boot/rdebugger.conf"
chroot "$R" /usr/sbin/rdebugger-setup boot
chroot "$R" /bin/sh -c 'cat /run/rdebugger/openocd.cfg' > "$WORK/openocd-f411.cfg"
cp "$R/etc/avahi/services/rdebugger.service" "$WORK/avahi-f411.service"
printf 'TARGET=bogus\r\n' > "$R/boot/rdebugger.conf"
chroot "$R" /usr/sbin/rdebugger-setup boot
chroot "$R" /bin/sh -c 'cat /run/rdebugger/station.env' > "$WORK/station-bogus.env"
EOF
unshare --mount --uts --fork sh "$WORK/inner.sh"

fails=0
ok()   { echo "PASS  $1"; }
bad()  { echo "FAIL  $1"; fails=$((fails + 1)); }
expect() { if grep -q -- "$2" "$1"; then ok "$3"; else bad "$3 (no '$2' in $(basename "$1"))"; fi; }

expect "$WORK/rc" 'exit status of a clean run: 0' "setup exits 0 when nothing failed"
[ "$(cat "$R/etc/hostname")" = rdebugger-a1b2c3d4 ] && ok "hostname from serial" || bad "hostname from serial: $(cat "$R/etc/hostname")"
[ "$(cat "$WORK/hostname.kernel")" = rdebugger-a1b2c3d4 ] && ok "kernel hostname set (namespaced)" || bad "kernel hostname"
[ -f "$R/boot/rdebugger.conf.applied" ] && [ ! -f "$R/boot/rdebugger.conf" ] && ok "rdebugger.conf imported and renamed" || bad "rdebugger.conf import"
[ ! -e "$R/boot/wpa_supplicant.conf" ] && ok "wpa_supplicant.conf removed from /boot" || bad "wpa_supplicant.conf still on /boot"
W=$R/etc/wpa_supplicant/wpa_supplicant-nl80211-wlan0.conf
[ "$(stat -c %a "$W" 2>/dev/null)" = 600 ] && ok "Wi-Fi config installed, mode 600" || bad "Wi-Fi config missing or wrong mode"
expect "$W" 'ctrl_interface=/run/wpa_supplicant' "ctrl_interface added"
grep -q 'GROUP=netdev' "$W" && bad "foreign ctrl_interface (GROUP=netdev) removed" || ok "foreign ctrl_interface (GROUP=netdev) removed"
[ "$(grep -c '^ctrl_interface=' "$W")" = 1 ] && ok "exactly one ctrl_interface" || bad "exactly one ctrl_interface"
grep -q "$(printf '\r')" "$W" && bad "CR stripped from Wi-Fi config" || ok "CR stripped from Wi-Fi config"
expect "$R/root/.ssh/authorized_keys" 'ssh-ed25519 AAAAtest' "SSH key installed"
[ "$(stat -c %a "$R/root/.ssh")" = 700 ] && ok "/root/.ssh mode 700" || bad "/root/.ssh mode"
expect "$WORK/station.env" 'TARGET="stm32f767"' "TARGET imported"
expect "$WORK/station.env" 'DUT_UART_BAUD="115200"' "invalid value replaced by default"
expect "$WORK/openocd.cfg" 'source /usr/share/rdebugger/openocd/stm32f767.cfg' "OpenOCD target config"
expect "$WORK/openocd.cfg" 'adapter speed 950' "OpenOCD adapter speed"
expect "$WORK/openocd.cfg" 'gdb port 3333' "OpenOCD gdb port"
expect "$WORK/openocd.cfg" 'foreach e {reset-start reset-init reset-end}' "OpenOCD speed kept through resets"
expect "$WORK/openocd.cfg" 'reset_config srst_only srst_nogate$' "OpenOCD reset_config (quoted value, trailing comment)"
expect "$WORK/can.network" 'BitRate=250000' "CAN bit rate (trailing comment ignored)"
expect "$WORK/ser2net.yaml" 'serialdev,/dev/dut-uart,115200N81,local' "ser2net connector"
expect "$WORK/avahi.service" 'target=stm32f767' "avahi TXT record"
expect "$WORK/openocd-f411.cfg" 'source /usr/share/rdebugger/openocd/stm32f411.cfg' "OpenOCD target config (F411)"
expect "$WORK/avahi-f411.service" 'target=stm32f411' "avahi TXT record (F411)"
expect "$WORK/station-bogus.env" 'TARGET="stm32h747"' "unknown TARGET without a config falls back to the default"
# busybox head/tail here have no legacy -N syntax: scripts must use -n N.
if grep -rnE '(head|tail) -[0-9]' "$LAYER"/recipes-*/ >/dev/null; then
    bad "scripts use head/tail -n N, not -N ($(grep -rlE '(head|tail) -[0-9]' "$LAYER"/recipes-*/ | tr '\n' ' '))"
else
    ok "scripts use head/tail -n N, not -N"
fi

echo
[ "$fails" -eq 0 ] && echo "all tests passed" || echo "$fails test(s) failed"
[ "$fails" -eq 0 ]
