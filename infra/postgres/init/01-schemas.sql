-- UM banco (marketplace), um SCHEMA e um USUARIO por servico.
-- Facilita rodar localmente (uma instancia, uma conexao no DBeaver) sem perder o isolamento:
-- cada usuario e dono SO do proprio schema e nao enxerga os outros (sem USAGE).
-- Em producao: troca-se o host/banco por servico sem mudar codigo (so a DB_URL).
-- Usuario "observer": somente leitura em tudo, para inspecao manual e suporte.

REVOKE ALL ON DATABASE marketplace FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

CREATE ROLE observer LOGIN PASSWORD 'observer';
GRANT CONNECT ON DATABASE marketplace TO observer;

DO $$
DECLARE
    svc TEXT;
BEGIN
    FOREACH svc IN ARRAY ARRAY['ingestion', 'catalog', 'inventory', 'orders', 'payments', 'invoicing', 'shipping', 'notification', 'audit']
    LOOP
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', svc, svc);
        EXECUTE format('GRANT CONNECT ON DATABASE marketplace TO %I', svc);
        EXECUTE format('CREATE SCHEMA %I AUTHORIZATION %I', svc, svc);
        EXECUTE format('ALTER ROLE %I SET search_path = %I', svc, svc);
        -- leitura para o observer, inclusive nas tabelas que o Flyway ainda vai criar
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO observer', svc);
        EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I GRANT SELECT ON TABLES TO observer', svc, svc);
    END LOOP;
END
$$;
