-- M3.1: real signup/login. Existing rows (pre-auth dev data) get an empty
-- hash — they can never match a login attempt, which is correct: they were
-- never signed up with a password. New rows always carry a real BCrypt hash.
ALTER TABLE users ADD COLUMN password_hash TEXT NOT NULL DEFAULT '';
ALTER TABLE users ALTER COLUMN password_hash DROP DEFAULT;
