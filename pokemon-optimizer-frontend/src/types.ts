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

// One exported row (Poké Genie or Calcy IV) mapped to our IDs. fastMove is null / chargedMoves empty when the app didn't record them.
export interface ImportedPokemon
{
    index : number;          // Genie's Index column, or the row number in a Calcy IV file
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

// POST /api/import/preview: what the file would import; nothing is saved yet
export interface ImportPreview
{
    pokemon : ImportedPokemon[];
    rejected : RejectedRow[];
    learnsets : Record<string, Learnset>;   // only for species that need moves
}

// ---- Team Builder (POST /api/team-builder) ----

export type TeamBuilderLeague = 'GREAT' | 'ULTRA' | 'MASTER'

export interface TeamBuilderRequest
{
    league : TeamBuilderLeague;
    pokemon : CaughtPokemonRequest[];
}

// A sampled win rate with its Wilson 95% interval, all as fractions 0–1
export interface WinRate
{
    rate : number;
    low : number;
    high : number;
    battles : number;
}

// As it would battle: level and CP after building it for the League. index = position in the request.
export interface TeamMember
{
    index : number;
    speciesId : string;
    shadow : boolean;
    level : number;
    cp : number;
    atkIv : number;
    defIv : number;
    staIv : number;
    fastMove : string;
    chargedMoves : string[];
    metaRating : number;    // average 1v1 battle rating vs the meta, 500 = even
}

export interface TeamResult
{
    members : TeamMember[];   // lead first
    winRate : WinRate;
    toughest : string[];      // PvPoke meta IDs, e.g. "annihilape_shadow"
}

export interface SkippedPokemon
{
    index : number;
    reason : string;
}

export interface TeamBuilderResult
{
    league : TeamBuilderLeague;
    metaSize : number;
    metaDate : string;        // yyyy-mm-dd of the PvPoke snapshot
    eligible : number;
    teams : TeamResult[];     // best first; empty when fewer than 3 species can play
    skipped : SkippedPokemon[];
}
