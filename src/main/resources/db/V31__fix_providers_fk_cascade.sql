-- V31: Fix duplicate FK constraint on providers.user_id
-- V2 defined two FKs: inline ON DELETE CASCADE + named CONSTRAINT NO ACTION
-- PostgreSQL resolves NO ACTION as dominant, blocking user deletes.
-- Drop the named NO ACTION constraint so CASCADE takes effect.

ALTER TABLE providers DROP CONSTRAINT IF EXISTS fk_provider_user;

ALTER TABLE providers ADD CONSTRAINT fk_provider_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
