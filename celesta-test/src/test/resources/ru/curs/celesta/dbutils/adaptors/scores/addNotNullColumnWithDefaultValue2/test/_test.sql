create schema test version '1.0';

create table t (
  title varchar(10) NOT NULL PRIMARY KEY,
  description varchar(25) NOT NULL DEFAULT 'DESC'
);