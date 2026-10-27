-- 002_add_users_security.sql
-- Adds user accounts and task ownership WITHOUT deleting existing tasks.
-- Safe to re-run (idempotent). Run after 001_create_schema.sql.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_email_lowercase CHECK (email = lower(email))
);

-- 1) Nullable owner column first, so existing rows remain valid while we migrate them.
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS owner_id BIGINT;

-- 2) Pre-existing tasks go to a placeholder account that nobody can log into: its password is a random
--    BCrypt hash that is discarded right away. The row is only created if there is data to migrate.
INSERT INTO users (name, email, password_hash, role)
SELECT 'Legacy tasks owner', 'legacy@todo.local', crypt(gen_random_uuid()::text, gen_salt('bf', 10)), 'USER'
WHERE EXISTS (SELECT 1 FROM tasks WHERE owner_id IS NULL)
ON CONFLICT (email) DO NOTHING;

UPDATE tasks
SET owner_id = (SELECT id FROM users WHERE email = 'legacy@todo.local')
WHERE owner_id IS NULL;

-- 3) Only now it is safe to make the owner mandatory and add the relation.
ALTER TABLE tasks ALTER COLUMN owner_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tasks_owner') THEN
        ALTER TABLE tasks
            ADD CONSTRAINT fk_tasks_owner FOREIGN KEY (owner_id) REFERENCES users (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_tasks_owner_id ON tasks (owner_id);

COMMIT;

-- To hand the legacy tasks to a real account (e.g. after registering ana@example.com):
--   UPDATE tasks
--   SET owner_id = (SELECT id FROM users WHERE email = 'ana@example.com')
--   WHERE owner_id = (SELECT id FROM users WHERE email = 'legacy@todo.local');
