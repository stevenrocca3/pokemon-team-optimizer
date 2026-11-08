// Loads PvPoke's battle code into a vm context with stubbed browser globals.
const fs=require('fs'),vm=require('vm'),path=require('path');
const SRC=process.argv[2], GM=process.argv[3];
const data=JSON.parse(fs.readFileSync(GM,'utf8'));
const noop=()=>{};
const chain=new Proxy(function(){},{get:(t,k)=>k==='length'?0:(k===Symbol.toPrimitive?()=>'':chain),apply:()=>chain});
const jq=function(){return chain};
const pending=[]; jq.ajax=(o)=>{ if(o.url.includes('gamemaster')) pending.push(()=>o.success(data)); };
const ctx={console:{log:noop,warn:console.warn,error:console.error},$:jq,jQuery:jq,host:'localhost',webRoot:'',siteVersion:'',document:{},settings:{gamemaster:'gamemaster'},localStorage:{getItem:()=>null,setItem:()=>{}},sessionStorage:{getItem:()=>null,setItem:()=>{}},Math,JSON,Array,Object,Infinity,parseInt,parseFloat,isNaN,Map,Set};
ctx.window=ctx; vm.createContext(ctx);
for(const f of ['GameMaster.js','pokemon/Pokemon.js','battle/DamageCalculator.js','battle/timeline/TimelineAction.js','battle/timeline/TimelineEvent.js','battle/actions/ActionLogic.js','battle/Battle.js']){
  vm.runInContext(fs.readFileSync(path.join(SRC,f),'utf8'),ctx,{filename:f});
}
vm.runInContext('var gm=GameMaster.getInstance();',ctx); while(pending.length) pending.shift()();
module.exports=ctx;
