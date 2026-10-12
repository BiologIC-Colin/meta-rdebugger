SUMMARY = "rdebugger remote debug station image"
DESCRIPTION = "Headless bench node: OpenOCD for an ST-LINK, the DUT UART over \
ser2net, SocketCAN + socketcand, 1-Wire, systemd-networkd on PoE Ethernet \
(optional Wi-Fi), avahi mDNS and OpenSSH."
LICENSE = "MIT"

inherit core-image

# Lean on purpose: packagegroup-core-boot instead of core-image's default
# packagegroup-base-extended (which would install every kernel module).
IMAGE_INSTALL = " \
    packagegroup-core-boot \
    packagegroup-rdebugger \
    ${CORE_IMAGE_EXTRA_INSTALL} \
"

IMAGE_FEATURES += "ssh-server-openssh"

IMAGE_LINGUAS = ""

# rdebugger-growfs grows the root filesystem to fill the eMMC at first boot;
# leave a little headroom in the image itself.
IMAGE_ROOTFS_EXTRA_SPACE = "131072"
