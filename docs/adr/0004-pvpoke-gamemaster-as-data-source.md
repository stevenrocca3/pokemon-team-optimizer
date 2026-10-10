# Species and move data come from a snapshot of PvPoke's gamemaster

Caught Pokémon need Movesets chosen from real Learnsets, including Elite Moves, and the battle simulator is checked against PvPoke (ADR 0002). We therefore replace our own species CSV with a snapshot of PvPoke's MIT-licensed `gamemaster.json`, committed to the repo with its license, as the single source of Base Stats, moves and Learnsets, and we adopt PvPoke's `speciesId` (e.g. `goodra_hisuian`) as our Species ID. The loader translates the file into our model: it skips Shadow entries, because Shadow is a property of a Caught Pokémon rather than a Form, and skips Species that are not yet released.

## Considered Options

- **Keep the CSV for Species and use the gamemaster only for moves.** Rejected: two sources of Base Stats could disagree, and every Species would need a hand-written ID mapping between the files.
- **Keep our own ID format (`706-hisuian`).** Rejected: it needs a mapping for every form name and makes comparisons with PvPoke harder; changing IDs is cheap now, before any Caught Pokémon reference them.
- **Download the gamemaster at startup.** Rejected: tests would depend on a file that changes underneath them and the app would need the network to start; updating the snapshot is a deliberate step instead.

## Consequences

Existing IDs, URLs and tests that use `706-hisuian`-style IDs change. The data must be refreshed by hand when the game adds Species or moves.
