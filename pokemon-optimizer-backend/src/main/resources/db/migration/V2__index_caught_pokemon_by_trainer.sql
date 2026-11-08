-- Every Collection read and the batch duplicate check filter on the Trainer.
CREATE INDEX caught_pokemon_trainer_id_idx ON caught_pokemon (trainer_id);
