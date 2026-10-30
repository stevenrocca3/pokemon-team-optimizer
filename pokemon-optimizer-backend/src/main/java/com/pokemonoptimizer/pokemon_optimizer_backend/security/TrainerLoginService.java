package com.pokemonoptimizer.pokemon_optimizer_backend.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.Trainer;
import com.pokemonoptimizer.pokemon_optimizer_backend.persistence.TrainerRepository;

@Service
public class TrainerLoginService extends OidcUserService
{
    private final TrainerRepository trainerRepository;
    public TrainerLoginService(TrainerRepository tr)
    {
        trainerRepository = tr;
    }

    @Override 
    public OidcUser loadUser(OidcUserRequest request)
    {
        OidcUser user = super.loadUser(request);
        if (trainerRepository.findByGoogleId(user.getSubject()).isEmpty())
        {
            trainerRepository.save(new Trainer(user.getSubject(), user.getFullName()));
        }
        return user;
    }
}
