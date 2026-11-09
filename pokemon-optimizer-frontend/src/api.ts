import type {SpeciesOption, RankRequest, RankedIvResult, SpeciesDetail, Move, CaughtPokemon, CaughtPokemonRequest, BreakpointReport, CupSummary, ImportPreview, IvComparisonResult, OwnedSpecies, SiteStats, TeamBuilderLeague, TeamBuilderRequest, TeamBuilderResult} from './types'
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
