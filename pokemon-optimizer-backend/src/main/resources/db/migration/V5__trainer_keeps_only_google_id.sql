-- The name came from Google at first login and was never read back; /api/me takes it from the login session.
ALTER TABLE trainer DROP COLUMN name;
