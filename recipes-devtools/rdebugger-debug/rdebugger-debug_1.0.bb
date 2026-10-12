SUMMARY = "rdebugger debug server: OpenOCD target configs, service and USB rules"
DESCRIPTION = "OpenOCD configurations for the station's targets (STM32H747XI \
dual-core, STM32F767ZI), a systemd service that runs OpenOCD unprivileged while \
an ST-LINK is plugged in, and udev rules for the ST-LINK and the FTDI DUT UART \
(/dev/dut-uart). The active target is chosen in /etc/rdebugger/station.conf."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://stm32h747.cfg \
    file://stm32f767.cfg \
    file://rdebugger-openocd.service \
    file://70-rdebugger-usb.rules \
"
S = "${UNPACKDIR}"

inherit allarch

do_install() {
    install -d ${D}${datadir}/rdebugger/openocd
    install -m 0644 ${S}/stm32h747.cfg ${S}/stm32f767.cfg ${D}${datadir}/rdebugger/openocd/

    # Not enabled: udev starts it (SYSTEMD_WANTS) while an ST-LINK is present.
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/rdebugger-openocd.service ${D}${systemd_system_unitdir}/

    install -d ${D}${nonarch_base_libdir}/udev/rules.d
    install -m 0644 ${S}/70-rdebugger-usb.rules ${D}${nonarch_base_libdir}/udev/rules.d/

    # Images to flash through OpenOCD's telnet/Tcl "program" command. OpenOCD
    # runs with a private /tmp, so it cannot see files copied to /tmp.
    install -d -m 0755 ${D}${localstatedir}/lib/rdebugger/upload
}

FILES:${PN} += " \
    ${datadir}/rdebugger \
    ${localstatedir}/lib/rdebugger/upload \
    ${systemd_system_unitdir} \
    ${nonarch_base_libdir}/udev/rules.d \
"

RDEPENDS:${PN} = "openocd rdebugger-base"
