"""What the frame readers saw on one turn, before the reconstructor cleans it into the turn_log."""
from dataclasses import dataclass, field


@dataclass
class SideReading:
    """One side's HUD on one turn; None wherever that value wasn't readable this turn."""
    species: str | None = None
    cp: int | None = None
    hp: float | None = None          # 0..1, the HP bar's fill
    balls: int | None = None         # Poké Balls in the panel: Pokémon not yet fainted
    shields: int | None = None       # 0, 1 or 2


@dataclass
class TurnReading:
    turn: int
    timestamp_ms: int
    user: SideReading | None = None  # top-left panel, bottom HP bar
    opp: SideReading | None = None   # top-right panel, top HP bar
    banner: object | None = None     # a detection.Banner read in the middle of the screen
    flash: bool = False              # the screen whited out (a switch-in)
    shield_prompt: bool = False      # "Use a Protect Shield?" is up
    shielding: bool = False          # the shield bubble is on screen
    energy_fill: list[float | None] = field(default_factory=list)  # each charged move button's fill, 0..1
    charge_moves: list[str | None] = field(default_factory=list)   # the move IDs under the buttons
    switch_wedge: float | None = None  # the bench timer's elapsed share, 0..1, when the wedge shows
    dropped: bool = False            # no frame for this turn
