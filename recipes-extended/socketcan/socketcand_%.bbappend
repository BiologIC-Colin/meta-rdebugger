# rdebugger: socketcand ships no service unit. Run it on a local AF_UNIX socket
# for the CAN interface from station.conf, exposed on TCP 29536 (all
# addresses) through systemd-socket-proxyd.
FILESEXTRAPATHS:prepend := "${THISDIR}/files:"

SRC_URI += " \
    file://socketcand.service \
    file://socketcand-proxy.socket \
    file://socketcand-proxy.service \
"

# Without libsocketcan, a client's "< open can0 >" no longer tries to start the
# interface and "< can0 B ... >" cannot change the bit rate: systemd-networkd
# alone owns can0's configuration, and socketcand runs unprivileged.
PACKAGECONFIG:remove = "libsocketcan"

inherit systemd

SYSTEMD_SERVICE:${PN} = "socketcand.service socketcand-proxy.socket"

do_install:append() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${UNPACKDIR}/socketcand.service ${UNPACKDIR}/socketcand-proxy.socket \
        ${UNPACKDIR}/socketcand-proxy.service ${D}${systemd_system_unitdir}/
}
