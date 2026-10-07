# Eligibility belongs to Cups, not Species; every Form is rankable

Which Species may enter a battle depends on the Cup, not the Species: Mega Evolutions are barred from the Standard Great League Cup but allowed in Mega cups, and Standard Cup rules change from season to season. We therefore keep every Form, Megas included, in the species data and let every League rank it, since Stat Product depends only on the League's CP Cap and is the same in every Cup under that League. Eligibility will be modelled as each Cup's own entry rules once the Meta and Team Builder need it.

## Considered Options

- **Remove Mega and Primal Forms from the species data or `/api/species`.** Rejected: it would hide valid rankings whenever a Mega cup runs, and adding them back would touch the data, both endpoints and the UI.
- **A per-Species "PvP-eligible" flag.** Rejected: one flag can't express rules that differ between Cups and change over time, and it would invite code that checks the Species instead of the Cup.
