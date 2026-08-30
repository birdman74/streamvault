ALTER TABLE users ADD CONSTRAINT chk_users_password_hash_or_google_id
    CHECK (password_hash IS NOT NULL OR google_id IS NOT NULL);
