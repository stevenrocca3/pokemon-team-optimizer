"""
Compares our gamemaster.json snapshot with PvPoke's current one and prints what changed, as Markdown.

    python3 summarize.py OURS LIVE

Only the Pokémon and moves count: they're all SpeciesLookup and MoveLookup read. Cups come in nightly on the
server (CupSync), and PvPoke bumps its timestamp on every rebuild, so neither alone makes an update.

Exit 0: Pokémon or moves changed, summary printed.  Exit 10: nothing we use changed.  Exit 1: LIVE looks wrong.
"""
import json
import sys

NO_CHANGE = 10


def by_id(gamemaster, section, key):
    return {entry[key]: entry for entry in gamemaster[section]}


def changed_fields(old, new):
    return sorted(field for field in old.keys() | new.keys() if old.get(field) != new.get(field))


def section(title, old, new):
    added = sorted(new.keys() - old.keys())
    removed = sorted(old.keys() - new.keys())
    changed = sorted(k for k in old.keys() & new.keys() if old[k] != new[k])
    lines = [f"### {title}: {len(added)} new, {len(changed)} changed, {len(removed)} removed", ""]
    if added:
        lines.append("New: " + ", ".join(f"`{k}`" for k in added))
    for k in changed:
        lines.append(f"- `{k}`: " + ", ".join(changed_fields(old[k], new[k])))
    if removed:
        # a saved Caught Pokémon or a test fixture may still point at these
        lines.append("**Removed** (check saved Pokémon and fixtures): " + ", ".join(f"`{k}`" for k in removed))
    return lines, bool(added or removed or changed)


def main(ours_path, live_path):
    with open(ours_path, encoding="utf-8") as f:
        ours = json.load(f)
    with open(live_path, encoding="utf-8") as f:
        live = json.load(f)
    # a half-written or error file from GitHub must never replace the snapshot
    if len(live.get("pokemon", [])) < 1000 or len(live.get("moves", [])) < 200:
        print(f"{live_path} doesn't look like PvPoke's gamemaster", file=sys.stderr)
        return 1

    pokemon, pokemon_changed = section(
        "Pokémon", by_id(ours, "pokemon", "speciesId"), by_id(live, "pokemon", "speciesId"))
    moves, moves_changed = section("Moves", by_id(ours, "moves", "moveId"), by_id(live, "moves", "moveId"))
    if not (pokemon_changed or moves_changed):
        return NO_CHANGE

    print(f"PvPoke gamemaster {ours.get('timestamp', '?')} → {live.get('timestamp', '?')}")
    print()
    print("\n".join(pokemon + [""] + moves))
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        sys.exit(1)
    sys.exit(main(sys.argv[1], sys.argv[2]))
