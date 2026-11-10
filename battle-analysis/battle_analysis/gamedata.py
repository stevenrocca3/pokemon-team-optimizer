"""
The species and move data (PvPoke's gamemaster, the same snapshot the Java backend uses) and the battle maths both
steps need: type effectiveness, CP multipliers, damage.
"""
import json
import os
import re
import unicodedata
from dataclasses import dataclass
from functools import cache
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RESOURCES = Path(os.environ.get('GAMEDATA_DIR', REPO / 'pokemon-optimizer-backend/src/main/resources'))


def norm(text: str) -> str:
    """Lower case letters and digits only, accents dropped: how OCR'd text is compared with names."""
    text = unicodedata.normalize('NFD', text or '')
    return re.sub(r'[^a-z0-9]', '', ''.join(c for c in text if not unicodedata.combining(c)).lower())


@dataclass(frozen=True)
class Move:
    id: str
    name: str
    type: str
    power: int
    energy: int        # a charged move's cost
    energy_gain: int   # a fast move's gain
    turns: int         # a fast move's duration in turns

    @property
    def is_fast(self) -> bool:
        return self.energy == 0


@dataclass(frozen=True)
class Species:
    id: str
    name: str
    types: tuple[str, ...]
    atk: int
    defense: int
    hp: int
    fast_moves: tuple[str, ...]
    charged_moves: tuple[str, ...]
    default_ivs: dict


@cache
def gamemaster() -> dict:
    return json.loads((RESOURCES / 'gamemaster.json').read_text())


@cache
def moves() -> dict[str, Move]:
    out = {}
    for m in gamemaster()['moves']:
        out[m['moveId']] = Move(m['moveId'], m['name'], m['type'], m.get('power', 0), m.get('energy', 0),
                                m.get('energyGain', 0), m.get('turns', 1) or 1)
    return out


@cache
def species() -> dict[str, Species]:
    """By species name as the gamemaster writes it ("Stunfisk (Galarian)")."""
    out = {}
    for p in gamemaster()['pokemon']:
        if p['speciesName'] in out:
            continue
        b = p['baseStats']
        out[p['speciesName']] = Species(p['speciesId'], p['speciesName'], tuple(t for t in p['types'] if t != 'none'),
                                        b['atk'], b['def'], b['hp'], tuple(p.get('fastMoves', [])),
                                        tuple(p.get('chargedMoves', [])), p.get('defaultIVs', {}))
    return out


@cache
def recommended_movesets() -> dict[str, list[str]]:
    """species ID -> [fast, charged, charged], PvPoke's ranked moveset (see resources/meta/README.md)."""
    path = RESOURCES / 'meta/movesets.json'
    return json.loads(path.read_text()) if path.exists() else {}


def moveset_for(name: str) -> list[str]:
    """The likely [fast, charged, charged] for a species name, from the rankings; its first moves when unranked."""
    sp = species().get(name)
    if not sp:
        return []
    ranked = recommended_movesets().get(sp.id)
    if ranked:
        return ranked
    return [*sp.fast_moves[:1], *sp.charged_moves[:2]]


# --- battle maths, ported from the Java engine (battle/TypeChart.java, DamageCalculator.java) ------------------
SE, RESISTED, IMMUNE = 1.60000002384185791015625, 0.625, 0.390625
_SUPER = {
    'normal': set(), 'fire': {'grass', 'ice', 'bug', 'steel'}, 'water': {'fire', 'ground', 'rock'},
    'electric': {'water', 'flying'}, 'grass': {'water', 'ground', 'rock'}, 'ice': {'grass', 'ground', 'flying', 'dragon'},
    'fighting': {'normal', 'ice', 'rock', 'dark', 'steel'}, 'poison': {'grass', 'fairy'},
    'ground': {'fire', 'electric', 'poison', 'rock', 'steel'}, 'flying': {'grass', 'fighting', 'bug'},
    'psychic': {'fighting', 'poison'}, 'bug': {'grass', 'psychic', 'dark'}, 'rock': {'fire', 'ice', 'flying', 'bug'},
    'ghost': {'psychic', 'ghost'}, 'dragon': {'dragon'}, 'dark': {'psychic', 'ghost'},
    'steel': {'ice', 'rock', 'fairy'}, 'fairy': {'fighting', 'dragon', 'dark'},
}
_RESISTED = {
    'normal': {'rock', 'steel'}, 'fire': {'fire', 'water', 'rock', 'dragon'}, 'water': {'water', 'grass', 'dragon'},
    'electric': {'electric', 'grass', 'dragon'}, 'grass': {'fire', 'grass', 'poison', 'flying', 'bug', 'dragon', 'steel'},
    'ice': {'fire', 'water', 'ice', 'steel'}, 'fighting': {'poison', 'flying', 'psychic', 'bug', 'fairy'},
    'poison': {'poison', 'ground', 'rock', 'ghost'}, 'ground': {'grass', 'bug'}, 'flying': {'electric', 'rock', 'steel'},
    'psychic': {'psychic', 'steel'}, 'bug': {'fire', 'fighting', 'poison', 'flying', 'ghost', 'steel', 'fairy'},
    'rock': {'fighting', 'ground', 'steel'}, 'ghost': {'dark'}, 'dragon': {'steel'}, 'dark': {'fighting', 'dark', 'fairy'},
    'steel': {'fire', 'water', 'electric', 'steel'}, 'fairy': {'fire', 'poison', 'steel'},
}
_IMMUNE = {'normal': {'ghost'}, 'electric': {'ground'}, 'fighting': {'ghost'}, 'poison': {'steel'},
           'ground': {'flying'}, 'psychic': {'dark'}, 'ghost': {'normal'}, 'dragon': {'fairy'}}


def effectiveness(move_type: str, defender_types: tuple[str, ...]) -> float:
    result = 1.0
    for t in defender_types:
        if t in _IMMUNE.get(move_type, ()):
            result *= IMMUNE
        elif t in _RESISTED.get(move_type, ()):
            result *= RESISTED
        elif t in _SUPER.get(move_type, ()):
            result *= SE
    return result


# PvPoke's CP multipliers, index = level * 2 (CpCalculator.java)
_CPM = [0, 0, 0.0939999967813491, 0.135137430784308, 0.166397869586944, 0.192650914456886, 0.215732470154762,
        0.236572655026622, 0.255720049142837, 0.273530381100769, 0.290249884128570, 0.306057381335773,
        0.321087598800659, 0.335445032295077, 0.349212676286697, 0.362457748778790, 0.375235587358474,
        0.387592411085168, 0.399567276239395, 0.411193549517250, 0.422500014305114, 0.432926413410414,
        0.443107545375824, 0.453059953871985, 0.462798386812210, 0.472336077786704, 0.481684952974319,
        0.490855810259008, 0.499858438968658, 0.508701756943992, 0.517393946647644, 0.525942508771329,
        0.534354329109191, 0.542635762230353, 0.550792694091796, 0.558830599438087, 0.566754519939422,
        0.574569148039264, 0.582278907299041, 0.589887911977272, 0.597400009632110, 0.604823657502073,
        0.612157285213470, 0.619404110566050, 0.626567125320434, 0.633649181622743, 0.640652954578399,
        0.647580963301656, 0.654435634613037, 0.661219263506722, 0.667934000492096, 0.674581899290818,
        0.681164920330047, 0.687684905887771, 0.694143652915954, 0.700542893277978, 0.706884205341339,
        0.713169102333341, 0.719399094581604, 0.725575616972598, 0.731700003147125, 0.734741011137376,
        0.737769484519958, 0.740785574597326, 0.743789434432983, 0.746781208702482, 0.749761044979095,
        0.752729105305821, 0.755685508251190, 0.758630366519684, 0.761563837528228, 0.764486065255226,
        0.767397165298461, 0.770297273971590, 0.773186504840850, 0.776064945942412, 0.778932750225067,
        0.781790064808426, 0.784636974334716, 0.787473583646825, 0.790300011634826, 0.792803950958807,
        0.795300006866455, 0.797803921486970, 0.800300002098083, 0.802803892322847, 0.805299997329711,
        0.807803863460723, 0.810299992561340, 0.812803834895026, 0.815299987792968, 0.817803806620319,
        0.820299983024597, 0.822803778631297, 0.825299978256225, 0.827803750922782, 0.830299973487854,
        0.832803753381377, 0.835300028324127, 0.837803755931569, 0.840300023555755, 0.842803729034748,
        0.845300018787384]
BONUS, STAB = 1.2999999523162841796875, 1.2000000476837158203125
SHADOW_ATK, SHADOW_DEF = 1.2, 0.83333331


@dataclass(frozen=True)
class Stats:
    atk: float
    defense: float
    hp: int


def league_stats(name: str, cp_cap: int = 1500) -> Stats | None:
    """A species' stats at PvPoke's default IVs for the league (the usual build), or None for an unknown species."""
    sp = species().get(name)
    if not sp:
        return None
    key = {500: 'cp500', 1500: 'cp1500', 2500: 'cp2500'}.get(cp_cap)
    level, a, d, s = sp.default_ivs.get(key, [50, 15, 15, 15]) if key else [50, 15, 15, 15]
    cpm = _CPM[int(round(level * 2))]
    return Stats((sp.atk + a) * cpm, (sp.defense + d) * cpm, max(10, int((sp.hp + s) * cpm)))


def damage(move: Move, attacker: str, defender: str, cp_cap: int = 1500) -> int | None:
    """Damage of one hit at default IVs (no buffs, unshielded), the Java engine's formula; None if a side is unknown."""
    a, d = league_stats(attacker, cp_cap), league_stats(defender, cp_cap)
    sa, sd = species().get(attacker), species().get(defender)
    if not (a and d and sa and sd):
        return None
    stab = STAB if move.type in sa.types else 1.0
    return int(move.power * stab * (a.atk / d.defense) * effectiveness(move.type, sd.types) * 0.5 * BONUS) + 1


def cp_cap_for(cp: int | None) -> int:
    """The league a Pokémon's CP puts it in."""
    if cp is None or cp <= 1500:
        return 1500
    return 2500 if cp <= 2500 else 10000
