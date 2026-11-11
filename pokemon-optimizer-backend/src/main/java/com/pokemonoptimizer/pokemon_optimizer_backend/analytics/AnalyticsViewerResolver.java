package com.pokemonoptimizer.pokemon_optimizer_backend.analytics;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

/**
 * The analytics routes' session check (#121): a logged-in session with a Trainer behind it is a Player; anything
 * else (no session, or a session whose account was deleted) is a Guest. Handlers never read the session themselves.
 * The repository is looked up per request, so web test slices that never call an analytics route don't need one.
 */
public class AnalyticsViewerResolver implements HandlerMethodArgumentResolver
{
    private final ObjectProvider<TrainerRepository> trainers;

    public AnalyticsViewerResolver(ObjectProvider<TrainerRepository> trainers)
    {
        this.trainers = trainers;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter)
    {
        return AnalyticsViewer.class.equals(parameter.getParameterType());
    }

    @Override
    public AnalyticsViewer resolveArgument(MethodParameter parameter, ModelAndViewContainer mav, NativeWebRequest request,
                                           WebDataBinderFactory binderFactory)
    {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OidcUser user)
        {
            return trainers.getObject().findByGoogleId(user.getSubject())
                .<AnalyticsViewer>map(AnalyticsViewer.Player::new)
                .orElseGet(AnalyticsViewer.Guest::new);
        }
        return new AnalyticsViewer.Guest();
    }
}
