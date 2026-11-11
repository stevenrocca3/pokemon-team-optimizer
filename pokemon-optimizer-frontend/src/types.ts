export interface SpeciesOption
{
    id : string;
    name : string;
    dex : number;
    shadowEligible : boolean;
}

/** A species ticked on the Collection checklist: owned, exact IVs unknown. */
export interface OwnedSpecies
{
    speciesId : string;
    shadow : boolean;
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
    suggestedMoves : SuggestedMoves | null;   // PvPoke's moveset; null when it doesn't rank the species
}
// PvPoke's recommended moveset for the League a species ranks best in
export interface SuggestedMoves
{
    fastMove : string;
    chargedMoves : string[];
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
    level : number | null;   // null: added without a CP; the Team Builder assumes the best level
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
    level : number | null;   // null: added without a CP; the Team Builder assumes the best level
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
    suggestedMoves : Record<string, SuggestedMoves>;   // where pickers start, for those PvPoke ranks
}

// ---- Team Builder (POST /api/team-builder) ----

export type TeamBuilderLeague = 'GREAT' | 'ULTRA' | 'MASTER'

export interface TeamBuilderRequest
{
    league : TeamBuilderLeague;
    pokemon : CaughtPokemonRequest[];
    species : OwnedSpecies[];   // checklist ticks; result indexes past pokemon point here
    cup? : string | null;       // a live Cup's key ("fantasy-1500"); absent for the open League
}

// GET /api/cups: a live Cup from PvPoke's data, refreshed nightly
export interface CupSummary
{
    key : string;               // "fantasy-1500"
    title : string;
    league : TeamBuilderLeague;
    partySize : number;         // 6 for Play! Pokémon's pick-3-of-6 formats
    rules : string[];           // PvPoke's rule lines, for people to read
    metaSize : number;
    updated : string;           // yyyy-mm-dd of PvPoke's data
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
    assumed : boolean;      // a checklist tick battling with assumed good IVs
    powerUp : PowerUpImpact | null;   // only when its current level is known and below the built one
    cost : PowerUpCost | null;        // current level to built level; null when the current level isn't known
}

export interface MatchupRef
{
    opponent : string;        // PvPoke meta ID
    shields : number;         // each side
}

/** Stardust and Candy for a power-up (Candy XL from level 40). Shadow costs are estimates. */
export interface PowerUpCost
{
    stardust : number;
    candy : number;
    xlCandy : number;
}

/** What powering a Pokémon from its current level to the built one changes, in meta battles won. */
export interface PowerUpImpact
{
    fromLevel : number;
    fromCp : number;
    winsBefore : number;
    winsAfter : number;
    battles : number;
    gained : MatchupRef[];
    lost : MatchupRef[];
}

export interface TeamResult
{
    members : TeamMember[];   // lead first
    winRate : WinRate;
    toughest : string[];      // PvPoke meta IDs, e.g. "annihilape_shadow"
    suggestions : Suggestion[];   // 5 meta swaps when it wins under 50%, best first; empty otherwise
    coverage : TeamCoverage;
    threats : Threat[];           // the 5 meta Pokémon it does worst against, worst first
}

// How a team covers the meta: its best answer's average battle rating per meta Pokémon (500 = even),
// and how many meta Pokémon some member beats
export interface TeamCoverage
{
    rating : number;
    answered : number;
    metaSize : number;
}

export interface Threat
{
    opponent : string;        // PvPoke meta ID
    rating : number;          // the team's best 1v1 rating against it; under 500 loses
    answer : string;          // species ID of the member that rates best against it
}

// Where a suggested swap plays best: shields up (Lead), one each (Safe Swap), none left (Switcher/Closer)
export type SuggestionRole = 'LEAD' | 'SAFE_SWAP' | 'CLOSER'

// A meta Pokémon to swap in, built the way PvPoke battles it (its default IVs and level, its meta moveset)
export interface Suggestion
{
    speciesId : string;
    shadow : boolean;
    role : SuggestionRole;
    replaces : number;            // request index of the member it takes the place of
    replacesSpeciesId : string;
    level : number;
    cp : number;
    atkIv : number;
    defIv : number;
    staIv : number;
    fastMove : string;
    chargedMoves : string[];
    coverageGain : number;        // rise in the team's meta coverage (battle rating points)
    covers : string[];            // PvPoke meta IDs it helps against most
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
    cup : string | null;      // the Cup's key, or null for the open League
}

// POST /api/compare: which of a few Pokémon (copies of one species) is worth building for a League
export interface ComparedPokemon
{
    index : number;           // position in the request
    level : number;           // built for the League
    cp : number;
    wins : number;            // meta battles won at 0, 1 and 2 shields
    battles : number;
    metaRating : number;
    problem : string | null;  // why it can't play there
}

export interface FlippedMatchup
{
    opponent : string;        // PvPoke meta ID, e.g. "annihilape_shadow"
    shields : number;         // each side
    winners : number[];       // request indexes that win it
}

export interface IvComparisonResult
{
    league : TeamBuilderLeague;
    metaSize : number;
    metaDate : string;
    pokemon : ComparedPokemon[];   // best first
    flips : FlippedMatchup[];
}

// POST /api/breakpoints: fast move damage both ways against each meta Pokémon
export interface OpponentBreakpoint
{
    opponent : string;              // PvPoke meta ID
    opponentFastMove : string;
    damageDealt : number;
    attackForMore : number | null;  // Attack stat for +1 damage; null when no IV spread reaches it under the cap
    damageTaken : number;
    defenseForLess : number | null; // Defense stat for 1 less damage taken
}

export interface BreakpointReport
{
    league : TeamBuilderLeague;
    level : number;
    cp : number;
    fastMove : string;
    attack : number;
    defense : number;
    maxAttack : number;             // best any IV spread reaches under the cap
    maxDefense : number;
    opponents : OpponentBreakpoint[];
}

// GET /api/stats: the site's public totals. Floors: guests keep their Pokémon in the browser.
export interface RatingSummary {
    total : number;
    average : number;                       // 0 when nobody has rated yet
    percentByStars : Record<string, number>;
}

export interface SiteStats {
    teamsBuilt : number;
    pokemonSaved : number;                  // exact Pokémon in Collections
    speciesTicked : number;                 // checklist ticks
    trainers : number;                      // accounts; guests never show up here
    ratings : RatingSummary;
}

// Battle analysis (the Python service behind /api/v1/analyze). Field names are the service's own snake_case.
export type AnalysisStage = 'queued' | 'processing_cv' | 'reconstructing' | 'auditing_turns' | 'complete' | 'failed'

export interface AnalysisStatus {
    job_id : string;
    status : AnalysisStage;
    progress : number;                      // 0-100
    message : string;                       // the stage as a status line ("Detecting Pokémon alignments...")
    error : string | null;                  // set when failed, a sentence for the player
    score : number | null;                  // set when complete
    tier : string | null;
    created_at : number;                    // epoch seconds
    updated_at : number;
}

export interface AnalysisUpload {
    job_id : string;
    status : 'queued';
}

export type AnalysisAction = 'FAINT' | 'OPP_FAINT' | 'SWITCH' | 'OPP_SWITCH' | 'FORCED_SWITCH' | 'OPP_FORCED_SWITCH'
    | 'CHARGE_MOVE_THROWN' | 'OPP_CHARGE_MOVE_THROWN' | 'FAST_MOVE_HIT' | 'OPP_FAST_MOVE_HIT' | 'NONE'

export interface TurnLogEntry {
    turn : number;
    timestamp_ms : number;
    user_active : string | null;
    opp_active : string | null;
    user_hp_pct : number | null;
    opp_hp_pct : number | null;
    user_energy : number | null;
    opp_energy : number | null;             // the opponent's energy isn't on screen
    action_detected : AnalysisAction;
    actor : 'user' | 'opponent' | null;
    move_name : string | null;
    move_id : string | null;
    shield_used : boolean;
    opp_shield_used : boolean;
    user_shields : number | null;
    opp_shields : number | null;
    user_remaining : number | null;
    opp_remaining : number | null;
    switch_clock_s : number | null;
    hud_visible : boolean;
    events : AnalysisAction[];
}

export interface TurnLog {
    match_id : string;
    user_team : string[];
    opponent_team : string[];
    cp : Record<string, number>;            // "user:Swampert" -> CP
    turn_log : TurnLogEntry[];
    video? : { fps : number; duration_ms : number; turns_read : number; turns_dropped : number };
}

export type FindingCategory = 'timing' | 'shield' | 'switch' | 'charge'

export interface Finding {
    turn : number;
    timestamp_ms : number;
    category : FindingCategory;
    deduction : number;
    message : string;
    confidence : 'high' | 'low';
}

export interface MatchReport {
    match_id : string;
    score : number;
    tier : { key : 'legend' | 'veteran' | 'ace' | 'critical'; name : string; min : number };
    counts : { timing_errors : number; mismanaged_shields : number; suboptimal_switches : number; suboptimal_charges : number };
    deductions : Record<string, number>;
    findings : Finding[];
    summary : { fatal_flaws : string[]; shield_audit : string; turn_efficiency : string };
    metrics : Record<string, number>;
}

export interface AnalysisResult {
    report : MatchReport;
    turn_log : TurnLog;
}

// GET /api/v1/analytics/summary (#121-#123): a guest gets only the flag; a logged-in player their analytics
export interface BattleRow {
    matchId : string;           // the analysis job id as a UUID (with dashes)
    createdAt : string;
    userTeam : string[];
    oppTeam : string[];
    score : number;
    tier : string;
    outcome : 'WIN' | 'LOSS' | null;
    turnsLost : number | null;
    shieldEfficiencyPct : number | null;
    timingAccuracyPct : number | null;
}

export interface AnalyticsRank {
    locked : boolean;
    battles : number;
    required : number;
    remaining? : number;        // locked only
    score? : number;            // unlocked only: the server never sends a locked score
    tier? : { key : 'legend' | 'veteran' | 'ace' | 'critical'; name : string };
}

export type TrendDirection = 'IMPROVING' | 'STABLE' | 'DECLINING' | 'NOT_ENOUGH_DATA';

export interface AnalyticsTrend {
    direction : TrendDirection;
    velocity : number | null;
    recentAverage : number | null;
    earlierAverage : number | null;
    rolling : { last5 : number | null; last10 : number | null; last25 : number | null };
    focus : 'timing' | 'shield' | 'switch' | 'charge' | null;
    summary : string;
}

export interface TeamRanking {
    team : string[];
    battles : number;
    wins : number;
    losses : number;
    averageScore : number;
    winRatePct : number;
    rankScore : number;
}

export interface PlayerAnalytics {
    access : 'full';
    battles : number;
    history : BattleRow[];
    rank : AnalyticsRank;
    trend : AnalyticsTrend;
    topTeams : TeamRanking[];
}

export interface GuestAnalytics {
    access : 'guest';
    loginUrl : string;
}

export type AnalyticsSummary = PlayerAnalytics | GuestAnalytics;
