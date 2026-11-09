package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The bundled PvPoke snapshot stands in for the live gamemaster, with its formats and cups swapped for test ones;
 * the bundled Great League group (resources/meta/great.json) stands in for every meta group.
 */
public class PvpokeCupsTest
{
    private static final JsonMapper JSON = new JsonMapper();
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();
    private static final JsonNode GREAT_GROUP = read("/meta/great.json");

    private static JsonNode read(String resource)
    {
        try (InputStream is = PvpokeCupsTest.class.getResourceAsStream(resource))
        {
            return JSON.readTree(is);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static ObjectNode gamemaster(String formats, String cups)
    {
        ObjectNode gamemaster = (ObjectNode) read("/gamemaster.json");
        gamemaster.set("formats", JSON.readTree(formats));
        gamemaster.set("cups", JSON.readTree(cups));
        return gamemaster;
    }

    private static final String FANTASY_FORMAT = """
        {"title": "Fantasy Cup", "cup": "fantasy", "cp": 1500, "meta": "fantasy", "showCup": true, "showFormat": true,
         "showMeta": true, "rules": ["Only Steel, Dragon and Fairy types."]}""";
    private static final String FANTASY_CUP = """
        {"name": "fantasy", "title": "Fantasy Cup",
         "include": [{"filterType": "type", "values": ["steel", "dragon", "fairy"]}],
         "exclude": [{"filterType": "tag", "values": ["mega"]}]}""";

    private static ParsedCups parse(ObjectNode gamemaster)
    {
        return PvpokeCups.parse(gamemaster, meta -> GREAT_GROUP, SPECIES, MOVES);
    }

    @Test
    public void aShownFormatComesWithItsRulesFiltersAndMeta()
    {
        ParsedCups parsed = parse(gamemaster("[" + FANTASY_FORMAT + "]", "[" + FANTASY_CUP + "]"));

        assertThat(parsed.sourceTimestamp()).isEqualTo("2026-10-04 00:17:21");
        assertThat(parsed.formats()).hasSize(1);
        CupFormat fantasy = parsed.formats().get(0);
        assertThat(fantasy.key()).isEqualTo("fantasy-1500");
        assertThat(fantasy.title()).isEqualTo("Fantasy Cup");
        assertThat(fantasy.cpCap()).isEqualTo(1500);
        assertThat(fantasy.partySize()).isEqualTo(3);
        assertThat(fantasy.rules()).containsExactly("Only Steel, Dragon and Fairy types.");
        assertThat(fantasy.eligibility().include()).containsExactly(new CupFilter("type", List.of("steel", "dragon", "fairy"), List.of(), false));
        assertThat(fantasy.eligibility().exclude()).containsExactly(new CupFilter("tag", List.of("mega"), List.of(), false));
        assertThat(fantasy.meta()).hasSize(44);
    }

    @Test
    public void metaEntriesGetPvPokesDefaultIvsForTheCap()
    {   // annihilape's cp1500 defaultIVs are [17, 4, 13, 13]; its Shadow has no row of its own
        CupFormat fantasy = parse(gamemaster("[" + FANTASY_FORMAT + "]", "[" + FANTASY_CUP + "]")).formats().get(0);

        assertThat(fantasy.meta().get(1)).isEqualTo(new MetaEntry("annihilape", true, "LOW_KICK", List.of("RAGE_FIST", "ICE_PUNCH"), 17, 4, 13, 13));
    }

    @Test
    public void masterLeagueMetaIsBuiltPerfectAtLevel50()
    {
        String format = FANTASY_FORMAT.replace("\"cp\": 1500", "\"cp\": 10000");

        MetaEntry first = parse(gamemaster("[" + format + "]", "[" + FANTASY_CUP + "]")).formats().get(0).meta().get(0);

        assertThat(List.of(first.level(), (double) first.atkIv(), (double) first.defIv(), (double) first.staIv())).containsExactly(50.0, 15.0, 15.0, 15.0);
    }

    @Test
    public void hiddenFormatsAndTheCustomFormatAreLeftOut()
    {
        String hidden = FANTASY_FORMAT.replace("\"showFormat\": true", "\"showFormat\": false");
        String custom = """
            {"title": "Custom", "cup": "custom", "cp": 1500, "meta": "great", "showFormat": true}""";

        ParsedCups parsed = parse(gamemaster("[" + FANTASY_FORMAT + "," + hidden + "," + custom + "]",
            "[" + FANTASY_CUP + ", {\"name\": \"custom\", \"include\": [], \"exclude\": []}]"));

        assertThat(parsed.formats()).extracting(CupFormat::key).containsExactly("fantasy-1500");
    }

    @Test
    public void aCupWithAFilterWeDontUnderstandIsSkippedWithANote()
    {
        String cost = FANTASY_CUP.replace("\"filterType\": \"type\"", "\"filterType\": \"cost\"");
        String other = FANTASY_FORMAT.replace("Fantasy Cup", "Other Cup").replace("\"fantasy\"", "\"other\"");
        String otherCup = FANTASY_CUP.replace("\"fantasy\"", "\"other\"");

        ParsedCups parsed = parse(gamemaster("[" + FANTASY_FORMAT + "," + other + "]", "[" + cost + "," + otherCup + "]"));

        assertThat(parsed.formats()).extracting(CupFormat::key).containsExactly("other-1500");
        assertThat(parsed.notes()).anySatisfy(note -> assertThat(note).contains("Fantasy Cup").contains("cost"));
    }

    @Test
    public void littleCupAndLevelCapsArentSupportedYet()
    {
        String little = FANTASY_FORMAT.replace("\"cp\": 1500", "\"cp\": 500");
        String classic = FANTASY_FORMAT.replace("Fantasy Cup", "Classic Cup").replace("\"fantasy\"", "\"classic\"");
        String classicCup = FANTASY_CUP.replace("\"fantasy\"", "\"classic\"").replace("\"exclude\"", "\"levelCap\": 40, \"exclude\"");
        String other = FANTASY_FORMAT.replace("Fantasy Cup", "Other Cup").replace("\"fantasy\"", "\"other\"");
        String otherCup = FANTASY_CUP.replace("\"fantasy\"", "\"other\"");

        ParsedCups parsed = parse(gamemaster("[" + little + "," + classic + "," + other + "]", "[" + FANTASY_CUP + "," + classicCup + "," + otherCup + "]"));

        assertThat(parsed.formats()).extracting(CupFormat::key).containsExactly("other-1500");
        assertThat(parsed.notes()).anySatisfy(note -> assertThat(note).contains("Fantasy Cup").contains("500"));
        assertThat(parsed.notes()).anySatisfy(note -> assertThat(note).contains("Classic Cup").contains("level cap"));
    }

    @Test
    public void filterLeaguesAndIncludeShadowsAreKept()
    {
        String cup = FANTASY_CUP.replace("\"values\": [\"mega\"]}", "\"values\": [\"mega\"], \"leagues\": [1500]}")
            .replace("\"values\": [\"steel\", \"dragon\", \"fairy\"]}", "\"values\": [\"steel\"]}, {\"filterType\": \"id\", \"values\": [\"medicham\"], \"includeShadows\": true}");

        CupRules rules = parse(gamemaster("[" + FANTASY_FORMAT + "]", "[" + cup + "]")).formats().get(0).eligibility();

        assertThat(rules.exclude().get(0).leagues()).containsExactly(1500);
        assertThat(rules.include().get(1)).isEqualTo(new CupFilter("id", List.of("medicham"), List.of(), true));
    }

    @Test
    public void numericFilterValuesBecomeStrings()
    {
        String little = FANTASY_CUP.replace("\"filterType\": \"type\", \"values\": [\"steel\", \"dragon\", \"fairy\"]", "\"filterType\": \"evolution\", \"values\": [1]");

        CupRules rules = parse(gamemaster("[" + FANTASY_FORMAT + "]", "[" + little + "]")).formats().get(0).eligibility();

        assertThat(rules.include().get(0).values()).containsExactly("1");
    }

    @Test
    public void playPokemonFormatsKeepTheirPartySize()
    {
        String cup = FANTASY_CUP.replace("\"exclude\"", "\"partySize\": 6, \"exclude\"");

        assertThat(parse(gamemaster("[" + FANTASY_FORMAT + "]", "[" + cup + "]")).formats().get(0).partySize()).isEqualTo(6);
    }

    @Test
    public void metaPokemonDexPressoDoesntKnowYetAreDroppedWithANote()
    {
        ObjectNode group = (ObjectNode) GREAT_GROUP.deepCopy().get(0);
        group.put("speciesId", "pokemon_from_next_week");
        var meta = JSON.createArrayNode().add(group).addAll((tools.jackson.databind.node.ArrayNode) GREAT_GROUP);

        ParsedCups parsed = PvpokeCups.parse(gamemaster("[" + FANTASY_FORMAT + "]", "[" + FANTASY_CUP + "]"), m -> meta, SPECIES, MOVES);

        assertThat(parsed.formats().get(0).meta()).hasSize(44);
        assertThat(parsed.notes()).anySatisfy(note -> assertThat(note).contains("pokemon_from_next_week"));
    }

    @Test
    public void aMetaTooSmallToSampleTeamsFromSkipsTheCup()
    {
        var tiny = JSON.createArrayNode().add(GREAT_GROUP.get(0)).add(GREAT_GROUP.get(2));
        String other = FANTASY_FORMAT.replace("Fantasy Cup", "Other Cup").replace("\"fantasy\"", "\"other\"");
        String otherCup = FANTASY_CUP.replace("\"fantasy\"", "\"other\"");
        Map<String, JsonNode> groups = Map.of("fantasy", tiny, "other", GREAT_GROUP);

        ParsedCups parsed = PvpokeCups.parse(gamemaster("[" + FANTASY_FORMAT + "," + other + "]", "[" + FANTASY_CUP + "," + otherCup + "]"),
            groups::get, SPECIES, MOVES);

        assertThat(parsed.formats()).extracting(CupFormat::key).containsExactly("other-1500");
        assertThat(parsed.notes()).anySatisfy(note -> assertThat(note).contains("Fantasy Cup").contains("meta"));
    }

    @Test
    public void aGamemasterWithoutFormatsIsRejected()
    {
        ObjectNode broken = gamemaster("[]", "[]");
        broken.remove("formats");

        assertThatThrownBy(() -> parse(broken)).isInstanceOf(CupDataException.class);
    }

    @Test
    public void noUsableFormatAtAllIsRejected()
    {   // better to keep yesterday's Cups than to replace them with nothing
        assertThatThrownBy(() -> parse(gamemaster("[]", "[]"))).isInstanceOf(CupDataException.class);
    }

    @Test
    public void aMetaGroupThatFailsToLoadFailsTheWholeSync()
    {   // a network blip mustn't quietly drop a Cup that was fine yesterday
        Function<String, JsonNode> failing = meta -> { throw new CupDataException("timed out"); };

        assertThatThrownBy(() -> PvpokeCups.parse(gamemaster("[" + FANTASY_FORMAT + "]", "[" + FANTASY_CUP + "]"), failing, SPECIES, MOVES))
            .isInstanceOf(CupDataException.class);
    }

    @Test
    public void aMegasThirdChargedMoveIsSetAsideWithANote()
    {   // PvPoke gives some Megas a third, Mega-only charged move; DexPresso's battles use two
        ObjectNode mega = JSON.createObjectNode();
        mega.put("speciesId", "dragonite_mega");
        mega.put("fastMove", "DRAGON_BREATH");
        mega.set("chargedMoves", JSON.readTree("[\"SUPER_POWER\", \"THUNDER_PUNCH\", \"OUTRAGE_PLUS\"]"));
        var meta = JSON.createArrayNode().add(mega).addAll((tools.jackson.databind.node.ArrayNode) GREAT_GROUP);

        ParsedCups parsed = PvpokeCups.parse(gamemaster("[" + FANTASY_FORMAT + "]", "[" + FANTASY_CUP + "]"), m -> meta, SPECIES, MOVES);

        MetaEntry first = parsed.formats().get(0).meta().get(0);
        assertThat(first.speciesId()).isEqualTo("dragonite_mega");
        assertThat(first.chargedMoves()).containsExactly("SUPER_POWER", "THUNDER_PUNCH");
        assertThat(parsed.notes()).anySatisfy(note -> assertThat(note).contains("dragonite_mega").contains("OUTRAGE_PLUS"));
    }
}
