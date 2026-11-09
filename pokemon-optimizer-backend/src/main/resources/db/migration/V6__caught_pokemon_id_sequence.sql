-- Hibernate takes ids 50 at a time from this sequence so imports can batch their INSERTs (CaughtPokemon).
-- INCREMENT BY must equal allocationSize. Start past every existing id; the IDENTITY default stays for manual inserts.
CREATE SEQUENCE caught_pokemon_seq INCREMENT BY 50;
SELECT setval('caught_pokemon_seq', (SELECT COALESCE(MAX(id), 0) FROM caught_pokemon) + 50);
