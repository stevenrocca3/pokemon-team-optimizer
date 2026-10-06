import type {SpeciesOption, RankRequest, RankedIvResult} from './types'

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

