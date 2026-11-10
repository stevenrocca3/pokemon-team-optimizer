package com.pokemonoptimizer.pokemon_optimizer_backend.importer;

import java.text.Normalizer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Learnset;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.Move;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.MoveLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMoves;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SuggestedMovesets;

/** Turns Poké Genie's display names ("Stunfisk" + "Galar", "Mud-Slap") into our species and move IDs. */
@Component
public class PokeGenieMapper
{
    // Genie's region names vs PvPoke's ID suffixes
    private static final Map<String, String> REGIONS = Map.of(
        "alola", "alolan", "galar", "galarian", "hisui", "hisuian", "paldea", "paldean");

    // Species PvPoke only lists by form, for the ID Genie's plain name produces
    private static final Map<String, String> DEFAULT_FORMS = Map.ofEntries(
        Map.entry("tornadus", "tornadus_incarnate"),
        Map.entry("thundurus", "thundurus_incarnate"),
        Map.entry("landorus", "landorus_incarnate"),
        Map.entry("enamorus", "enamorus_incarnate"),
        Map.entry("giratina", "giratina_altered"),
        Map.entry("keldeo", "keldeo_ordinary"),
        Map.entry("meloetta", "meloetta_aria"),
        Map.entry("darmanitan", "darmanitan_standard"),
        Map.entry("darmanitan_galarian", "darmanitan_galarian_standard"),
        Map.entry("burmy", "burmy_plant"),
        Map.entry("cherrim", "cherrim_overcast"),
        Map.entry("hoopa_confined", "hoopa"));

    private final SpeciesLookup speciesLookup;
    private final MoveLookup moveLookup;
    private final SuggestedMovesets suggestedMovesets;

    public PokeGenieMapper(SpeciesLookup speciesLookup, MoveLookup moveLookup, SuggestedMovesets suggestedMovesets)
    {
        this.speciesLookup = speciesLookup;
        this.moveLookup = moveLookup;
        this.suggestedMovesets = suggestedMovesets;
    }

    public ImportPreview map(PokeGenieImport parsed)
    {
        List<ImportedPokemon> pokemon = new ArrayList<>();
        List<RejectedRow> rejected = new ArrayList<>(parsed.rejected());
        Map<String, Learnset> learnsets = new TreeMap<>();
        Map<String, SuggestedMoves> suggestions = new TreeMap<>();

        for (PokeGenieRow row : parsed.rows())
        {
            String impossible = impossibleStats(row);
            if (impossible != null)
            {
                rejected.add(new RejectedRow(row.index(), impossible));
                continue;
            }
            Optional<SpeciesType> found = findSpecies(row.name(), row.form());
            if (found.isEmpty())
            {
                rejected.add(new RejectedRow(row.index(), "Unknown species: " + displayName(row)));
                continue;
            }
            SpeciesType species = found.get();
            if (row.shadow() && !species.shadowEligible())
            {
                rejected.add(new RejectedRow(row.index(), species.speciesName() + " can't be a Shadow"));
                continue;
            }
            Learnset learnset = species.learnset();

            // a move we can't place counts as missing: the player picks it on the import page
            String fastMove = findMove(row.fastMove(), learnset.fastMoves());
            List<String> chargedMoves = Stream.of(row.chargedMove1(), row.chargedMove2())
                .map(name -> findMove(name, learnset.chargedMoves()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            boolean needsMoves = fastMove == null || chargedMoves.isEmpty();
            if (needsMoves)
            {
                learnsets.put(species.speciesId(), learnset);
                suggestedMovesets.forSpecies(species.speciesId()).ifPresent(moves -> suggestions.put(species.speciesId(), moves));
            }

            pokemon.add(new ImportedPokemon(row.index(), species.speciesId(), row.atkIv(), row.defIv(), row.staIv(),
                row.level(), row.shadow(), fastMove, chargedMoves, needsMoves));
        }

        rejected.sort(Comparator.comparingInt(RejectedRow::index));
        return new ImportPreview(pokemon, rejected, learnsets, suggestions);
    }

    /**
     * Saving refuses IVs outside 0 to 15 and levels outside 1 to 51 or between halves (CaughtPokemonRequest and
     * CaughtPokemonController), and refuses the whole import with them. Apps that write Genie-style files don't always
     * keep to that, so those rows are turned away here, where the player sees which ones and the rest still save.
     */
    private static String impossibleStats(PokeGenieRow row)
    {
        if (!isIv(row.atkIv()) || !isIv(row.defIv()) || !isIv(row.staIv()))
        {
            return "IVs " + row.atkIv() + "/" + row.defIv() + "/" + row.staIv() + " aren't possible (each is 0 to 15)";
        }
        if (row.level() < 1 || row.level() > 51)
        {
            return "Level " + level(row.level()) + " isn't possible (1 to 51)";
        }
        if (row.level() * 2 != Math.floor(row.level() * 2))
        {
            return "Level " + level(row.level()) + " isn't possible (levels go up in halves)";
        }
        return null;
    }

    private static boolean isIv(int iv)
    {
        return iv >= 0 && iv <= 15;
    }

    /** 0.0 → "0", 20.25 → "20.25" */
    private static String level(double level)
    {
        return BigDecimal.valueOf(level).stripTrailingZeros().toPlainString();
    }

    private Optional<SpeciesType> findSpecies(String name, String form)
    {
        String base = slug(name);
        String formSlug = slug(form);
        String id = formSlug.isEmpty() || formSlug.equals("normal")
            ? base
            : base + "_" + REGIONS.getOrDefault(formSlug, formSlug);
        return speciesLookup.findById(DEFAULT_FORMS.getOrDefault(id, id));
    }

    /**
     * The one move in {@code candidates} with this display name, or null. Genie drops the type from
     * moves like "Weather Ball", so a name also matches a candidate's name without its "(Fire)" part;
     * the species' learnset narrows that to one.
     */
    private String findMove(String genieName, List<String> candidates)
    {
        if (genieName.isBlank())
        {
            return null;
        }
        String key = moveKey(genieName);
        List<String> matches = candidates.stream()
            .filter(id -> {
                String name = moveLookup.findById(id).map(Move::name).orElse(id);
                return moveKey(name).equals(key) || moveKey(name.replaceAll("\\s*\\(.*\\)", "")).equals(key);
            })
            .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static String displayName(PokeGenieRow row)
    {
        return row.form().isBlank() || row.form().equals("Normal") ? row.name() : row.name() + " (" + row.form() + ")";
    }

    /** "Mr. Mime" → mr_mime, "Nidoran♀" → nidoran_female, "Flabébé" → flabebe, "Pom-Pom" → pom_pom */
    private static String slug(String text)
    {
        String ascii = Normalizer.normalize(text.replace("♀", "_female").replace("♂", "_male"), Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "");
        return ascii.toLowerCase()
            .replaceAll("[.'’]", "")
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }

    /** "Mud-Slap" and "Mud Slap" → mudslap */
    private static String moveKey(String name)
    {
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
