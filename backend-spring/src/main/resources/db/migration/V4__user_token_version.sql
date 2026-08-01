ALTER TABLE "User"
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE "User"
    ADD CONSTRAINT ck_user_token_version CHECK (token_version >= 0);
