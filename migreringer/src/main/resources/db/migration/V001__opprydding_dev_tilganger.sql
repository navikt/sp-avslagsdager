DO
$$
    BEGIN
        IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sp-avslagsdager-opprydding-dev')
        THEN
            GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO "sp-avslagsdager-opprydding-dev";
            GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO "sp-avslagsdager-opprydding-dev";
            ALTER DEFAULT PRIVILEGES FOR USER "sp-avslagsdager" IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO "sp-avslagsdager-opprydding-dev";
            ALTER DEFAULT PRIVILEGES FOR USER "sp-avslagsdager" IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO "sp-avslagsdager-opprydding-dev";
        END IF;
    END
$$;
