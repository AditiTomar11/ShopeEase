-- ===========================================================================
-- Creates the other two logical databases in the local Postgres container.
--
-- Postgres can hold several databases in one instance, and the entrypoint
-- script only ever creates POSTGRES_DB (productdb), so orderdb and authdb are
-- created here. This file runs ONLY on first boot, when the data directory is
-- empty.
--
-- Why three databases and not three tables in one?
-- Because it is the rule that makes these microservices rather than a
-- distributed monolith: no service can JOIN another service's tables, so the
-- services can be deployed, scaled and failed independently.
--
-- In production each of these would be its own managed instance.
-- ===========================================================================

-- order-service
CREATE DATABASE orderdb OWNER shopease;

-- auth-service
CREATE DATABASE authdb OWNER shopease;
