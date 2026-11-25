#!/usr/bin/env bash
# Install and open two sample identities on the specified emulator only.
set -euo pipefail
serial="${1:-emulator-5554}"
[[ "$serial" == emulator-* ]] || { echo "Choose an Android emulator serial." >&2; exit 1; }
lab_dir="${CHAT_LAB_DIR:-${TMPDIR:-/tmp}/unspoken-event-chat-lab}"
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
pkg=com.example.unspokenqueues.chatlab
"$adb" -s "$serial" reverse tcp:18764 tcp:18764
"$adb" -s "$serial" shell run-as "$pkg" mkdir -p no_backup
"$adb" -s "$serial" shell -T run-as "$pkg" sh -c "'cat > no_backup/event-chat-lab-tokens'" < "$lab_dir/tokens"
"$adb" -s "$serial" shell am start -n "$pkg/com.example.unspokenqueues.debug.ChatLabActivity"
