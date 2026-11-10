"""
What the analysis modules share about a reconstructed battle: the league, each side's species (with regional forms
worked out from the moves they threw), their likely moves, and helpers over the turn_log.
"""
from dataclasses import dataclass, field
from functools import cached_property

from battle_analysis import gamedata


def resolve_form(name: str, seen_moves: set[str]) -> str:
    """
    The species a HUD name really is. The HUD shows "Weezing" for Galarian Weezing too, so the form that can throw
    every charged move it was seen throwing wins; the plain species when nothing tells them apart. Shadows have the
    same moves and are never preferred (their only difference, damage, isn't visible).
    """
    all_species = gamedata.species()
    forms = [n for n in all_species if (n == name or n.startswith(name + ' (')) and 'Shadow' not in n and 'Mega' not in n]
    if name not in all_species and not forms:
        return name
    fits = [n for n in forms if seen_moves <= set(all_species[n].charged_moves)]
    if name in fits or not fits:
        return name
    return fits[0]


def has_event(entry: dict, action: str) -> bool:
    return action in (entry.get('events') or [entry.get('action_detected')])


@dataclass
class MatchContext:
    log: dict
    turns: list[dict] = field(init=False)

    def __post_init__(self):
        self.turns = self.log['turn_log']

    @cached_property
    def cp_cap(self) -> int:
        cps = [v for v in (self.log.get('cp') or {}).values() if v]
        return gamedata.cp_cap_for(max(cps) if cps else None)

    @cached_property
    def _seen_moves(self) -> dict[tuple[str, str], set[str]]:
        seen: dict[tuple[str, str], set[str]] = {}
        for e in self.turns:
            if e.get('move_id') and e.get('actor') in ('user', 'opp'):
                sp = e['user_active'] if e['actor'] == 'user' else e['opp_active']
                if sp:
                    seen.setdefault((e['actor'], sp), set()).add(e['move_id'])
        return seen

    def species(self, side: str, hud_name: str | None) -> str | None:
        """The resolved species for a HUD name on a side ('user' or 'opp')."""
        if not hud_name:
            return None
        return resolve_form(hud_name, self._seen_moves.get((side, hud_name), set()))

    def charged_moves(self, side: str, hud_name: str) -> list[gamedata.Move]:
        """Moves seen thrown, plus its usual charged moves to make two."""
        moves = gamedata.moves()
        ids = list(self._seen_moves.get((side, hud_name), set()))
        for m in gamedata.moveset_for(self.species(side, hud_name))[1:]:
            if len(ids) >= 2:
                break
            if m not in ids:
                ids.append(m)
        return [moves[i] for i in ids if i in moves]

    def fast_move(self, side: str, hud_name: str) -> gamedata.Move | None:
        ms = gamedata.moveset_for(self.species(side, hud_name))
        return gamedata.moves().get(ms[0]) if ms else None

    def matchup_start(self, i: int) -> int:
        """Index of the first turn of the matchup (same two Pokémon out) that turn i is in."""
        pair = (self.turns[i]['user_active'], self.turns[i]['opp_active'])
        while i > 0 and (self.turns[i - 1]['user_active'], self.turns[i - 1]['opp_active']) == pair:
            i -= 1
        return i

    def throws(self, actor: str | None = None) -> list[int]:
        """Indexes of charged moves thrown (by `actor`, or either side)."""
        out = []
        for i, e in enumerate(self.turns):
            if (actor in (None, 'user') and has_event(e, 'CHARGE_MOVE_THROWN')) or \
                    (actor in (None, 'opp') and has_event(e, 'OPP_CHARGE_MOVE_THROWN')):
                out.append(i)
        return out

    def damage_pct(self, move: gamedata.Move, attacker: str, defender: str) -> float | None:
        """One hit as a share of the defender's max HP at default league IVs (no buffs), None when unknown."""
        dmg = gamedata.damage(move, attacker, defender, self.cp_cap)
        stats = gamedata.league_stats(defender, self.cp_cap)
        if dmg is None or not stats:
            return None
        return dmg / stats.hp

    def stamp(self, i: int) -> str:
        s = self.turns[i]['timestamp_ms'] / 1000
        return f'{int(s // 60)}:{int(s % 60):02d}'
