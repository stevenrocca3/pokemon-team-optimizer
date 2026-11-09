package com.pokemonoptimizer.pokemon_optimizer_backend.web;

import java.io.IOException;
import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React app (copied into static/ by the Dockerfile) from the same origin as the API.
 * React Router owns page URLs, so any path that isn't a file or an API call gets index.html.
 * Controllers are matched before this, so real endpoints are untouched.
 */
@Configuration
public class SinglePageApp implements WebMvcConfigurer
{
    private static final Resource APP = new ClassPathResource("/static/index.html");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry)
    {
        // Vite names every bundle by its content hash: a new build is a new URL, so these never need rechecking
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/static/assets/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // index.html and the other unhashed files: always revalidated, so a deploy shows up on the next load
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.noCache())
            .resourceChain(true)
            .addResolver(new PathResourceResolver()
            {
                @Override
                protected Resource getResource(String path, Resource location) throws IOException
                {
                    Resource file = location.createRelative(path);
                    if (file.exists() && file.isReadable())
                    {
                        return file;
                    }
                    // an unknown API or actuator call, or a missing file (has an extension), stays a 404
                    boolean isPage = !path.startsWith("api/") && !path.startsWith("actuator/")
                        && !path.substring(path.lastIndexOf('/') + 1).contains(".");
                    return isPage && APP.exists() ? APP : null;
                }
            });
    }
}
