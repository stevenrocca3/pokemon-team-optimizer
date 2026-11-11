import type { SpeciesOption, RankRequest, RankedIvResult, SpeciesDetail, Move, CaughtPokemon, CaughtPokemonRequest, BreakpointReport, CupSummary, ImportPreview, IvComparisonResult, OwnedSpecies, SiteStats, AnalysisStatus, AnalysisUpload, AnalysisResult, TeamBuilderLeague, TeamBuilderRequest, TeamBuilderResult, AnalyticsSummary, MetaPick, MetaPresetRequest} from './types'
import { apiFetch } from './lib/http'

export { NotLoggedInError } from './lib/http'

// production serves the app and the API from one origin; in development (.env.development) login starts on Spring's port
export const LOGIN_URL: string = import.meta.env.VITE_LOGIN_URL ?? '/oauth2/authorization/google'

// apiFetch throws a player-readable Error for anything but a 2xx, and NotLoggedInError for a 401

export async function getSpecies(): Promise<SpeciesOption[]>
{
    const response = await apiFetch('/api/species');
    return await response.json();
}

export async function getRank(request: RankRequest): Promise<RankedIvResult[]>
{
    const response = await apiFetch('/api/rank',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify(request)
    });
    return await response.json();
}

export async function getSpeciesDetail(id: string): Promise<SpeciesDetail>
{
    const response = await apiFetch(`/api/species/${encodeURIComponent(id)}`);
    return await response.json();
}

export async function getMoves(): Promise<Move[]>
{
    const response = await apiFetch('/api/moves');
    return await response.json();
}

export async function getCollection(): Promise<CaughtPokemon[]>
{
    const response = await apiFetch('/api/collection');
    return await response.json();
}

export async function addToCollection(request: CaughtPokemonRequest): Promise<CaughtPokemon>
{
    const response = await apiFetch('/api/collection',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify(request)
    });
    return await response.json();
}

export async function removeFromCollection(id : number): Promise<void>
{
    await apiFetch(`/api/collection/${id}`, { method: 'DELETE', headers: csrfHeader() });
}

/** The species the logged-in Trainer ticked on the Collection checklist. */
export async function getChecklist(): Promise<OwnedSpecies[]>
{
    const response = await apiFetch('/api/checklist');
    return await response.json();
}

/** Replaces the whole checklist; returns what was saved (species order, each once). */
export async function saveChecklist(species: OwnedSpecies[]): Promise<OwnedSpecies[]>
{
    const response = await apiFetch('/api/checklist',
    {
        method: 'PUT',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify({ species })
    });
    return await response.json();
}

/** The League's meta, the checklist's default list. */
/** The checklist's quick-select grid (#116): PvPoke's top 150 (Great, Ultra) or 50 (Master), best first. */
export async function getTopMeta(league: TeamBuilderLeague): Promise<MetaPick[]>
{
    const response = await apiFetch(`/api/meta/${league}/top`);
    return await response.json();
}

export async function getMeta(league: TeamBuilderLeague): Promise<OwnedSpecies[]>
{
    const response = await apiFetch(`/api/meta/${league}`);
    return await response.json();
}

/** The Cups running now, from PvPoke's data (refreshed nightly on the server). */
export async function getCups(): Promise<CupSummary[]>
{
    const response = await apiFetch('/api/cups');
    return await response.json();
}

/** A Poké Genie or Calcy IV export; the server tells them apart. */
export async function previewImport(file: File): Promise<ImportPreview>
{
    const body = new FormData();
    body.append('file', file);

    // no Content-Type: the browser sets multipart/form-data with its boundary
    const response = await apiFetch('/api/import/preview', { method: 'POST', headers: csrfHeader(), body, timeoutMs: 60_000 });
    return await response.json();
}

/** "Add to Box" on a Team Builder suggestion: the server saves the meta build. added is false when it was already there. */
export async function addMetaPreset(request: MetaPresetRequest): Promise<{ pokemon: CaughtPokemon; added: boolean }>
{
    const response = await apiFetch('/api/collection/meta-preset',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify(request)
    });
    return { pokemon: await response.json(), added: response.status === 201 };
}

export async function addAllToCollection(pokemon: CaughtPokemonRequest[]): Promise<CaughtPokemon[]>
{
    const response = await apiFetch('/api/collection/batch',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify({ pokemon }),
        timeoutMs: 60_000,
    });
    return await response.json();
}

export async function buildTeams(request: TeamBuilderRequest, signal?: AbortSignal): Promise<TeamBuilderResult>
{
    // a search can wait up to 30 s for a free slot on the server, then take a while on a small machine
    const response = await apiFetch('/api/team-builder',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify(request),
        signal,
        timeoutMs: 120_000,
    });
    return await response.json();
}

/** Battles each Pokémon (2 to 6) against the League's meta, built to its best level under the cap. */
export async function compareCopies(league: TeamBuilderLeague, pokemon: CaughtPokemonRequest[], signal?: AbortSignal): Promise<IvComparisonResult>
{
    const response = await apiFetch('/api/compare',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify({ league, pokemon }),
        signal,
    });
    return await response.json();
}

/** Damage breakpoints and bulkpoints for one Pokémon against the League's meta. */
export async function getBreakpoints(league: TeamBuilderLeague, pokemon: CaughtPokemonRequest, signal?: AbortSignal): Promise<BreakpointReport>
{
    const response = await apiFetch('/api/breakpoints',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify({ league, pokemon }),
        signal,
    });
    return await response.json();
}

/** Anonymous: only the message and the page it was sent from are kept. */
export async function sendFeedback(message: string, page: string): Promise<void>
{
    await apiFetch('/api/feedback',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify({ message, page })
    });
}

export async function sendRating(stars: number, page: string): Promise<void>
{
    await apiFetch('/api/ratings',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify({ stars, page })
    });
}

/** The logged-in Trainer's display name, straight from their Google login (the server doesn't store it). */
export async function getMe(): Promise<{ name: string | null }>
{
    const response = await apiFetch('/api/me');
    return await response.json();
}

export async function logout(): Promise<void>
{
    await apiFetch('/api/logout', { method: 'POST', headers: csrfHeader() });
}

/** Deletes the Trainer and their whole Collection on the server, then ends the session. Can't be undone. */
export async function deleteAccount(): Promise<void>
{
    await apiFetch('/api/me', { method: 'DELETE', headers: csrfHeader() });
}

function csrfHeader(): Record<string, string> {
    const token = document.cookie.match(/XSRF-TOKEN=([^;]+)/)?.[1]
    return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}
}

export async function getSiteStats(): Promise<SiteStats>
{
    const response = await apiFetch('/api/stats');
    return await response.json();
}

// Battle analysis: the Python service (Caddy routes /api/v1/analyze* to it). Videos can be large and phones upload
// slowly, so the upload gets ten minutes.
const ANALYZE = '/api/v1/analyze'

export async function uploadBattleVideo(file: File): Promise<AnalysisUpload>
{
    const body = new FormData();
    body.append('file', file);
    const response = await apiFetch(ANALYZE, { method: 'POST', body, timeoutMs: 600_000 });
    return await response.json();
}

/**
 * The job's status, or null when the server doesn't know it (results are kept for a day). Used to check on a job
 * whose event stream dropped, so any other failure just throws and the stream's own retry carries on.
 */
export async function findAnalysisStatus(jobId: string): Promise<AnalysisStatus | null>
{
    const response = await fetch(`${ANALYZE}/${encodeURIComponent(jobId)}`);
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`analysis status ${response.status}`);
    return await response.json();
}

export async function getAnalysisResult(jobId: string): Promise<AnalysisResult>
{
    const response = await apiFetch(`${ANALYZE}/${encodeURIComponent(jobId)}/result`);
    return await response.json();
}

/** Saves a finished analysis to battle history (#120): the account's when logged in, else under the guest token. */
export async function recordBattle(jobId: string, guestToken: string | null): Promise<void>
{
    await apiFetch('/api/v1/battles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...csrfHeader(), ...(guestToken ? { 'X-Guest-Token': guestToken } : {}) },
        body: JSON.stringify({ jobId }),
    });
}

/** After logging in: this browser's guest battles move to the account. How many moved. */
export async function claimGuestBattles(guestToken: string): Promise<number>
{
    const response = await apiFetch('/api/v1/battles/claim', {
        method: 'POST',
        headers: { ...csrfHeader(), 'X-Guest-Token': guestToken },
    });
    return (await response.json()).claimed;
}

/** Server-Sent Events: an `status` event with an AnalysisStatus on every change, until complete or failed. */
export function analysisEventsUrl(jobId: string): string
{
    return `${ANALYZE}/${encodeURIComponent(jobId)}/events`;
}

/** The player's battle analytics, or just the guest flag when logged out (#121). */
export async function getAnalyticsSummary(): Promise<AnalyticsSummary>
{
    const response = await apiFetch('/api/v1/analytics/summary');
    return await response.json();
}

/**
 * A battle from the logged-in player's saved history, once the analysis service has forgotten it (after a day).
 * Null when it isn't theirs, or they're logged out.
 */
export async function getStoredBattle(matchId: string): Promise<AnalysisResult | null>
{
    const response = await fetch(`/api/v1/analytics/battles/${encodeURIComponent(matchId)}`);
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`stored battle ${response.status}`);
    return await response.json();
}
