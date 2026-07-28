#!/usr/bin/env bash
#sourced

_SD_C_RESET="\033[0m"
_SD_C_RED="\033[1;31m"
_SD_C_GREEN="\033[0;32m"
_SD_C_YELLOW="\033[0;33m"
_SD_C_BLUE="\033[1;34m"

SD_LOOPDEV=""
SD_MOUNTS=()

sd_log() {
    printf "${_SD_C_BLUE}[shimdroid]${_SD_C_RESET} %b\n" "$*";
}

sd_ok() {
    printf "${_SD_C_GREEN}[ok]${_SD_C_RESET} %b\n" "$*";
}

sd_warn() {
    printf "${_SD_C_YELLOW}[warn]${_SD_C_RESET} %b\n" "$*" >&2;
}

sd_err() {
    printf "${_SD_C_RED}[error]${_SD_C_RESET} %b\n" "$*" >&2;
}

sd_debug() {
    [ "${SD_DEBUG:-0}" = "1" ] && printf "${_SD_C_YELLOW}[debug]${_SD_C_RESET} %b\n" "$*" >&2 || :;
}

sd_fail() {
    sd_err "$*"
    exit 1
}

sd_quiet_sgdisk () {
    if [ "${SD_DEBUG:-0}" = "1" ]; then
        "$@"
    else
        "$@" >/dev/null 2>&1
    fi
}

sd_require_root() {
    [ "$EUID" -eq 0 ] || sd_fail "This must be ran as root."
}

sd_require_tools() {
    local missing=()
    local tool 
    for tool in "$@"; do
        command -v "$tool" >/dev/null 2>&1 || missing+=("$tool")
    done
    if [ "${#missing[@]}" -gt 0 ]; then
        sd_fail "Missing required tools: ${missing[*]}"
    fi
}

sd_parse_size() {
    local input="$1"
    local num unit
    if [[ "$input" =~ ^([0-9]+)([KkMmGg]?)$ ]]; then
        num="${BASH_REMATCH[1]}"
        unit="${BASH_REMATCH[2],,}"
        case "$unit" in
            k) echo $((num * 1024));;
            m) echo $((num * 1024 * 1024)) ;;
            g) echo $((num * 1024 * 1024 * 1024)) ;;
            "") echo "$num" ;;
        esac
        return 0
    fi
    if command -v numfmt >/dev/null 2>&1; then
        numfmt --from=iec "$input" 2>/dev/null && return 0
    fi
    sd_fail "Could not parse size $input"
}

sd_format_size() {
    if command -v numfmt >/dev/null 2>&1; then
        numfmt --to=iec-i --suffix=B  "$@"
    else
        echo "${1} bytes"
    fi
}

sd_loop_attach() {
    local image="$1"
    SD_LOOPDEV=$(losetup -f)
    losetup -P "$SD_LOOPDEV" "$image"
    sd_debug "attached $image to $SD_LOOPDEV"
    echo "$SD_LOOPDEV"
}

sd_loop_detach() {
    [ -n "$SD_LOOPDEV" ] || return 0
    losetup -d "$SD_LOOPDEV" 2>/dev/null || sd_warn "failed to detach $SD_LOOPDEV"
    SD_LOOPDEV=""
}

sd_mount() {
    local device="$1"
    local mountpoint="${2:-}"
    local opts="${3:-}"
    [ -n "$mountpoint" ] || mountpoint=$(mktemp -d)
    if [ -n "$opts" ]; then
        mount -o "$opts" "$device" "$mountpoint"
    else 
        mount "$device" "$mountpoint"
    fi
    SD_MOUNTS+=("$mountpoint")
    echo "$mountpoint"
}

sd_unmount_all() {
    local i mp 
    for ((i=${#SD_MOUNTS[@]} - 1; i >= 0; i--)); do
        mp="${SD_MOUNTS[$i]}"
        if mountpoint -q "$mp" 2>/dev/null; then
            umount "$mp" 2>/dev/null || sd_warn "failed to unmount $mp"
        fi
        rmdir "$mp" 2>/dev/null || :
    done
    SD_MOUNTS=()
}

sd_cleanup() {
    sd_unmount_all
    sd_loop_detach
}

sd_arch() {
    local arch 
    arch="$(uname -m)"
    case "$arch" in
        x86_64 | x86-64) echo "x86_64" ;;
        aarch64 | armv8*) echo "aarch64" ;;
        *) sd_fail "unsupported host architecture: $arch" ;;
    esac
}

sd_resolve_gpt_tools() {
    local lib_dir="$1"
    local arch
    arch=$(sd_arch)
    if command -v cgpt >/dev/null 2>&1; then
        SD_CGPT="cgpt"
    else
        SD_CGPT="${lib_dir}/bin/${arch}/cgpt"
        [ -x "$SD_CGPT" ] || sd_fail "no system cgpt found"
    fi
    if command -v sfdisk >/dev/null 2>&1; then 
        SD_SFDISK="sfdisk"
    else
        SD_SFDISK="${lib_dir}/bin/${arch}/sfdisk"
        [ -x "$SD_SFDISK" ] || sd_fail "no system sfdisk found"
    fi
    sd_debug "using cgpt $SD_CGPT"
    sd_debug "using sfdisk $SD_SFDISK"
}

sd_gpt_sector_size() {
    local device="$1"
    "$SD_SFDISK" -l "$device" 2>/dev/null | awk '/^Sector size/ {print $4; exit}'
}

sd_gpt_last_data_sector() {
    local device="$1"
    "$SD_SFDISK" -l -o end "$device" 2>/dev/null | awk '/^[[:space:]]*[0-9]/ {print $1}' | sort -n | tail -1
}

sd_gpt_backup_table_sector() {
    local device="$1"
    "$SD_CGPT" show "$device" | awk '/Sec GPT table$/ {print $1}'
}

sd_gpt_used_partitions() {
    local device="$1"
    "$SD_CGPT" show -q "$device" | awk '{print $3}'
}

sd_gpt_add_partition() {
    local image="$1" device="$2" part_num="$3" size_bytes="$4" label="$5"
    shift 5
    local sector_size last_sector backup_sector size_sectors
    sector_size=$(sd_gpt_sector_size "$device")
    last_sector=$(sd_gpt_last_data_sector "$device")
    backup_sector=$(sd_gpt_backup_table_sector "$device")
    size_sectors=$(((size_bytes + sector_size - 1) / sector_size))
    if sd_gpt_would_overflow "$last_sector" "$backup_sector" "$size_sectors"; then
        sd_grow_image_for_partition "$image" "$device" "$last_sector" "$backup_sector" "$size_sectors" "$sector_size"
        last_sector=$(sd_gpt_last_data_sector "$device")
    fi 
    "$SD_CGPT" add "$device" -i "$part_num" -b $((last_sector + 1)) -s "$size_sectors" -l "$label" "$@"
    partx -u -n "$part_num" "$device"
    sd_debug "added partition $part_num ($label, $(sd_format_size "$size_bytes")) at sector $((last_sector + 1))"
}

sd_gpt_would_overflow() {
    local last_sector="$1" backup_sector="$2" size_sectors="$3"
    local remaining=$((backup_sector - last_sector - size_sectors - 1))
    [ "$remaining" -lt 0 ] 
}

sd_grow_image_for_partition() {
    local image="$1" device="$2" last_sector="$3" backup_sector="$4" size_sectors="$5" sector_size="$6"
    if [ -b "$image" ]; then
        sd_fail "image is a block dev with no room left, cannot grow it to fit the new partition"
    fi 
    local shortfall=$((size_sectors + last_sector + 1 - backup_sector))
    local current_sectors
    current_sectors=$("$SD_SFDISK" -l "$device" 2>/dev/null | awk '/ sectors$/ {print $(NF-1); exit}')
    local new_total_sectors=$((current_sectors + shortfall))
    local new_size_bytes=$((new_total_sectors * sector_size))
    truncate -s "$new_size_bytes" "$image"
    losetup -c "$device"
    sd_quiet_sgdisk -e "$device"
    partx -u "$device" 2>/dev/null || :
}

sd_check_is_gpt() {
    local image="$1"
    "$SD_SFDISK" -l "$image" 2>/dev/null | grep -qi '^Disklabel type:[[:space:]]*gpt$'
}

sd_check_writable_file() {
    local f="$1"
    [ -f "$f" ] && [ -r "$f" ] && [ -w "$f" ]
}

sd_load_shflags() {
    if [ -f "${SCRIPT_DIR}/lib/shflags" ]; then
        . "${SCRIPT_DIR}/lib/shflags"
    else
        sd_fail "cannot find lib/shflags"
    fi
}

sd_get_parts_physical_order() {
    local device="$1"
    local table physical
    table=$("$SD_CGPT" show -q "$device")
    physical=$(awk '{print $1}' <<<"$table" | sort -n)
    for p in $physical; do
        grep "^[[:space:]]*${p}[[:space:]]" <<<"$table" | awk '{print $3}'
    done 
}

sd_delete_partitions_except() {
    local image="$1"
    shift
    local keep=("$@")
    local part to_delete=()
    for part in $(sd_gpt_used_partitions "$image"); do 
        local found=0
        for k in "${keep[@]}"; do [ "$part" = "$k" ] && found=1; done 
        [ "$found" -eq 0 ] && to_delete+=("$part")
    done 
    sd_log "deleting partitions: ${to_delete[*]}"
    "$SD_SFDISK" --delete "$image" "${to_delete[@]}" >/dev/null 2>&1
}

sd_squash_partitions() {
    local device="$1"
    local part 
    for part in $(sd_get_parts_physical_order "$device"); do
        sd_debug "squashing ${device}p${part}"
        "$SD_SFDISK" -N "$part" --move-data "$device" >/dev/null 2>&1 <<<"+,-" || :
    done 
}

sd_truncate_image() {
    local image="$1" outsizefile="${2:-}"
    local buffer=35
    local sector_size final_sector end_bytes
    sector_size=$(sd_gpt_sector_size "$image")
    final_sector=$("$SD_SFDISK" -l -o end "$image" 2>/dev/null | awk '/^[[:space:]]*[0-9]/ {print $1}' | sort -n | tail -1)
    end_bytes=$(((final_sector + buffer) * sector_size))
    sd_log "truncating image to $(sd_format_size "$end_bytes")"
    if [ -b "$image" ]; then 
        local ld; ld=$(losetup -f)
        losetup -P "$ld" "$image" --sizelimit "$endbytes"
        sd_quiet_sgdisk sgdisk -e "$ld"
        losetup -d "$ld"
    else 
        truncate -s "$end_bytes" "$image"
        sd_quiet_sgdisk sgdisk -e "$image"
    fi 
    [ -z "$outsizefile" ] || echo "$end_bytes" >"$outsizefile"
}