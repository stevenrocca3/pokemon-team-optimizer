package com.pokemonoptimizer.pokemon_optimizer_backend.battle;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 400 random Great League matchups, each run through PvPoke's real simulator (tools/pvpoke-oracle/gen-fixture.js).
 * Every field of the result must match PvPoke exactly.
 */
public class PvPokeFixtureTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();
    private static final MoveLookup MOVES = new MoveLookup();

    @TestFactory
    Stream<DynamicTest> matchesPvPoke() throws Exception
    {
        JsonNode fixture;
        try (InputStream in = getClass().getResourceAsStream("/battle/pvpoke-matchups.json"))
        {
            fixture = JsonMapper.builder().build().readTree(in);
        }

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode matchup : fixture)
        {
            tests.add(DynamicTest.dynamicTest(name(matchup), () -> check(matchup)));
        }
        return tests.stream();
    }

    private static void check(JsonNode matchup)
    {
        JsonNode expected = matchup.get("expected");
        BattleResult result = Battle.simulate(
            combatant(matchup.get("a")), moveset(matchup.get("a")),
            combatant(matchup.get("b")), moveset(matchup.get("b")),
            new ShieldScenario(matchup.get("shields").get(0).asInt(), matchup.get("shields").get(1).asInt()));

        assertAll(
            () -> assertEquals(expected.get("turns").asInt(), result.turns(), "turns"),
            () -> assertEquals(expected.get("hp").get(0).asInt(), result.hpA(), "hpA"),
            () -> assertEquals(expected.get("hp").get(1).asInt(), result.hpB(), "hpB"),
            () -> assertEquals(expected.get("energy").get(0).asInt(), result.energyA(), "energyA"),
            () -> assertEquals(expected.get("energy").get(1).asInt(), result.energyB(), "energyB"),
            () -> assertEquals(expected.get("shields").get(0).asInt(), result.shieldsA(), "shieldsA"),
            () -> assertEquals(expected.get("shields").get(1).asInt(), result.shieldsB(), "shieldsB"),
            () -> assertEquals(expected.get("buffs").get(0).get(0).asInt(), result.atkStageA(), "atkStageA"),
            () -> assertEquals(expected.get("buffs").get(0).get(1).asInt(), result.defStageA(), "defStageA"),
            () -> assertEquals(expected.get("buffs").get(1).get(0).asInt(), result.atkStageB(), "atkStageB"),
            () -> assertEquals(expected.get("buffs").get(1).get(1).asInt(), result.defStageB(), "defStageB"),
            () -> assertEquals(expected.get("ratings").get(0).asInt(), result.ratingA(), "ratingA"),
            () -> assertEquals(expected.get("ratings").get(1).asInt(), result.ratingB(), "ratingB"));
    }

    private static Combatant combatant(JsonNode side)
    {
        JsonNode ivs = side.get("ivs");
        return new Combatant(SPECIES.findById(side.get("id").asString()).orElseThrow(),
            ivs.get(0).asInt(), ivs.get(1).asInt(), ivs.get(2).asInt(), side.get("level").asDouble(), side.get("shadow").asBoolean());
    }

    private static Moveset moveset(JsonNode side)
    {
        List<com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move> charged = new ArrayList<>();
        for (JsonNode id : side.get("charged"))
        {
            charged.add(MOVES.findById(id.asString()).orElseThrow());
        }
        return new Moveset(MOVES.findById(side.get("fast").asString()).orElseThrow(), charged);
    }

    private static String name(JsonNode m)
    {
        return describe(m.get("a")) + " vs " + describe(m.get("b")) + " " + m.get("shields").get(0).asInt() + "-"
            + m.get("shields").get(1).asInt();
    }

    private static String describe(JsonNode side)
    {
        StringBuilder s = new StringBuilder(side.get("shadow").asBoolean() ? "shadow " : "");
        s.append(side.get("id").asString()).append(" (").append(side.get("fast").asString());
        for (JsonNode c : side.get("charged"))
        {
            s.append(", ").append(c.asString());
        }
        return s.append(")").toString();
    }
}
