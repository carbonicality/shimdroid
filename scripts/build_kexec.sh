#!/usr/bin/env bash
set -eE
SCRIPT_DIR=$(cd "$(dirname "$0")/.." && pwd)
. "$SCRIPT_DIR/lib/common.sh"

BOARD="$1"
[ -n "$BOARD" ] || sd_fail "usage: $0 <board>"

CONF="$SCRIPT_DIR/boards/${BOARD}.conf"
[ -f "$CONF" ] || sd_fail "no board config at $CONF"
. "$CONF"

[ -n "$BOARD_ARCH" ] || sd_fail "$CONF: BOARD_ARCH not set"
[ -n "$KERNEL_COMPAT" ] || sd_fail "$CONF: KERNEL_COMPAT not set"
[ -n "$KERNEL_BUILD_DIR" ] || sd_fail "$CONF: KERNEL_BUILD_DIR not set"

KERNEL_BUILD_DIR="$SCRIPT_DIR/$KERNEL_BUILD_DIR"
[ -f "$KERNEL_BUILD/Module.symver" ] || sd_fail "no prepared kernel at $KERNEL_BUILD"

[ -d "$SCRIPT_DIR/kexec-mod/kernel" ] || sd_fail "kexec submodule not present"

MAKE_ARGS=(KERNEL_BUILD="$KERNEL_BUILD" ARCH="$BOARD_ARCH" KERNEL_COMPAT="$KERNEL_COMPAT")
[ -n "${KERNEL_VERSION:-}" ] && MAKE_ARGS+=(KERNEL_VERSION="$KERNEL_VERSION")

sd_log "building modded kexec for board=$BOARD arch=$BOARD_ARCH compat=$KERNEL_COMPAT"
make -C "$SCRIPT_DIR/kexec-mod/kernel" "${MAKE_ARGS[@]}"

KO_SRC=$(find "$SCRIPT_DIR/kexec-mod/kernel" -maxdepth 1 -name 'kexec_mod.ko')
[ -n "$KO_SRC" ] || sd_fail "build finished, but modded kexec not found"

OUT_DIR="$SCRIPT_DIR/out/$BOARD"
mkdir -p "$OUT_DIR"
cp "$KO_SRC" "$OUT_DIR/kexec_mod.ko"
sd_ok "build $OUT_DIR/kexec_mod.ko"