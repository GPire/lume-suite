#!/bin/sh
# Lume Suite: downloads TV Doctor from the latest GitHub release, checks its SHA-256 against the release's SHA256SUMS,
# unzips it into ~/LumeSuite and starts it in automatic mode. Nothing else is installed on this computer.
# Read it first if you like: https://github.com/GPire/lume-suite/blob/main/docs/install.sh
set -eu
BASE="https://github.com/GPire/lume-suite/releases/latest/download"
DIR="${LUME_DIR:-$HOME/LumeSuite}"
mkdir -p "$DIR"; cd "$DIR"
command -v python3 >/dev/null 2>&1 || { echo "Serve Python 3 (su Mac: xcode-select --install; su Linux: il pacchetto python3)."; exit 1; }
echo "Scarico TV Doctor..."
curl -fsSL -o TV-Doctor.zip "$BASE/TV-Doctor.zip"
curl -fsSL -o SHA256SUMS "$BASE/SHA256SUMS"
want=$(awk '$2=="TV-Doctor.zip"{print $1}' SHA256SUMS)
if command -v shasum >/dev/null 2>&1; then got=$(shasum -a 256 TV-Doctor.zip | awk '{print $1}'); else got=$(sha256sum TV-Doctor.zip | awk '{print $1}'); fi
[ -n "$want" ] && [ "$want" = "$got" ] || { echo "La firma SHA-256 non corrisponde: non lo apro."; rm -f TV-Doctor.zip; exit 1; }
rm -rf tv-doctor.new && mkdir tv-doctor.new && python3 -I -c "import zipfile,sys; zipfile.ZipFile('TV-Doctor.zip').extractall('tv-doctor.new')"
rm -rf tv-doctor && mv tv-doctor.new/tv-doctor tv-doctor && rmdir tv-doctor.new
echo "Pronto in $DIR/tv-doctor"
[ "${LUME_NO_RUN:-}" = 1 ] && exit 0
cd tv-doctor && exec python3 tvdoctor_app.py --auto
