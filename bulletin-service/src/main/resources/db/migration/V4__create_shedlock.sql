-- V4: ShedLock table (E1-4). Keeps scheduled and manually triggered jobs from running twice,
-- also across several application instances. Layout as documented by ShedLock's JDBC provider.

create table shedlock (
    name       varchar(64)  not null primary key,
    lock_until timestamp    not null,
    locked_at  timestamp    not null,
    locked_by  varchar(255) not null
);
