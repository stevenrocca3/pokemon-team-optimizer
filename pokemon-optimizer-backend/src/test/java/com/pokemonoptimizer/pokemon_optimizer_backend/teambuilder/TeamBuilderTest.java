package com.pokemonoptimizer.pokemon_optimizer_backend.teambuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.calculator.PowerUpCost;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.Cup;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupFilter;
import com.pokemonoptimizer.pokemon_optimizer_backend.cups.CupRules;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.CaughtPokemonRequest;
import com.pokemonoptimizer.pokemon_optimizer_backend.dto.OwnedSpecies;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;

public class TeamBuilderTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();
    // narrower than the real search so the suite stays fast; same stages
    private static final TeamBuilder.Settings SMALL = new TeamBuilder.Settings(6, 6, 3, 40, 80, 2, 7);
    private static final TeamBuilder BUILDER = new TeamBuilder(SPECIES, MOVES, new MetaLookup(SPECIES, MOVES),
        new GoodIvs(SPECIES, new SuggestedMovesets(SPECIES)), SMALL);

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

    private static OwnedSpecies owned(String id)
    {
        return new OwnedSpecies(id, false);
    }

    private static TeamMember memberOf(TeamResult team, String speciesId)
    {
        return team.members().stream().filter(m -> m.speciesId().equals(speciesId)).findFirst().orElseThrow();
    }

    @Test
    public void tickedSpeciesBuildATeamWithAssumedIvs()
    {   // nothing entered exactly: indexes count from 0 in the species list
        List<OwnedSpecies> ticked = List.of(owned("medicham"), owned("registeel"), owned("azumarill"));

        TeamBuilderResult result = BUILDER.build(League.GREAT, List.of(), ticked);

        TeamResult best = result.teams().get(0);
        assertEquals(Set.of("medicham", "registeel", "azumarill"), species(best));
        assertTrue(best.members().stream().allMatch(TeamMember::assumed));
        assertEquals(0, memberOf(best, "medicham").index());
        assertEquals(3, result.eligible());
    }

    @Test
    public void anEnteredPokemonOverridesItsTickedSpecies()
    {   // the Trainer's real (poor) Medicham is used even though the assumed good one would rate higher
        List<CaughtPokemonRequest> entered = List.of(pokemon("medicham", 15, 0, 0, 20, "COUNTER", "ICE_PUNCH", "DYNAMIC_PUNCH"));
        List<OwnedSpecies> ticked = List.of(owned("medicham"), owned("registeel"), owned("azumarill"));

        TeamBuilderResult result = BUILDER.build(League.GREAT, entered, ticked);

        TeamMember medicham = memberOf(result.teams().get(0), "medicham");
        assertEquals(0, medicham.index());
        assertEquals(false, medicham.assumed());
        assertEquals(15, medicham.atkIv());
        TeamMember registeel = memberOf(result.teams().get(0), "registeel");
        assertEquals(1 + 1, registeel.index());   // pokemon.size() + its place in the species list
        assertTrue(registeel.assumed());
    }

    @Test
    public void aTickedSpeciesWithNoPvpMovesetIsSkipped()
    {   // PvPoke doesn't rank Charmander, so there's no moveset to assume
        TeamBuilderResult result = BUILDER.build(League.GREAT, List.of(), List.of(owned("medicham"), owned("charmander")));

        assertEquals(List.of(new SkippedPokemon(1, "No PvP moveset to assume; add it with its moves instead")), result.skipped());
    }

    @Test
    public void aPokemonWithoutALevelIsBuiltToTheBestLevel()
    {   // added without a CP: assumed to be able to reach the League's best level
        List<CaughtPokemonRequest> box = List.of(
            new CaughtPokemonRequest("medicham", 0, 15, 15, null, "COUNTER", List.of("ICE_PUNCH", "DYNAMIC_PUNCH"), false),
            pokemon("registeel", 0, 14, 15, 20, "LOCK_ON", "FOCUS_BLAST", "FLASH_CANNON"),
            pokemon("azumarill", 0, 14, 15, 20, "BUBBLE", "ICE_BEAM", "PLAY_ROUGH"));

        TeamMember medicham = memberOf(BUILDER.build(League.GREAT, box).teams().get(0), "medicham");

        assertEquals(50.0, medicham.level());
        assertEquals(1439, medicham.cp());
    }

    @Test
    public void aMemberBelowItsBuiltLevelShowsWhatPoweringUpChanges()
    {   // BOX's Registeel is level 20; built for Great League it's far higher
        TeamMember registeel = memberOf(BUILDER.build(League.GREAT, BOX).teams().get(0), "registeel");

        PowerUpImpact impact = registeel.powerUp();
        assertEquals(20.0, impact.fromLevel());
        assertEquals(League.cp(SPECIES.findById("registeel").orElseThrow(), 0, 14, 15, 20), impact.fromCp());
        assertEquals(44 * 3, impact.battles());
        assertTrue(impact.winsAfter() > impact.winsBefore(), impact.toString());
        assertEquals(impact.winsAfter() - impact.winsBefore(), impact.gained().size() - impact.lost().size());
    }

    @Test
    public void noPowerUpWithoutAKnownLowerLevel()
    {   // level unknown, already at its built level, or a checklist tick: nothing to compare against
        List<CaughtPokemonRequest> box = List.of(
            new CaughtPokemonRequest("medicham", 0, 15, 15, null, "COUNTER", List.of("ICE_PUNCH", "DYNAMIC_PUNCH"), false),
            pokemon("registeel", 0, 14, 15, 20, "LOCK_ON", "FOCUS_BLAST", "FLASH_CANNON"));
        TeamResult best = BUILDER.build(League.GREAT, box, List.of(owned("azumarill"))).teams().get(0);

        assertEquals(null, memberOf(best, "medicham").powerUp());
        assertEquals(null, memberOf(best, "azumarill").powerUp());
        TeamMember atBuiltLevel = memberOf(BUILDER.build(League.GREAT, List.of(
            pokemon("medicham", 0, 15, 15, 50, "COUNTER", "ICE_PUNCH", "DYNAMIC_PUNCH"),
            pokemon("registeel", 0, 14, 15, 20, "LOCK_ON", "FOCUS_BLAST", "FLASH_CANNON"),
            pokemon("azumarill", 0, 14, 15, 20, "BUBBLE", "ICE_BEAM", "PLAY_ROUGH"))).teams().get(0), "medicham");
        assertEquals(null, atBuiltLevel.powerUp());
    }

    @Test
    public void eachMemberSaysWhatPoweringItUpCosts()
    {   // from its current level to the one it's built at; unknown without a current level
        List<CaughtPokemonRequest> box = List.of(
            new CaughtPokemonRequest("medicham", 0, 15, 15, null, "COUNTER", List.of("ICE_PUNCH", "DYNAMIC_PUNCH"), false),
            pokemon("registeel", 0, 14, 15, 20, "LOCK_ON", "FOCUS_BLAST", "FLASH_CANNON"));
        TeamResult best = BUILDER.build(League.GREAT, box, List.of(owned("azumarill"))).teams().get(0);

        TeamMember registeel = memberOf(best, "registeel");
        assertEquals(PowerUpCost.between(20, registeel.level(), false), registeel.cost());
        assertTrue(registeel.cost().stardust() > 0);
        assertEquals(null, memberOf(best, "medicham").cost());
        assertEquals(null, memberOf(best, "azumarill").cost());
    }

    // ---- Cups ----

    private static Cup cup(String key, String title, CupRules rules)
    {   // the Great League meta's first 20, so a Cup's meta is visibly its own
        List<MetaPokemon> meta = new MetaLookup(SPECIES, MOVES).meta(League.GREAT).subList(0, 20);
        return new Cup(key, title, League.GREAT, 1500, 3, List.of(), rules, meta, "2026-11-03");
    }

    private static final Cup FANTASY = cup("fantasy-1500", "Fantasy Cup", new CupRules(
        List.of(new CupFilter("type", List.of("steel", "dragon", "fairy"), List.of(), false)),
        List.of(new CupFilter("tag", List.of("mega"), List.of(), false))));

    @Test
    public void aCupLeavesOutWhatItsRulesDontAllow()
    {   // Medicham is fighting/psychic; Registeel, Azumarill and a ticked Altaria are steel, fairy and dragon
        List<CaughtPokemonRequest> box = List.of(
            pokemon("medicham", 0, 15, 15, 20, "COUNTER", "ICE_PUNCH", "DYNAMIC_PUNCH"),
            pokemon("registeel", 0, 14, 15, 20, "LOCK_ON", "FOCUS_BLAST", "FLASH_CANNON"),
            pokemon("azumarill", 0, 14, 15, 20, "BUBBLE", "ICE_BEAM", "PLAY_ROUGH"));

        TeamBuilderResult result = BUILDER.build(FANTASY, box, List.of(owned("altaria")));

        assertTrue(result.skipped().contains(new SkippedPokemon(0, "Not allowed in Fantasy Cup")));
        assertEquals(Set.of("registeel", "azumarill", "altaria"), species(result.teams().get(0)));
    }

    @Test
    public void aCupBattlesItsOwnMetaAndSaysWhichCupItWas()
    {
        List<CaughtPokemonRequest> box = List.of(
            pokemon("registeel", 0, 14, 15, 20, "LOCK_ON", "FOCUS_BLAST", "FLASH_CANNON"),
            pokemon("azumarill", 0, 14, 15, 20, "BUBBLE", "ICE_BEAM", "PLAY_ROUGH"));

        TeamBuilderResult result = BUILDER.build(FANTASY, box, List.of(owned("altaria")));

        assertEquals(20, result.metaSize());
        assertEquals("2026-11-03", result.metaDate());
        assertEquals("fantasy-1500", result.cup());
        assertEquals(null, BUILDER.build(League.GREAT, box).cup());
    }

    @Test
    public void aCupDecidesForItselfWhetherMegasMayEnter()
    {   // the open Leagues ban Megas; a Mega Cup lets them in, and one tagged "mega" out keeps them out
        CaughtPokemonRequest mega = pokemon("venusaur_mega", 0, 0, 0, 1, "VINE_WHIP", "SLUDGE_BOMB");
        Cup megaCup = cup("mega-1500", "Mega Great League", new CupRules(List.of(), List.of()));

        assertEquals("Megas can't battle in GO Battle League", BUILDER.problemWith(mega, League.GREAT));
        assertEquals(null, BUILDER.problemWith(mega, League.GREAT, megaCup));
        assertEquals("Not allowed in Fantasy Cup", BUILDER.problemWith(mega, League.GREAT, FANTASY));
    }

    @Test
    public void aShadowIsCheckedAsAShadow()
    {
        Cup noShadows = cup("spectral-1500", "Spectral Cup", new CupRules(List.of(), List.of(new CupFilter("tag", List.of("shadow"), List.of(), false))));
        CaughtPokemonRequest shadow = new CaughtPokemonRequest("registeel", 0, 14, 15, 20.0, "LOCK_ON", List.of("FOCUS_BLAST"), true);

        assertEquals("Not allowed in Spectral Cup", BUILDER.problemWith(shadow, League.GREAT, noShadows));
    }
}
