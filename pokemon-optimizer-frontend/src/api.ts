import type {SpeciesOption, RankRequest, RankedIvResult, SpeciesDetail, Move, CaughtPokemon, CaughtPokemonRequest, ImportPreview, TeamBuilderRequest, TeamBuilderResult} from './types'

// production serves the app and the API from one origin; in development (.env.development) login starts on Spring's port
export const LOGIN_URL: string = import.meta.env.VITE_LOGIN_URL ?? '/oauth2/authorization/google'
export class NotLoggedInError extends Error {}

export async function getSpecies(): Promise<SpeciesOption[]>
{
    const url = '/api/species';
    
    const response = await fetch(url);
    if (!response.ok)
    {
        throw new Error(`Response Status:  ${response.status}`);
    }
    return await response.json();
}
export async function getRank(request: RankRequest): Promise<RankedIvResult[]>
{
    const url = '/api/rank';
   
    const response = await fetch(url, 
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify(request)
    }
    );
    if (!response.ok)
    {
        throw new Error(`Response Status: ${response.status}`);
    }
    return await response.json();

}
export async function getSpeciesDetail(id: string): Promise<SpeciesDetail>
{
    const url = `/api/species/${encodeURIComponent(id)}`;

    const response = await fetch(url);
    if (!response.ok)
    {
        throw new Error(`Response Status:  ${response.status}`);
    }
    return await response.json();
}
export async function getMoves(): Promise<Move[]>
{
    const url = '/api/moves';
    
    const response = await fetch(url);
    if (!response.ok)
    {
        throw new Error(`Response Status:  ${response.status}`);
    }
    return await response.json();
}
export async function getCollection(): Promise<CaughtPokemon[]>
{
    const url = '/api/collection';
    
    const response = await fetch(url);
    if (response.status === 401) throw new NotLoggedInError()
    if (!response.ok)
    {
        throw new Error(`Response Status:  ${response.status}`);
    }
    return await response.json();
}

export async function addToCollection(request: CaughtPokemonRequest): Promise<CaughtPokemon>
{
    const url = '/api/collection';
   
    const response = await fetch(url, 
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify(request)
    }
    );
    if (response.status === 401) throw new NotLoggedInError();
    if (!response.ok)
    {
        throw new Error(`Response Status: ${response.status}`);
    }
    return await response.json();

}

export async function removeFromCollection(id : number): Promise<void>
{
    const url = `/api/collection/${id}`;

    const response = await fetch(url, 
    {
        method: 'DELETE',
        headers: csrfHeader(),
    })
    if (!response.ok)
    {
        throw new Error(`Response Status:  ${response.status}`);
    }
}

/** A Poké Genie or Calcy IV export; the server tells them apart. */
export async function previewImport(file: File): Promise<ImportPreview>
{
    const url = '/api/import/preview';
    const body = new FormData();
    body.append('file', file);

    // no Content-Type: the browser sets multipart/form-data with its boundary
    const response = await fetch(url, { method: 'POST', headers: csrfHeader(), body });
    if (!response.ok)
    {
        throw new Error(await problemDetail(response));
    }
    return await response.json();
}

export async function addAllToCollection(pokemon: CaughtPokemonRequest[]): Promise<CaughtPokemon[]>
{
    const url = '/api/collection/batch';

    const response = await fetch(url,
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify({ pokemon })
    });
    if (response.status === 401) throw new NotLoggedInError();
    if (!response.ok)
    {
        throw new Error(await problemDetail(response));
    }
    return await response.json();
}

export async function buildTeams(request: TeamBuilderRequest, signal?: AbortSignal): Promise<TeamBuilderResult>
{
    const url = '/api/team-builder';

    const response = await fetch(url,
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify(request),
        signal,
    });
    if (!response.ok)
    {
        throw new Error(await problemDetail(response));
    }
    return await response.json();
}

/** The backend's ProblemDetail message, falling back to the status code. */
/** Anonymous: only the message and the page it was sent from are kept. */
export async function sendFeedback(message: string, page: string): Promise<void>
{
    const response = await fetch('/api/feedback',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify({ message, page })
    });
    if (!response.ok)
    {
        throw new Error(await problemDetail(response));
    }
}

export async function sendRating(stars: number, page: string): Promise<void>
{
    const response = await fetch('/api/ratings',
    {
        method: 'POST',
        headers: { 'Content-Type' : 'application/json', ...csrfHeader() },
        body: JSON.stringify({ stars, page })
    });
    if (!response.ok)
    {
        throw new Error(await problemDetail(response));
    }
}

async function problemDetail(response: Response): Promise<string>
{
    try
    {
        const body = await response.json();
        if (typeof body.detail === 'string') return body.detail;
    }
    catch
    {
        // not JSON (e.g. a proxy error page)
    }
    return `Response Status: ${response.status}`;
}

function csrfHeader(): Record<string, string> {
    const token = document.cookie.match(/XSRF-TOKEN=([^;]+)/)?.[1]
    return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {}
}
