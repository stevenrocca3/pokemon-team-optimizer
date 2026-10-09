export interface SpeciesOption
{
    id : string;
    name : string;
    dex : number;
}
export interface RankRequest
{
    //String speciesId, boolean hasCap, int cap, boolean hasBestBuddy
    speciesId : string;
    hasCap : boolean;
    cap : number;
    hasBestBuddy : boolean;
}
export interface IvResult
{
    atkIV : number;
    defIV : number;
    staIV : number;
    level : number;
    effectiveAttack : number;
    statProduct : number;
}
export interface RankedIvResult
{
    // IvResult ivResult, int statProductRank, int mirrorRank
    ivResult : IvResult;
    statProductRank : number;
    mirrorRank : number;
}
export interface SpeciesDetail
{
    //public record SpeciesDetail(String id, String name, String form, int baseAtk, int baseDef, int baseSta){};
    id : string;
    name : string;
    dex : number;
    baseAtk : number;
    baseDef : number;
    baseSta : number;
}