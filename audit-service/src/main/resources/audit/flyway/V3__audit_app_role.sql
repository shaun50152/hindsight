DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'hindsight_audit_app') THEN
        CREATE ROLE hindsight_audit_app LOGIN PASSWORD 'audit-app-test-password';
    END IF;
END
$$;

REVOKE ALL ON audit_log, audit_checkpoints FROM PUBLIC;

GRANT USAGE ON SCHEMA audit TO hindsight_audit_app;
GRANT SELECT, INSERT ON audit_log, audit_checkpoints TO hindsight_audit_app;

REVOKE UPDATE, DELETE ON audit_log FROM hindsight_audit_app;
REVOKE UPDATE, DELETE ON audit_checkpoints FROM hindsight_audit_app;
