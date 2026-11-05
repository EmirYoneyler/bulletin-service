-- V2: TEFAS fund code, the identity used by every data source (E1-1).
-- Nullable so existing rows stay valid; PostgreSQL UNIQUE allows many NULLs.

alter table funds add column tefas_code varchar(10);
alter table funds add constraint uk_funds_tefas_code unique (tefas_code);
