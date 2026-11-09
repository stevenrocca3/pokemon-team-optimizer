package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.pokemonoptimizer.pokemon_optimizer_backend.gamemaster.SpeciesLookup;
import com.pokemonoptimizer.pokemon_optimizer_backend.security.SecurityConfig;

@WebMvcTest(SeoController.class)
@Import({SecurityConfig.class, SpeciesLookup.class})
public class SeoControllerTest
{
    @Autowired
    private MockMvcTester mvc;

    /** What a request looks like once the host's X-Forwarded-* headers are applied in production. */
    private static final RequestPostProcessor BEHIND_THE_PROXY = request -> {
        request.setScheme("https");
        request.setServerName("dexpresso.example");
        request.setServerPort(443);
        request.setSecure(true);
        return request;
    };

    @Test
    public void robotsKeepsLoginOutOfCrawlsButLetsGooglebotFetchTheApiItNeedsToRender() throws Exception
    {
        MvcTestResult result = mvc.get().uri("/robots.txt").with(BEHIND_THE_PROXY).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.TEXT_PLAIN);
        String robots = result.getResponse().getContentAsString();
        assertThat(robots).contains("User-agent: *", "Disallow: /oauth2/", "Disallow: /login/")
            .contains("Sitemap: https://dexpresso.example/sitemap.xml")
            .doesNotContain("Disallow: /api");   // API responses carry X-Robots-Tag: noindex instead
    }

    @Test
    public void theSitemapListsThePagesAndEverySpeciesWithAbsoluteUrls() throws Exception
    {
        MvcTestResult result = mvc.get().uri("/sitemap.xml").with(BEHIND_THE_PROXY).exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_XML);
        String xml = result.getResponse().getContentAsString();
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            .contains("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")
            .contains("<loc>https://dexpresso.example/</loc>")
            .contains("<loc>https://dexpresso.example/team-builder</loc>")
            .contains("<loc>https://dexpresso.example/privacy</loc>")
            .contains("<loc>https://dexpresso.example/terms</loc>")
            .contains("<loc>https://dexpresso.example/species/medicham</loc>")
            .contains("<loc>https://dexpresso.example/species/typhlosion_hisuian</loc>")
            .doesNotContain("/collection");   // each visitor's own: nothing for a search engine there
        long urls = xml.split("<url>", -1).length - 1;
        assertThat(urls).isEqualTo(new SpeciesLookup().findAll().size() + 5);
    }
}
