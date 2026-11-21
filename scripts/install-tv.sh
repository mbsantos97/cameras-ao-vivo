#!/usr/bin/env bash
# Instala o Câmeras ao Vivo na TV Box pela rede (sem cabos).
# Uso: scripts/install-tv.sh IP_DA_TVBOX [porta] [código_de_pareamento porta_de_pareamento]
#  - Mi Box S / Mi TV Stick: ative "Depuração USB" nas Opções do desenvolvedor (a porta 5555 abre na rede).
#  - Google TV (TV Box S 2ª geração): também pode usar "Depuração sem fio" com código de pareamento.
set -euo pipefail
IP="${1:?Informe o IP da TV Box}"
PORT="${2:-5555}"
ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Android/Sdk/platform-tools/adb"
if [ $# -ge 4 ]; then
  echo "Pareando com o código $3 na porta $4…"
  "$ADB" pair "$IP:$4" "$3"
fi
echo "Conectando em $IP:$PORT (confirme \"Permitir depuração\" na TV, marcando \"Sempre permitir\")…"
"$ADB" connect "$IP:$PORT"
"$ADB" -s "$IP:$PORT" wait-for-device
ABI="$("$ADB" -s "$IP:$PORT" shell getprop ro.product.cpu.abi | tr -d '\r')"
DIR="$(cd "$(dirname "$0")/.." && pwd)/dist"
APK="$(ls "$DIR"/CamerasAoVivo-v*-"$ABI".apk 2>/dev/null | sort -V | tail -1 || true)"
[ -z "$APK" ] && APK="$(ls "$DIR"/CamerasAoVivo-v*-universal.apk | sort -V | tail -1)"
echo "ABI da TV Box: $ABI → instalando $(basename "$APK")"
"$ADB" -s "$IP:$PORT" install -r "$APK"
"$ADB" -s "$IP:$PORT" shell am start -n io.github.mbsantos97.camerasaovivo/.MainActivity
echo "Pronto."
