-- The Collection checklist: species a Trainer has ticked as owned, without exact IVs. The Team Builder battles
-- them with assumed good IVs. Ids come 50 at a time so replacing a whole checklist goes out in JDBC batches.
CREATE SEQUENCE owned_species_seq INCREMENT BY 50;

CREATE TABLE owned_species (
    id         BIGINT PRIMARY KEY,
    trainer_id BIGINT       NOT NULL REFERENCES trainer (id),
    species_id VARCHAR(255) NOT NULL,
    shadow     BOOLEAN      NOT NULL,
    UNIQUE (trainer_id, species_id, shadow)
);
