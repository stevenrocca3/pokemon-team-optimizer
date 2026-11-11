"""
Builds the backend's evolution-costs.json from Pokémon GO's game master (PokeMiners' export), keyed by PvPoke IDs:
each species' second Charged Move unlock cost, and what each of its evolutions takes.

    curl -L -o latest.json https://raw.githubusercontent.com/PokeMiners/game_masters/master/latest/latest.json
    python3 generate.py latest.json ../../pokemon-optimizer-backend/src/main/resources/gamemaster.json OUT.json

PvPoke's gamemaster has the evolution graph but no costs. For each evolution this keeps the Candy, the item (Sinnoh
Stone), the Lure Module, and the other conditions (buddy, time of day, a quest) as words for people to read. It
prints the PvPoke evolution edges it couldn't find a cost for; the backend shows those as "cost unknown".
"""
import json
import sys

ITEMS = {
    "ITEM_SUN_STONE": "Sun Stone",
    "ITEM_KINGS_ROCK": "King's Rock",
    "ITEM_METAL_COAT": "Metal Coat",
    "ITEM_DRAGON_SCALE": "Dragon Scale",
    "ITEM_UP_GRADE": "Up-Grade",
    "ITEM_GEN4_EVOLUTION_STONE": "Sinnoh Stone",
    "ITEM_GEN5_EVOLUTION_STONE": "Unova Stone",
    "ITEM_OTHER_EVOLUTION_STONE_A": "Gimmighoul Coin",
    "ITEM_OTHER_EVOLUTION_STONE_MAPLE_A": "Black Augurite",
    "ITEM_OTHER_EVOLUTION_STONE_MAPLE_B": "Peat Block",
    "ITEM_OTHER_EVOLUTION_STONE_MAPLE_C": "Linking Cord",
    "ITEM_BEANS": "Beans",
}
LURES = {
    "ITEM_TROY_DISK_MOSSY": "Mossy Lure Module",
    "ITEM_TROY_DISK_GLACIAL": "Glacial Lure Module",
    "ITEM_TROY_DISK_MAGNETIC": "Magnetic Lure Module",
    "ITEM_TROY_DISK_RAINY": "Rainy Lure Module",
}
# GO's form suffixes where PvPoke spells them differently
SUFFIXES = {"_normal": "", "_alola": "_alolan", "_paldea": "_paldean", "_galarian": "_galarian", "_hisuian": "_hisuian",
            "_family_of_four": "", "_family_of_three": ""}


def candidates(form, pokemon_id):
    """PvPoke IDs to try for a GO form, most specific first."""
    tried = []
    if form:
        f = form.lower()
        for go, pvpoke in SUFFIXES.items():
            if f.endswith(go):
                f = f[: -len(go)] + pvpoke
        tried.append(f)
    tried.append(pokemon_id.lower())
    return tried


def resolve(form, pokemon_id, known):
    return next((c for c in candidates(form, pokemon_id) if c in known), None)


def conditions(branch):
    notes = []
    if branch.get("mustBeBuddy"):
        km = branch.get("kmBuddyDistanceRequirement")
        notes.append(f"Walk {km:g} km as your buddy" if km else "Make it your buddy")
    if branch.get("onlyDaytime"):
        notes.append("Evolve during the day")
    if branch.get("onlyNighttime"):
        notes.append("Evolve at night")
    if branch.get("noCandyCostViaTrade"):
        notes.append("Free when traded")
    if branch.get("questDisplay") and not branch.get("mustBeBuddy"):
        notes.append("Has an evolution quest")
    return notes


def main(game_master_path, pvpoke_path, out_path):
    with open(pvpoke_path) as f:
        pvpoke = json.load(f)
    known = {p["speciesId"] for p in pvpoke["pokemon"]}
    with open(game_master_path) as f:
        game_master = json.load(f)

    costs = {}
    for template in game_master:
        settings = template["data"].get("pokemonSettings")
        if not settings:
            continue
        source = resolve(settings.get("form"), settings["pokemonId"], known)
        if source is None:
            continue
        species = costs.setdefault(source, {"secondMove": None, "evolutions": {}})
        third = settings.get("thirdMove")
        if species["secondMove"] is None and third and "stardustToUnlock" in third:
            species["secondMove"] = {"stardust": third["stardustToUnlock"], "candy": third.get("candyToUnlock", 0)}
        for branch in settings.get("evolutionBranch") or []:
            if "evolution" not in branch or ("candyCost" not in branch and "evolutionItemRequirement" not in branch):
                continue   # a Mega Evolution or a temporary form, not an evolution
            target = resolve(branch.get("form"), branch["evolution"], known)
            if target is None or target == source:
                continue
            entry = {
                "candy": branch.get("candyCost", 0),   # Gholdengo takes Gimmighoul Coins instead
                "item": ITEMS.get(branch.get("evolutionItemRequirement"), branch.get("evolutionItemRequirement")),
                "itemCount": branch.get("evolutionItemRequirementCost", 1) if branch.get("evolutionItemRequirement") else 0,
                "lure": LURES.get(branch.get("lureItemRequirement"), branch.get("lureItemRequirement")),
                "conditions": conditions(branch),
            }
            species["evolutions"].setdefault(target, entry)   # the first, form-specific template wins

    missing = []
    for p in pvpoke["pokemon"]:
        for child in (p.get("family") or {}).get("evolutions") or []:
            has = costs.get(p["speciesId"], {}).get("evolutions", {})
            if child in known and child not in has and not p["speciesId"].endswith("_shadow"):
                missing.append(f"{p['speciesId']} -> {child}")
    edges = sum(len(v["evolutions"]) for v in costs.values())
    print(f"{edges} evolutions with costs; {len(missing)} PvPoke evolutions without:", file=sys.stderr)
    for m in missing:
        print("  " + m, file=sys.stderr)

    with open(out_path, "w") as f:
        for v in costs.values():
            v["evolutions"] = dict(sorted(v["evolutions"].items()))
        json.dump(dict(sorted(costs.items())), f, indent=1, ensure_ascii=False)
        f.write("\n")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
