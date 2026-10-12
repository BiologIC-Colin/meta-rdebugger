# rdebugger: the station's probe is an ST-LINK (st-link/dapdirect driver,
# libusb1). Ask for the drivers explicitly so configure fails loudly instead of
# silently building OpenOCD without them. (The recipe gets libusb1 only
# indirectly, through libusb-compat and libftdi.)
DEPENDS += "libusb1"
EXTRA_OECONF += "--enable-stlink --enable-cmsis-dap-v2"
