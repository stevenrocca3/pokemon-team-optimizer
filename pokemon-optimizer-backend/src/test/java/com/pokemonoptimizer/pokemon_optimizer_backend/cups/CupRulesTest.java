package com.pokemonoptimizer.pokemon_optimizer_backend.cups;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;

/** The filter semantics of PvPoke's GameMaster.generateFilteredPokemonList, on real species. */
public class CupRulesTest
{
    private static final SpeciesLookup SPECIES = new SpeciesLookup();

    private static CupEntrant entrant(String id, boolean shadow)
    {
        return CupEntrant.of(SPECIES.findById(id).orElseThrow(), SPECIES.traits(id).orElseThrow(), shadow);
    }

    private static CupEntrant entrant(String id)
    {
        return entrant(id, false);
    }

    private static CupFilter filter(String type, String... values)
    {
        return new CupFilter(type, List.of(values), List.of(), false);
    }

    private static CupRules rules(List<CupFilter> include, List<CupFilter> exclude)
    {
        return new CupRules(include, exclude);
    }

    @Test
    public void noFiltersLetEveryoneIn()
    {
        assertThat(rules(List.of(), List.of()).allows(entrant("medicham"), 1500)).isTrue();
    }

    @Test
    public void includedTypesLetInPokemonOfEitherType()
    {
        CupRules fighting = rules(List.of(filter("type", "fighting")), List.of());

        assertThat(fighting.allows(entrant("medicham"), 1500)).isTrue();   // fighting/psychic
        assertThat(fighting.allows(entrant("azumarill"), 1500)).isFalse();
    }

    @Test
    public void twoIncludeFiltersMustBothMatch()
    {
        CupRules fightingStarters = rules(List.of(filter("type", "grass"), filter("tag", "starter")), List.of());

        assertThat(fightingStarters.allows(entrant("venusaur"), 1500)).isTrue();
        assertThat(fightingStarters.allows(entrant("tangrowth"), 1500)).isFalse();   // grass, not a starter
    }

    @Test
    public void anIncludedIdLetsAPokemonInWhateverItsType()
    {
        CupRules cup = rules(List.of(filter("type", "fairy"), filter("id", "medicham")), List.of());

        assertThat(cup.allows(entrant("medicham"), 1500)).isTrue();
        assertThat(cup.allows(entrant("azumarill"), 1500)).isTrue();
        assertThat(cup.allows(entrant("registeel"), 1500)).isFalse();
    }

    @Test
    public void anIncludedIdOnlyCoversTheShadowWhenItSaysSo()
    {
        CupRules regularOnly = rules(List.of(filter("id", "medicham")), List.of());
        CupRules withShadows = rules(List.of(new CupFilter("id", List.of("medicham"), List.of(), true)), List.of());

        assertThat(regularOnly.allows(entrant("medicham", true), 1500)).isFalse();
        assertThat(withShadows.allows(entrant("medicham", true), 1500)).isTrue();
    }

    @Test
    public void anExcludedIdBansTheShadowToo()
    {
        CupRules noMedicham = rules(List.of(), List.of(filter("id", "medicham")));

        assertThat(noMedicham.allows(entrant("medicham"), 1500)).isFalse();
        assertThat(noMedicham.allows(entrant("medicham", true), 1500)).isFalse();
        assertThat(noMedicham.allows(entrant("azumarill"), 1500)).isTrue();
    }

    @Test
    public void anyMatchingExcludeFilterKeepsAPokemonOut()
    {
        CupRules cup = rules(List.of(), List.of(filter("tag", "legendary", "mythical"), filter("type", "steel")));

        assertThat(cup.allows(entrant("mewtwo"), 10000)).isFalse();
        assertThat(cup.allows(entrant("skarmory"), 1500)).isFalse();
        assertThat(cup.allows(entrant("medicham"), 1500)).isTrue();
    }

    @Test
    public void anIncludedIdBeatsAnExclusion()
    {
        // LAIC-style: every Mega is in, even of a banned type
        CupRules cup = rules(List.of(filter("type", "water"), filter("id", "venusaur_mega")), List.of(filter("type", "poison")));

        assertThat(cup.allows(entrant("venusaur_mega"), 1500)).isTrue();
        assertThat(cup.allows(entrant("venusaur"), 1500)).isFalse();
    }

    @Test
    public void aShadowIsTaggedShadow()
    {
        CupRules noShadows = rules(List.of(), List.of(filter("tag", "shadow")));
        CupRules onlyShadowEligible = rules(List.of(filter("tag", "shadoweligible")), List.of());

        assertThat(noShadows.allows(entrant("venusaur", true), 1500)).isFalse();
        assertThat(noShadows.allows(entrant("venusaur"), 1500)).isTrue();
        assertThat(onlyShadowEligible.allows(entrant("venusaur", true), 1500)).isFalse();
    }

    @Test
    public void aFilterLimitedToLeaguesIsIgnoredElsewhere()
    {
        CupRules cup = rules(List.of(), List.of(new CupFilter("id", List.of("mewtwo"), List.of(1500), false)));

        assertThat(cup.allows(entrant("mewtwo"), 1500)).isFalse();
        assertThat(cup.allows(entrant("mewtwo"), 10000)).isTrue();
    }

    @Test
    public void evolutionStageFiltersLikeLittleCup()
    {
        CupRules little = rules(List.of(filter("evolution", "1")), List.of());

        assertThat(little.allows(entrant("bulbasaur"), 500)).isTrue();
        assertThat(little.allows(entrant("ivysaur"), 500)).isFalse();
    }

    @Test
    public void dexFiltersTakeInclusiveRangesInPairs()
    {
        CupRules kanto = rules(List.of(filter("dex", "1", "151", "387", "493")), List.of());

        assertThat(kanto.allows(entrant("venusaur"), 1500)).isTrue();          // 3
        assertThat(kanto.allows(entrant("mewtwo"), 1500)).isTrue();            // 150
        assertThat(kanto.allows(entrant("registeel"), 1500)).isFalse();        // 379
        assertThat(kanto.allows(entrant("bastiodon"), 1500)).isTrue();         // 411
    }

    @Test
    public void moveFiltersLookAtTheWholeLearnset()
    {
        CupRules noCounter = rules(List.of(), List.of(filter("move", "COUNTER")));

        assertThat(noCounter.allows(entrant("medicham"), 1500)).isFalse();
        assertThat(noCounter.allows(entrant("azumarill"), 1500)).isTrue();
    }
}
