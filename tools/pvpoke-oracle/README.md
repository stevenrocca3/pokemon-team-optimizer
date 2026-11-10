# PvPoke oracle

Runs PvPoke's own battle simulator (Battle.js, ActionLogic.js, Pokemon.js) in Node against our
`gamemaster.json`, so battle tests can use expected values from the oracle named in ADR 0002
instead of hand calculations.

```sh
tools/pvpoke-oracle/setup.sh        # once: clones PvPoke at the pinned commit into .pvpoke/
tools/pvpoke-oracle/oracle.sh '{"a":{"id":"medicham","ivs":[4,14,9],"level":20.5,"fast":"COUNTER","charged":["DYNAMIC_PUNCH"]},"b":{"id":"registeel","ivs":[10,10,10],"level":25,"fast":"LOCK_ON","charged":[]},"shields":[0,0]}'
```

Things to know when comparing with `Battle.simulate`:

- PvPoke's `turns` is ours + 1.
- PvPoke's AI options default to the website's: `optimizeMoveTiming: true`, `baitShields: 1`.
  Pass `"opts": {"optimizeMoveTiming": false}` to match `Battle.simulate(..., false)`.
- `load.js` stubs the browser (jQuery, localStorage) just enough for the battle code to load.
  If a new PvPoke version fails to load, the stubs there are the place to look.

## Matchup fixture

`PvPokeFixtureTest` replays 3000 seeded random Great League matchups from
`src/test/resources/battle/pvpoke-matchups.json` and checks every result field against PvPoke.
Regenerate it after a gamemaster update or a PvPoke bump, then review the diff:

```sh
node tools/pvpoke-oracle/gen-fixture.js tools/pvpoke-oracle/.pvpoke/src/js \
  pokemon-optimizer-backend/src/main/resources/gamemaster.json \
  pokemon-optimizer-backend/src/test/resources/battle/pvpoke-matchups.json 3000
```

Four more fixtures each put one form-changing species in every matchup (either side, about 10% mirrors), for the
mechanics only it has: `pvpoke-cramorant.json` (400, Gulp Missile), `pvpoke-mimikyu.json` (300, Disguise),
`pvpoke-morpeko.json` (300, Hunger Switch) and `pvpoke-aegislash.json` (400, Stance Change). Pass a focus species
to build one, and a CP cap after it when the species' mechanics depend on the league. Aegislash's does (PvPoke works
out its Blade form level from the cap), so its fixture runs at 1500; the others run at PvPoke's default of 10000:

```sh
node tools/pvpoke-oracle/gen-fixture.js tools/pvpoke-oracle/.pvpoke/src/js \
  pokemon-optimizer-backend/src/main/resources/gamemaster.json \
  pokemon-optimizer-backend/src/test/resources/battle/pvpoke-cramorant.json 400 cramorant

node tools/pvpoke-oracle/gen-fixture.js tools/pvpoke-oracle/.pvpoke/src/js \
  pokemon-optimizer-backend/src/main/resources/gamemaster.json \
  pokemon-optimizer-backend/src/test/resources/battle/pvpoke-aegislash.json 400 aegislash_shield 1500
```

(Mimikyu and Morpeko: `300 mimikyu` and `300 morpeko_full_belly`.)

Not covered (the engine throws or doesn't model them): moves that buff both sides (Obstruct). Form changes on
switching out (Cramorant, Morpeko and Aegislash revert; busted Mimikyu stays busted) are ported, but only 3v3
battles switch and the fixtures are 1v1. Busted Mimikyu getting its -1 Defense back when it switches in is covered
by TeamBattleTest instead.
