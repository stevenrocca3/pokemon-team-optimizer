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
  Pass `"opts": {"optimizeMoveTiming": false}` to compare mechanics before that AI piece exists.
- `load.js` stubs the browser (jQuery, localStorage) just enough for the battle code to load.
  If a new PvPoke version fails to load, the stubs there are the place to look.
