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
export interface Learnset
{
    fastMoves : string[];
    chargedMoves : string[];
    eliteMoves : string[];
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
    learnset : Learnset;
    shadowEligible : boolean;
}
export interface Move
{
    moveId : string;
    name : string;
}
// public CaughtPokemon(String speciesId, int atkIv, int defIv, int staIv, double level) this.speciesId = speciesId; this.atkIv = atkIv;
// this.defIv = defIv; this.staIv = staIv; this.level = level;

export interface CaughtPokemon
{
    id : number; //fine bc IDs are sequential so precision loss won't happen for this scale
    speciesId : string;
    atkIv : number;
    defIv : number;
    staIv : number;
    level : number;
    fastMove : string;
    chargedMoves : string[];
    shadow : boolean;
}

export interface CaughtPokemonRequest
{
    // public record CaughtPokemonRequest(@NotBlank String speciesId, @Min(0) @Max(15) int atkIv, @Min(0) @Max(15) int defIv, 
    //@Min(0) @Max(15) int staIv, @DecimalMin("1") @DecimalMax("51") double level)
    speciesId : string;
    atkIv : number;
    defIv : number;
    staIv : number;
    level : number;
    fastMove : string;
    chargedMoves : string[];
    shadow : boolean;
}

// One Poké Genie row mapped to our IDs. fastMove is null / chargedMoves empty when Genie didn't record them.
export interface ImportedPokemon
{
    index : number;          // Genie's Index column
    speciesId : string;
    atkIv : number;
    defIv : number;
    staIv : number;
    level : number;
    shadow : boolean;
    fastMove : string | null;
    chargedMoves : string[];
    needsMoves : boolean;
}

export interface RejectedRow
{
    index : number;
    reason : string;
}

// POST /api/import/poke-genie: what the file would import; nothing is saved yet
export interface ImportPreview
{
    pokemon : ImportedPokemon[];
    rejected : RejectedRow[];
    learnsets : Record<string, Learnset>;   // only for species that need moves
}
