SUMMARY = "rdebugger station software"
DESCRIPTION = "Everything the remote debug station runs on top of packagegroup-core-boot, \
grouped by job. Board drivers come from the machine \
(MACHINE_ESSENTIAL_EXTRA_RRECOMMENDS)."

inherit packagegroup

PACKAGES = " \
    ${PN} \
    ${PN}-base \
    ${PN}-debug \
    ${PN}-serial \
    ${PN}-can \
    ${PN}-onewire \
    ${PN}-tools \
"

RDEPENDS:${PN} = " \
    ${PN}-base \
    ${PN}-debug \
    ${PN}-serial \
    ${PN}-can \
    ${PN}-onewire \
    ${PN}-tools \
"

SUMMARY:${PN}-base = "Station base: provisioning, networking, identity"
RDEPENDS:${PN}-base = " \
    os-release \
    rdebugger-base \
    rdebugger-network \
    tzdata \
"

SUMMARY:${PN}-debug = "SWD/JTAG debug server (OpenOCD) for the ST-LINK"
RDEPENDS:${PN}-debug = " \
    openocd \
    rdebugger-debug \
"

SUMMARY:${PN}-serial = "DUT UART over the network (ser2net) and serial tools"
RDEPENDS:${PN}-serial = " \
    picocom \
    ser2net \
"
RRECOMMENDS:${PN}-serial = " \
    kernel-module-cdc-acm \
    kernel-module-ch341 \
    kernel-module-cp210x \
    kernel-module-ftdi-sio \
    kernel-module-pl2303 \
"

SUMMARY:${PN}-can = "SocketCAN tools and CAN over TCP (socketcand)"
RDEPENDS:${PN}-can = " \
    can-utils \
    can-utils-cantest \
    can-utils-isotp \
    can-utils-j1939 \
    can-utils-log \
    iproute2-ip \
    socketcand \
"
RRECOMMENDS:${PN}-can = " \
    kernel-module-can-bcm \
    kernel-module-can-gw \
    kernel-module-can-isotp \
    kernel-module-can-j1939 \
    kernel-module-can-raw \
"

SUMMARY:${PN}-onewire = "1-Wire master and slave drivers (module ID chips, sensors)"
RRECOMMENDS:${PN}-onewire = " \
    kernel-module-ds2482 \
    kernel-module-w1-ds2431 \
    kernel-module-w1-ds2433 \
    kernel-module-w1-ds28e04 \
    kernel-module-w1-therm \
"
ALLOW_EMPTY:${PN}-onewire = "1"

SUMMARY:${PN}-tools = "Bring-up and diagnostics tools"
RDEPENDS:${PN}-tools = " \
    ethtool \
    htop \
    i2c-tools \
    iputils-ping \
    less \
    libgpiod-tools \
    nano \
    rsync \
    tmux \
    usbutils \
"
