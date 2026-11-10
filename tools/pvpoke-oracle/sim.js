// Usage: node sim.js <pvpoke js dir> <gamemaster.json> '<config json | array of configs>'
// config: {a:{id,ivs:[a,d,h],level,shadow,fast,charged:[..]}, b:{...}, shields:[x,y],
//          opts:{optimizeMoveTiming,baitShields}, cp, log:true}  (cp: the CP cap, 10000 when left out)
// Prints one JSON result per config (an array when given an array).
const vm = require('vm');
const ctx = require('./load.js');

const input = JSON.parse(process.argv[4]);
const configs = Array.isArray(input) ? input : [input];

const debugLines = [];
ctx.console.log = (line) => debugLines.push(String(line));

const results = configs.map((cfg) => {
    ctx.__cfg = cfg;
    debugLines.length = 0;
    const out = vm.runInContext(`(function(){
      var cfg = __cfg;
      var battle = new Battle();
      battle.setCP(cfg.cp || 10000);
      battle.setDebugMode(!!cfg.log);

      function make(c, i){
        var p = new Pokemon(c.id, i, battle);
        p.initialize(false);
        if(c.shadow) p.setShadowType('shadow');
        p.setIV('atk', c.ivs[0]); p.setIV('def', c.ivs[1]); p.setIV('hp', c.ivs[2]);
        p.setLevel(c.level);
        p.selectMove('fast', c.fast);
        p.chargedMoves = [];
        for(var j = 0; j < c.charged.length; j++){
          var mv = p.chargedMovePool.find(m => m.moveId == c.charged[j])
            || (p.extraChargedMovePool && p.extraChargedMovePool.find(m => m.moveId == c.charged[j]));
          if(! mv) throw new Error('move ' + c.charged[j] + ' not in ' + c.id + ' pool');
          p.chargedMoves.push(mv);
        }
        var o = cfg.opts || {}; for(var k in o) p[k] = o[k];
        for(var k in (c.opts || {})) p[k] = c.opts[k];
        return p;
      }

      var a = make(cfg.a, 0), b = make(cfg.b, 1);
      battle.setNewPokemon(a, 0, false); battle.setNewPokemon(b, 1, false);
      a.setShields(cfg.shields[0]); b.setShields(cfg.shields[1]);
      a.reset(); b.reset();
      battle.simulate();
      if(cfg.log) battle.debug();

      var charged = battle.getTimeline()
        .filter(e => (e.type && e.type.indexOf('charged') > -1) || e.type == 'shield')
        .map(e => ({turn: e.turn, actor: e.actor, name: e.name, values: e.values}));

      return {
        turns: battle.getTurns(),
        hp: [a.hp, b.hp], maxHp: [a.stats.hp, b.stats.hp],
        energy: [a.energy, b.energy], shields: [a.shields, b.shields],
        buffs: [a.statBuffs, b.statBuffs], ratings: battle.getBattleRatings(),
        charged: charged
      };
    })()`, ctx);
    if (cfg.log) out.log = debugLines.slice();
    return out;
});

console.error = console.error; // keep stderr for load errors
process.stdout.write(JSON.stringify(Array.isArray(input) ? results : results[0]) + '\n');
