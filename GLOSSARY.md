# DexPresso

Helps a player judge how good their caught Pokémon's IVs are for Pokémon GO PvP, and which stat values they should look for.

## Language

### Pokémon and stats

**Species**:
A kind of Pokémon, identified by its Pokédex number and Form (e.g. Galarian Stunfisk is distinct from Stunfisk).
_Avoid_: Pokémon (when you mean the kind rather than an individual)

**Form**:
A variant of a Pokémon with its own Base Stats, such as a regional form (Hisuian, Galarian, Alolan) or a Mega Evolution; each Form is its own Species.
_Avoid_: Variant

**Base Stats**:
The fixed Attack, Defense and Stamina values of a Species.

**IV Spread**:
One combination of Attack, Defense and Stamina IVs, each 0–15; every Species has 4096.
_Avoid_: Combo, IV set, IVs (for the triple)

**IV Floor**:
The minimum value every IV is guaranteed to have, based on how the Pokémon was obtained (e.g. trades, raids, eggs).
_Avoid_: Minimum IVs

**Stamina**:
The base stat and IV that determine a Pokémon's HP.
_Avoid_: HP (when you mean the base stat or IV)

**HP**:
A Pokémon's actual hit points at a given level, derived from Stamina.

**Shadow**:
A Caught Pokémon that deals more and takes more damage in battle than a regular one, while its CP stays the same; Shadow is a property of the individual, not a Form.
_Avoid_: Shadow form

**Purified**:
A former Shadow Pokémon returned to normal battle strength.

**Level**:
How far a Pokémon has been powered up, from 1 to its Max Level in half-level steps; it can only go up.
_Avoid_: Power level

**Best Buddy**:
Buddy status that raises a Pokémon's Max Level by one.

**Max Level**:
The highest level a Pokémon can be powered up to: Trainer Level + 10, capped at 40 for Trainers below level 40 and at 50 otherwise, plus one for a Best Buddy.
_Avoid_: Level cap

**XL Candy**:
The resource required to power a Pokémon above level 40.

**Fast Move**:
A move used every turn it is selected, dealing small damage and building energy.

**Charged Move**:
A move that spends built-up energy for a large effect; a Pokémon knows one or two.

**Moveset**:
A Pokémon's one Fast Move and one or two Charged Moves.

**Learnset**:
The Fast Moves and Charged Moves a Species can know, including its Legacy Moves.
_Avoid_: Move pool, movelist

**Legacy Move**:
A move a Species can no longer learn normally but that some Caught Pokémon still know, e.g. from a past event or an Elite TM.
_Avoid_: Elite move, exclusive move

### Players

**Trainer**:
A person using the app, identified by their login.
_Avoid_: User, account, player (in the glossary sense)

**Visitor**:
A person using the app without logging in; they can see rankings but have no Collection or Teams.
_Avoid_: Guest, anonymous user

**Trainer Level**:
A Trainer's in-game level, which limits how far their Pokémon can be powered up.

**Caught Pokémon**:
One individual Pokémon a Trainer has saved in the app, with its Species, IV Spread, Level, Moveset, Best Buddy status and whether it is Shadow or Purified.
_Avoid_: Pokémon (on its own), entry, instance

**Collection**:
All the Caught Pokémon belonging to one Trainer.
_Avoid_: Box, storage, inventory

### Leagues, Cups and ranking

**League**:
A CP tier of PvP, defined only by its CP Cap: Little (500), Great (1500), Ultra (2500) or Master (none).
_Avoid_: Cap (on its own), format

**CP Cap**:
The highest CP a Pokémon may have to enter a League.

**Cup**:
A PvP format played under exactly one League's CP Cap, with its own entry rules (e.g. allowed types, banned Species, whether Mega Evolutions may enter).
_Avoid_: Format, mode, event

**Standard Cup**:
The everyday Cup for a League (e.g. standard Great League), whose entry rules can change from season to season.
_Avoid_: Open League, default League

**Eligibility**:
Whether a Species may enter a given Cup; it belongs to the Cup, never to the Species alone.
_Avoid_: Legal, banned (on its own), PvP-eligible

**Stat Product**:
Attack × Defense × HP of an IV Spread at the highest Level it can reach under both the League's CP Cap and a Max Level (the Trainer's own, or the highest possible for a Visitor); the standard measure of overall PvP strength.

**Stat Product Rank**:
An IV Spread's position among all spreads of its Species in a League, ordered by Stat Product; equal Stat Products are ordered by Mirror Rank.
_Avoid_: PvP rank, IV rank

**Mirror Rank**:
An IV Spread's position among all spreads of its Species in a League, ordered by Attack; it shows how likely the spread is to win CMP Ties in mirror matches.
_Avoid_: Attack rank, CMP rank

**Meta**:
The set of Species commonly used in a Cup, which the Pokémon is checked against.

**Cup Ranking**:
The Species eligible for a Cup, ordered by how well they perform against its Meta.
_Avoid_: Tier list, PvP rankings, rank (on its own)

**Mirror Match**:
A battle between two Pokémon of the same Species.

### Teams

**Team**:
The three Pokémon a player brings to a 3v3 battle in a Cup, all of which can be used.
_Avoid_: Roster, party

**Role**:
The job a Team member is chosen for: Lead, Safe Swap or Closer.

**Lead**:
The Team member sent out first, chosen to win or trade evenly in the opening matchup.

**Safe Swap**:
The Team member that switches in when the Lead is losing its matchup, chosen to cover the Lead's weaknesses.

**Closer**:
The Team member held back to finish the battle, usually with shields down on both sides.
_Avoid_: Anchor

**Coverage**:
The share of the Meta that at least one member of a Team beats.

**Team Score**:
A Team's overall rating, made up of its Coverage, how well each member fills its Role, and how well it handles the most common Meta Species.

**Pick-6-Show-3**:
The in-person tournament format where a player registers six Pokémon and chooses three per game; not the format this app builds Teams for.
_Avoid_: Team (for the six)

### Battle mechanics

**CMP Tie**:
The situation where both players throw a charged move on the same turn. The Pokémon with the higher Attack stat goes first.
_Avoid_: Damage breakpoint, mirror win

**Damage Breakpoint**:
The Attack value at which your fast move deals one more damage against a specific opponent.
_Avoid_: Breakpoint (on its own), CMP

**Bulkpoint**:
The Defense value at which a specific opponent's fast move deals one less damage to you.
_Avoid_: Bulk breakpoint, defensive breakpoint

**Survival Threshold**:
The combination of HP and Defense that lets your Pokémon survive one more hit (or faint one hit sooner) against a specific opponent's attack sequence.
_Avoid_: Bulk breakpoint, HP breakpoint

**Shield Scenario**:
The number of shields each side starts a battle with, written as yours-theirs (e.g. 1-1, 2-0).

**Matchup**:
The result of one Pokémon battling one opponent in a given Shield Scenario.
_Avoid_: Sim, fight
