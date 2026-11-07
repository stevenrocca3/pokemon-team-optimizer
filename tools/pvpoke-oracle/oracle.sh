#!/bin/sh
# Runs one Matchup through PvPoke's real simulator and prints the result as JSON.
#
# Usage: tools/pvpoke-oracle/oracle.sh '<config json>'
#   {"a": {"id": "medicham", "ivs": [4,14,9], "level": 20.5, "shadow": false,
#          "fast": "COUNTER", "charged": ["DYNAMIC_PUNCH"]},
#    "b": {...},
#    "shields": [0, 1],
#    "opts": {"optimizeMoveTiming": false}}      <- optional, applied to both sides
#
# Output "turns" is PvPoke's count, which is one more than our BattleResult.turns()
# (PvPoke counts one turn past the KO).
set -e

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

if [ ! -d "$HERE/.pvpoke/src/js" ]; then
    echo "Run $HERE/setup.sh first" >&2
    exit 1
fi

node "$HERE/sim.js" "$HERE/.pvpoke/src/js" \
    "$ROOT/pokemon-optimizer-backend/src/main/resources/gamemaster.json" "$1"
