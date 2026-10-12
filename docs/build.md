# Building

## Build host

Any Linux host supported by Yocto 6.0 works. The reference host is **P8**: WSL2
distro `Yocto` (Ubuntu 24.04) on a 48-thread / 255 GB Windows 11 PC. On a fresh
Ubuntu 24.04, install the host packages first:

```bash
sudo apt-get install build-essential chrpath cpio debianutils diffstat file gawk gcc git iputils-ping libacl1 libcrypt-dev locales python3 python3-git python3-jinja2 python3-pexpect python3-pip python3-subunit socat texinfo unzip wget xz-utils zstd
```

```bash
sudo locale-gen en_US.UTF-8
```

WSL notes for P8:

- `%UserProfile%\.wslconfig` needs `[general] instanceIdleTimeout=-1`, or WSL
  stops the distro about 15 s after the last terminal closes, killing builds
  and SSH sessions. Also `[wsl2] networkingMode=mirrored`.
- `/etc/wsl.conf`: `systemd=true`, `appendWindowsPath=false` (keeps the Windows
  PATH out of BitBake).
- `git://` (port 9418) is blocked on the office network; BitBake falls back to
  mirrors. The OpenOCD fetch from repo.or.cz falls back to GitHub.

## Directory layout

```
~/yocto/
├── bitbake/               bootstrap BitBake (2.20) - only runs bitbake-setup init
├── meta-rdebugger/        this layer (git)
└── bitbake-builds/        bitbake-setup top directory
    ├── site.conf          shared DL_DIR, SSTATE_DIR, hash-equivalence DB
    ├── .bitbake-setup-downloads/   shared downloads
    ├── .sstate-cache/              shared sstate
    └── rdebugger-dev/
        ├── layers/        bitbake 2.18, openembedded-core, meta-openembedded,
        │                  meta-raspberrypi, meta-rdebugger -> ~/yocto/meta-rdebugger
        ├── build/conf/    bblayers.conf, toolcfg.conf (fragments), local.conf (empty)
        └── config/        bitbake-setup state (config-upstream.json, fixed revisions)
```

## Creating the build

The setup JSON (`setup/rdebugger-wrynose.conf.json`) pins the sources and lists
the layers and configuration fragments. `-L` links the working copy of this
layer instead of cloning it from GitHub:

```bash
cd ~/yocto
./bitbake/bin/bitbake-setup init -L meta-rdebugger ~/yocto/meta-rdebugger \
    ~/yocto/meta-rdebugger/setup/rdebugger-wrynose.conf.json rdebugger-dev
```

Use `rdebugger-release` instead of `rdebugger-dev` for a release build
directory. Without `-L`, bitbake-setup clones
`ssh://git@github.com/BiologIC-Colin/meta-rdebugger.git` (branch `main`), which
must exist first.

## Configurations

| Configuration | Fragments | Use |
| --- | --- | --- |
| both | `distro/rdebugger`, `machine/waveshare-cm4-poe-4g` | always |
| `rdebugger-dev` | `core/yocto/root-login-with-empty-password`, `rdebugger/dev-tools` | bring-up and development: root without password on console and SSH, gdb/gdbserver/strace |
| `rdebugger-release` | none | benches: SSH keys only (provision `authorized_keys`) |

Fragments are named `<layer collection>/<path>`. This layer's collection is
`rdebugger`, so its fragments are `rdebugger/...`, not `meta-rdebugger/...`.

## Building

```bash
. ~/yocto/bitbake-builds/rdebugger-dev/build/init-build-env
bitbake rdebugger-image
```

Output in `tmp/deploy/images/waveshare-cm4-poe-4g/`:

| File | Use |
| --- | --- |
| `rdebugger-image-waveshare-cm4-poe-4g.rootfs.wic.bz2` | eMMC image (Etcher, bmaptool) |
| `rdebugger-image-waveshare-cm4-poe-4g.rootfs.wic.bmap` | block map for bmaptool (fast, verified writes) |
| `rdebugger-image-waveshare-cm4-poe-4g.rootfs.manifest` | installed packages and versions |
| `bootfiles/config.txt`, `bootfiles/cmdline.txt` | generated firmware configuration |

Long builds on P8: run them in `tmux` so a dropped SSH session does not stop them.

## Changing the build

| To change | Edit | Then |
| --- | --- | --- |
| Board hardware (overlays, console, drivers) | `conf/machine/waveshare-cm4-poe-4g.conf` | `bitbake rdebugger-image` |
| Distro policy (features, logging, licences) | `conf/distro/rdebugger.conf` | rebuild (many recipes rebuild) |
| Installed packages | `recipes-core/packagegroups/packagegroup-rdebugger.bb` | rebuild |
| Station defaults, scripts, units | `recipes-*/rdebugger-*/files/` | rebuild |
| Layers, sources, fragments per configuration | `setup/rdebugger-wrynose.conf.json` | `bitbake-setup update --update-bb-conf yes` from a sourced shell |

Do not hand-edit `build/conf/toolcfg.conf` or rely on `build/conf/local.conf`:
`bitbake-setup update` regenerates both. Durable configuration lives in this
layer.

Precedence: fragments are parsed after `local.conf` but before the machine and
distro files, so a hard `X = "..."` in `rdebugger.conf` or the machine file
overrides a fragment. Use `?=`, `+=` or `:append` there for anything a fragment
may also set, and never hard-assign `EXTRA_IMAGE_FEATURES` in the distro.

## Useful commands

```bash
bitbake-getvar -r rdebugger-image IMAGE_INSTALL      # resolved variable for a recipe
bitbake-getvar MACHINEOVERRIDES                      # rpi:raspberrypi4:raspberrypi4-64:waveshare-cm4-poe-4g
bitbake-config-build list-fragments                  # available fragments
bitbake -e rdebugger-base | less                     # full environment of a recipe
oe-pkgdata-util list-pkg-files rdebugger-base        # what a package installs
oe-pkgdata-util find-path /usr/bin/openocd           # which package ships a file
bitbake -c cleansstate <recipe>                      # force a recipe to rebuild
```

## Deploying a changed package without reflashing

For quick iterations on scripts and configs, copy files over SSH (the dev image
has `rsync`), or use devtool:

```bash
devtool modify rdebugger-base       # work in a source tree
devtool build rdebugger-base
devtool deploy-target rdebugger-base root@rdebugger-1a2b3c4d.local
```

Fold the change back into the layer before the next image build
(`devtool finish`).
