package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesType;

/**
 * robots.txt and sitemap.xml for search engines. URLs are built from the request, which behind the host's proxy
 * already says https and the public domain (server.forward-headers-strategy), so no domain is hard-coded.
 */
@RestController
public class SeoController
{
    /** The public pages anyone can open; /collection is each visitor's own, so it isn't listed. */
    private static final List<String> PAGES = List.of("/", "/team-builder", "/import", "/privacy", "/terms");
    private static final CacheControl A_DAY = CacheControl.maxAge(Duration.ofDays(1)).cachePublic();

    private final SpeciesLookup speciesLookup;

    public SeoController(SpeciesLookup speciesLookup)
    {
        this.speciesLookup = speciesLookup;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots()
    {
        // /api stays crawlable: Googlebot fetches it to render the pages, and its X-Robots-Tag keeps it unindexed
        String robots = """
            User-agent: *
            Disallow: /oauth2/
            Disallow: /login/

            Sitemap: %s/sitemap.xml
            """.formatted(baseUrl());
        return ResponseEntity.ok().cacheControl(A_DAY).body(robots);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap()
    {
        String base = baseUrl();
        StringBuilder xml = new StringBuilder("""
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
            """);
        for (String page : PAGES)
        {
            appendUrl(xml, base + page);
        }
        speciesLookup.findAll().stream()
            .sorted(Comparator.comparingInt(SpeciesType::dex).thenComparing(SpeciesType::speciesId))
            .forEach(species -> appendUrl(xml, base + "/species/" + species.speciesId()));
        xml.append("</urlset>\n");
        return ResponseEntity.ok().cacheControl(A_DAY).body(xml.toString());
    }

    /** Species IDs are lowercase letters, digits and underscores, so nothing here needs XML escaping. */
    private static void appendUrl(StringBuilder xml, String url)
    {
        xml.append("  <url><loc>").append(url).append("</loc></url>\n");
    }

    private static String baseUrl()
    {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
