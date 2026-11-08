package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.pokemonoptimizer.pokemon_optimizer_backend.importer.ImportPreview;
import com.pokemonoptimizer.pokemon_optimizer_backend.importer.NotAPokeGenieExportException;
import com.pokemonoptimizer.pokemon_optimizer_backend.importer.ExportFile;
import com.pokemonoptimizer.pokemon_optimizer_backend.importer.PokeGenieMapper;

/**
 * Reads an export and says what it would import. Public and stateless, so guests can use it too;
 * saving is POST /api/collection/batch, which needs a login.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController
{
    private final PokeGenieMapper mapper;

    public ImportController(PokeGenieMapper mapper)
    {
        this.mapper = mapper;
    }

    /** A Poké Genie or Calcy IV export; uploads are capped at 10 MB, so reading it whole is fine. */
    @PostMapping("/preview")
    public ImportPreview preview(@RequestParam("file") MultipartFile file) throws IOException
    {
        try
        {
            return mapper.map(ExportFile.parse(new String(file.getBytes(), StandardCharsets.UTF_8)));
        }
        catch (NotAPokeGenieExportException e)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
