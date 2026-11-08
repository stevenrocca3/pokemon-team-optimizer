package com.pokemonoptimizer.pokemon_optimizer_backend.controller;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
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
import com.pokemonoptimizer.pokemon_optimizer_backend.importer.PokeGenieCsv;
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

    @PostMapping("/poke-genie")
    public ImportPreview previewPokeGenie(@RequestParam("file") MultipartFile file) throws IOException
    {
        try (Reader csv = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
        {
            return mapper.map(PokeGenieCsv.parse(csv));
        }
        catch (NotAPokeGenieExportException e)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
