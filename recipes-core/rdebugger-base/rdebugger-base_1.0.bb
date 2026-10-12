SUMMARY = "rdebugger station base: provisioning, identity, runtime configuration"
DESCRIPTION = "Station configuration (station.defaults + /etc/rdebugger/station.conf), \
boot-partition provisioning (Wi-Fi, SSH keys, station.conf), hostname from the CM4 \
serial number, first-boot root filesystem growth, persistent journal, hardware \
watchdog, the rdebugger-status report and the rdebugger-selftest bring-up checks."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://station.defaults \
    file://rdebugger-setup \
    file://rdebugger-setup.service \
    file://rdebugger-growfs \
    file://rdebugger-growfs.service \
    file://rdebugger-status \
    file://rdebugger-selftest \
    file://journald-rdebugger.conf \
    file://system-rdebugger.conf \
"
S = "${UNPACKDIR}"

inherit systemd allarch

SYSTEMD_SERVICE:${PN} = "rdebugger-setup.service rdebugger-growfs.service"

do_install() {
    install -d ${D}${nonarch_libdir}/rdebugger
    install -m 0644 ${S}/station.defaults ${D}${nonarch_libdir}/rdebugger/station.defaults

    install -d ${D}${sysconfdir}/rdebugger

    install -d ${D}${sbindir}
    install -m 0755 ${S}/rdebugger-setup ${D}${sbindir}/rdebugger-setup
    install -m 0755 ${S}/rdebugger-growfs ${D}${sbindir}/rdebugger-growfs
    install -d ${D}${bindir}
    install -m 0755 ${S}/rdebugger-status ${D}${bindir}/rdebugger-status
    install -m 0755 ${S}/rdebugger-selftest ${D}${bindir}/rdebugger-selftest

    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${S}/rdebugger-setup.service ${D}${systemd_system_unitdir}/
    install -m 0644 ${S}/rdebugger-growfs.service ${D}${systemd_system_unitdir}/

    install -d ${D}${systemd_unitdir}/journald.conf.d ${D}${systemd_unitdir}/system.conf.d
    install -m 0644 ${S}/journald-rdebugger.conf ${D}${systemd_unitdir}/journald.conf.d/50-rdebugger.conf
    install -m 0644 ${S}/system-rdebugger.conf ${D}${systemd_unitdir}/system.conf.d/50-rdebugger.conf
}

FILES:${PN} += " \
    ${nonarch_libdir}/rdebugger \
    ${systemd_unitdir}/journald.conf.d \
    ${systemd_unitdir}/system.conf.d \
"

# Tools the scripts call that busybox does not provide (or not fully).
RDEPENDS:${PN} = " \
    busybox \
    e2fsprogs-resize2fs \
    iproute2-ip \
    iproute2-ss \
    usbutils \
    util-linux-partx \
    util-linux-sfdisk \
"
