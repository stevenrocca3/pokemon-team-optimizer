-- A Pokémon can be added without its CP. Its level is then unknown (NULL), and the Team Builder assumes it
-- can be powered to the League's best level.
ALTER TABLE caught_pokemon ALTER COLUMN level DROP NOT NULL;
