-- Disposable database only. Load the same privacy functions that run in the app.
-- psql -v ON_ERROR_STOP=1 -f supabase/tests/quick_reactions_bootstrap.sql
\ir bootstrap.sql
\ir ../schema.sql
