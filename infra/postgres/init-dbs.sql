-- One database per service. Run as superuser on first startup.
CREATE DATABASE auth_db;
CREATE DATABASE project_db;
CREATE DATABASE member_db;
CREATE DATABASE design_db;
CREATE DATABASE tests_db;
CREATE DATABASE ticket_db;
CREATE DATABASE notification_db;
CREATE DATABASE analytics_db;

-- pgvector extension on analytics_db only
\c analytics_db
CREATE EXTENSION IF NOT EXISTS vector;

\c tests_db
CREATE EXTENSION IF NOT EXISTS vector;
