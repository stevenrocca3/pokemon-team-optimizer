From PvPoke at commit e02bde78d19be9b9099b5a6629c6a8d0835a53be (the same commit as the oracle in
tools/pvpoke-oracle). MIT-licensed like gamemaster.json; see ../LICENSE.txt. Refresh by hand together with gamemaster.json.

- `{great,ultra,master}.json`: `src/data/groups/` meta groups. The Team Builder battles against these (ADR 0005).
- `movesets.json`: species ID → [fast, charged, charged] from `src/data/rankings/all/overall/rankings-{1500,2500,10000}.json`,
  the moveset of the League where the species scores highest (regular entry preferred over its `_shadow` one).
  Pickers and imports start on these.
