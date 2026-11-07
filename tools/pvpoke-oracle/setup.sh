#!/bin/sh
# Clones PvPoke's battle code at a pinned commit into .pvpoke/ (gitignored).
# Re-run after bumping PVPOKE_COMMIT to compare against a newer PvPoke.
set -e

PVPOKE_COMMIT=e02bde78d19be9b9099b5a6629c6a8d0835a53be
DIR="$(cd "$(dirname "$0")" && pwd)/.pvpoke"

if [ ! -d "$DIR/.git" ]; then
    git clone -q --filter=blob:none --sparse https://github.com/pvpoke/pvpoke.git "$DIR"
fi

cd "$DIR"
git sparse-checkout set --no-cone '/src/js/battle/' '/src/js/pokemon/' '/src/js/GameMaster.js'
git fetch -q origin "$PVPOKE_COMMIT"
git checkout -q "$PVPOKE_COMMIT"
echo "PvPoke ready at $(git rev-parse --short HEAD)"
