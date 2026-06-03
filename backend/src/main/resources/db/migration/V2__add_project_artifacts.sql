ALTER TABLE projects
    ADD COLUMN brief             jsonb,
    ADD COLUMN blueprint         jsonb,
    ADD COLUMN artifacts         jsonb,
    ADD COLUMN generation_error  text;
