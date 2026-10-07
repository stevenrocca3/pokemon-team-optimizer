import type {SpeciesOption, RankRequest, RankedIvResult, SpeciesDetail} from './types'

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
        headers: { 'Content-Type' : 'application/json' },
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

