// Builds the battle fixture: random (seeded) matchups between Great League species, each run through
// PvPoke's real simulator. Re-run after a gamemaster update or a PvPoke bump, then review the diff.
//
// Usage: node gen-fixture.js <pvpoke js dir> <gamemaster.json> <out.json> [count] [focus species] [cp]
// With a focus species, every matchup has it on one side (either side, mirrors included): for mechanics only
// that species has, like Cramorant's Gulp Missile. cp is the battle's CP cap (PvPoke's setCP), 10000 when left
// out; it only matters for forms whose stats depend on the league, like Aegislash's Blade form.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const [jsDir, gmPath, outPath, countArg, focus, cpArg] = process.argv.slice(2);
const CP = cpArg ? Number(cpArg) : undefined;
const COUNT = Number(countArg || 400);
const gm = JSON.parse(fs.readFileSync(gmPath, 'utf8'));

const SPECIES = ('medicham azumarill registeel swampert lanturn altaria skarmory bastiodon trevenant galvantula ' +
    'stunfisk_galarian walrein whiscash umbreon sableye dewgong froslass noctowl jumpluff carbink cresselia ' +
    'deoxys_defense mandibuzz dragonair talonflame quagsire sealeo machamp venusaur annihilape clodsire feraligatr ' +
    'gligar lickitung marowak_alolan pelipper ninetales_alolan dunsparce cradily vigoroth nidoqueen melmetal').split(' ');

// Mulberry32, so the same seed always gives the same fixture
let seed = 20261107;
function rand() {
    seed |= 0; seed = seed + 0x6D2B79F5 | 0;
    let t = Math.imul(seed ^ seed >>> 15, 1 | seed);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
}
const pick = (list) => list[Math.floor(rand() * list.length)];

const unsupportedMoves = new Set(gm.moves.filter(m => m.buffTarget == 'both').map(m => m.moveId));

function side(id = pick(SPECIES)) {
    const p = gm.pokemon.find(x => x.speciesId == id);
    const [level, atk, def, hp] = p.defaultIVs.cp1500;
    const charged = p.chargedMoves.filter(m => !unsupportedMoves.has(m));
    const count = rand() < 0.15 ? 1 : 2;
    const chosen = [];
    while (chosen.length < Math.min(count, charged.length)) {
        const m = pick(charged);
        if (!chosen.includes(m)) chosen.push(m);
    }
    return {
        id: p.speciesId, ivs: [atk, def, hp], level,
        shadow: (p.tags || []).includes('shadoweligible') && rand() < 0.2,
        fast: pick(p.fastMoves), charged: chosen
    };
}

const SHIELDS = [[0, 0], [1, 1], [2, 2], [1, 0], [0, 2], [2, 1]];
const configs = [];
for (let i = 0; i < COUNT; i++) {
    if (focus) {
        const other = rand() < 0.1 ? side(focus) : side();
        const mine = side(focus);
        const [a, b] = rand() < 0.5 ? [mine, other] : [other, mine];
        configs.push({ a, b, shields: pick(SHIELDS), cp: CP });
    } else {
        configs.push({ a: side(), b: side(), shields: pick(SHIELDS) });
    }
}

const out = JSON.parse(execFileSync('node', [path.join(__dirname, 'sim.js'), jsDir, gmPath, JSON.stringify(configs)],
    { maxBuffer: 64 * 1024 * 1024 }).toString());

const fixture = configs.map((cfg, i) => ({
    a: cfg.a, b: cfg.b, shields: cfg.shields, ...(CP ? { cp: CP } : {}),
    expected: {
        turns: out[i].turns - 1, // our BattleResult counts the KO turn; PvPoke counts one past it
        hp: out[i].hp, energy: out[i].energy, shields: out[i].shields, buffs: out[i].buffs, ratings: out[i].ratings
    }
}));

fs.writeFileSync(outPath, '[\n' + fixture.map(m => JSON.stringify(m)).join(',\n') + '\n]\n');
console.log(`wrote ${fixture.length} matchups to ${outPath}`);
