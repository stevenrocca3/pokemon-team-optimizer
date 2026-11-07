// Usage: node sim.js <pvpoke js dir> <gamemaster.json> '<json config>'
// config: {a:{id,ivs:[a,d,h],level,shadow,fast,charged:[..]}, b:{...}, shields:[x,y], opts:{optimizeMoveTiming,baitShields}}
const vm=require('vm'); const ctx=require('./load.js');
ctx.__cfg=JSON.parse(process.argv[4]);
const out=vm.runInContext(`(function(){
  var cfg=__cfg; var battle=new Battle(); battle.setCP(10000);
  function make(c,i){
    var p=new Pokemon(c.id,i,battle); p.initialize(false);
    if(c.shadow) p.setShadowType('shadow');
    p.setIV('atk',c.ivs[0]);p.setIV('def',c.ivs[1]);p.setIV('hp',c.ivs[2]);p.setLevel(c.level);
    p.selectMove('fast',c.fast);
    p.chargedMoves=[];
    for(var j=0;j<c.charged.length;j++){ var mv=p.chargedMovePool.find(m=>m.moveId==c.charged[j])||p.extraChargedMovePool&&p.extraChargedMovePool.find(m=>m.moveId==c.charged[j]); if(!mv) throw new Error('move '+c.charged[j]); p.chargedMoves.push(mv); }
    var o=cfg.opts||{}; for(var k in o) p[k]=o[k];
    for(var k in (c.opts||{})) p[k]=c.opts[k];
    return p;
  }
  var a=make(cfg.a,0), b=make(cfg.b,1);
  battle.setNewPokemon(a,0,false); battle.setNewPokemon(b,1,false);
  a.setShields(cfg.shields[0]); b.setShields(cfg.shields[1]);
  a.reset(); b.reset();
  battle.simulate();
  var r=battle.getBattleRatings();
  var tl=battle.getTimeline().filter(e=>e.type&&e.type.indexOf('charged')>-1||e.type=='shield').map(e=>({t:e.turn,actor:e.actor,name:e.name,type:e.type,val:e.values}));
  return {turns:battle.getTurns(),hp:[a.hp,b.hp],maxHp:[a.stats.hp,b.stats.hp],energy:[a.energy,b.energy],shields:[a.shields,b.shields],buffs:[a.statBuffs,b.statBuffs],ratings:r,atk:[a.stats.atk,b.stats.atk],moves:[[a.fastMove.moveId].concat(a.chargedMoves.map(m=>m&&m.moveId)),[b.fastMove.moveId].concat(b.chargedMoves.map(m=>m&&m.moveId))],charged:tl};
})()`,ctx);
console.log(JSON.stringify(out));
