package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

/** Lets /api/v1/analytics/* handlers take an AnalyticsViewer parameter. */
@Configuration
public class AnalyticsWebConfig implements WebMvcConfigurer
{
    private final AnalyticsViewerResolver viewers;

    public AnalyticsWebConfig(ObjectProvider<TrainerRepository> trainers)
    {
        this.viewers = new AnalyticsViewerResolver(trainers);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers)
    {
        resolvers.add(viewers);
    }
}
