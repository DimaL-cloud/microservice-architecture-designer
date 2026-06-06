ALTER TABLE projects
    ADD COLUMN total_input_tokens       bigint,
    ADD COLUMN total_output_tokens      bigint,
    ADD COLUMN total_generation_time_ms bigint;
