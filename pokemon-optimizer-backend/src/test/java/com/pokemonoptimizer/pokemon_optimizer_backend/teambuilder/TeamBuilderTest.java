package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

public class TeamBuilderTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();
    // narrower than the real search so the suite stays fast; same stages
    private static final TeamBuilder.Settings SMALL = new TeamBuilder.Settings(6, 6, 3, 40, 80, 2, 7);
    private static final TeamBuilder BUILDER = new TeamBuilder(SPECIES, MOVES, new MetaLookup(SPECIES, MOVES), SMALL);

    private static CaughtPokemonRequest pokemon(String id, int atk, int def, int sta, double level, String fast, String... charged)
    {
        return new CaughtPokemonRequest(id, atk, def, sta, level, fast, List.of(charged), false);
    }

    // three Great League staples and three Pokémon that can't win anything
    private static final List<CaughtPokemonRequest> BOX = List.of(
        pokemon("magikarp", 15, 15, 15, 20, "SPLASH", "STRUGGLE"),
        pokemon("medicham", 0, 15, 15, 20, "COUNTER", "ICE_PUNCH", "DYNAMIC_PUNCH"),
        pokemon("caterpie", 15, 15, 15, 20, "BUG_BITE", "STRUGGLE"),
        pokemon("registeel", 0, 14, 15, 20, "LOCK_ON", "FOCUS_BLAST", "FLASH_CANNON"),
        pokemon("pidgey", 15, 15, 15, 20, "TACKLE", "AIR_CUTTER"),
        pokemon("azumarill", 0, 14, 15, 20, "BUBBLE", "ICE_BEAM", "PLAY_ROUGH"),
        pokemon("medicham", 15, 0, 0, 20, "PSYCHO_CUT", "PSYCHIC"));   // a worse second Medicham

    private static Set<String> species(TeamResult team)
    {
        return team.members().stream().map(TeamMember::speciesId).collect(Collectors.toSet());
    }

    @Test
    public void bestTeamIsTheStrongTrio()
    {
        TeamBuilderResult result = BUILDER.build(League.GREAT, BOX);

        assertEquals(Set.of("medicham", "registeel", "azumarill"), species(result.teams().get(0)));
    }

    @Test
    public void teamsNeverRepeatASpecies()
    {   // GO Battle League bans two of the same species; the stronger Medicham (index 1) is the one used
        TeamBuilderResult result = BUILDER.build(League.GREAT, BOX);

        for (TeamResult team : result.teams())
        {
            assertEquals(3, species(team).size());
        }
        TeamMember medicham = result.teams().get(0).members().stream().filter(m -> m.speciesId().equals("medicham")).findFirst().orElseThrow();
        assertEquals(1, medicham.index());
    }

    @Test
    public void membersAreBuiltForTheLeague()
    {   // 0/15/15 Medicham reaches level 50 at 1439 CP
        TeamMember medicham = BUILDER.build(League.GREAT, BOX).teams().get(0).members().stream()
            .filter(m -> m.speciesId().equals("medicham")).findFirst().orElseThrow();

        assertEquals(50.0, medicham.level());
        assertEquals(1439, medicham.cp());
    }

    @Test
    public void winOddsComeWithTheirInterval()
    {
        WinRate odds = BUILDER.build(League.GREAT, BOX).teams().get(0).winRate();

        assertEquals(80, odds.battles());   // estimated on the fresh sample, not the selection one
        assertTrue(odds.low() <= odds.rate() && odds.rate() <= odds.high(), odds.toString());
        assertTrue(odds.rate() > 0, odds.toString());
    }

    @Test
    public void sameBoxGivesTheSameAnswer()
    {   // sampling is seeded: a player re-running the builder must not see the ranking shuffle
        assertEquals(BUILDER.build(League.GREAT, BOX), BUILDER.build(League.GREAT, BOX));
    }

    @Test
    public void pokemonThatCantPlayAreSkippedWithAReason()
    {
        List<CaughtPokemonRequest> box = List.of(
            pokemon("registeel", 10, 10, 10, 40, "LOCK_ON", "FOCUS_BLAST"),     // 2321 CP: over the Cap
            pokemon("venusaur_mega", 15, 15, 15, 20, "VINE_WHIP", "FRENZY_PLANT"), // Megas can't battle in GBL
            pokemon("medicham", 0, 15, 15, 20, "MUD_SHOT", "ICE_PUNCH"),         // not in its learnset
            pokemon("obstagoon", 0, 15, 15, 20, "COUNTER", "OBSTRUCT"));         // buffs both sides: not simulated yet

        TeamBuilderResult result = BUILDER.build(League.GREAT, box);

        assertEquals(List.of(
            new SkippedPokemon(0, "Over 1500 CP"),
            new SkippedPokemon(1, "Megas can't battle in GO Battle League"),
            new SkippedPokemon(2, "Can't know that moveset"),
            new SkippedPokemon(3, "Obstruct isn't simulated yet")), result.skipped());
        assertEquals(0, result.eligible());
        assertEquals(List.of(), result.teams());
    }
}
