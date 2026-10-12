SUMMARY = "rdebugger networking: systemd-networkd links, Wi-Fi client, mDNS"
DESCRIPTION = "eth0 (DHCP, IPv4 link-local fallback, preferred route) and optional \
wlan0 for systemd-networkd, the wpa_supplicant-nl80211@wlan0 instance (started by udev \
when wlan0 appears, runs only once Wi-Fi is provisioned), a shorter network-online wait \
and the avahi SSH advertisement. \
The CAN link is configured at runtime by rdebugger-setup (bit rate from station.conf)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://20-eth0.network \
    file://30-wlan0.network \
    file://wait-online-rdebugger.conf \
    file://wpa-supplicant-rdebugger.conf \
    file://80-rdebugger-wifi.rules \
    file://ssh.service \
"
S = "${UNPACKDIR}"

inherit allarch

do_install() {
    install -d ${D}${systemd_unitdir}/network
    install -m 0644 ${S}/20-eth0.network ${S}/30-wlan0.network ${D}${systemd_unitdir}/network/

    install -d ${D}${systemd_system_unitdir}/systemd-networkd-wait-online.service.d
    install -m 0644 ${S}/wait-online-rdebugger.conf \
        ${D}${systemd_system_unitdir}/systemd-networkd-wait-online.service.d/50-rdebugger.conf

    install -d ${D}${systemd_system_unitdir}/wpa_supplicant-nl80211@.service.d
    install -m 0644 ${S}/wpa-supplicant-rdebugger.conf \
        ${D}${systemd_system_unitdir}/wpa_supplicant-nl80211@.service.d/50-rdebugger.conf

    install -d ${D}${nonarch_base_libdir}/udev/rules.d
    install -m 0644 ${S}/80-rdebugger-wifi.rules ${D}${nonarch_base_libdir}/udev/rules.d/

    install -d ${D}${sysconfdir}/avahi/services
    install -m 0644 ${S}/ssh.service ${D}${sysconfdir}/avahi/services/ssh.service
}

FILES:${PN} += " \
    ${systemd_unitdir}/network \
    ${systemd_system_unitdir} \
    ${nonarch_base_libdir}/udev/rules.d \
"

RDEPENDS:${PN} = " \
    avahi-daemon \
    iw \
    systemd-networkd \
    wireless-regdb-static \
    wpa-supplicant \
"
