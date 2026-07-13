-- Arruma Comigo — schema do Supabase (rodar no SQL Editor do dashboard).
--
-- Notas de design:
-- * PK = uuid text gerado no cliente; o id Long do Room é só local.
-- * Nada é apagado no remoto: deletes viram tombstone (deleted = true).
-- * Sem FKs remotas: o cliente resolve as referências por uuid e pula órfãos.
--   (Também permite tombstonar por upsert parcial — HttpURLConnection não tem PATCH.)
-- * Colunas com DEFAULT para o upsert de tombstone {uuid, deleted, updated_at}
--   funcionar mesmo para linhas que nunca foram enviadas.
-- * updated_at = epoch millis do relógio do device (LWW no cliente).
-- * RLS desabilitado (default) — projeto privado do casal, anon key = acesso total.

create table people (
  uuid text primary key,
  name text not null default '',
  color_hex text not null default '',
  emoji text not null default '',
  updated_at bigint not null default 0,
  deleted boolean not null default false
);

create table rooms (
  uuid text primary key,
  name text not null default '',
  type text not null default 'OTHER',
  updated_at bigint not null default 0,
  deleted boolean not null default false
);

create table tasks (
  uuid text primary key,
  room_uuid text not null default '',
  title text not null default '',
  assigned_person_uuid text,
  priority text not null default 'MEDIUM',
  estimated_minutes int,
  recurrence text not null default 'NONE',
  recurrence_interval int not null default 1,
  days_of_week int not null default 0,
  next_due_date text not null default '',
  reminder_time text,
  reminder_enabled boolean not null default false,
  is_archived boolean not null default false,
  updated_at bigint not null default 0,
  deleted boolean not null default false
);

create table task_completions (
  uuid text primary key,
  task_uuid text not null default '',
  person_uuid text,
  task_title text not null default '',
  completed_at text not null default '',
  due_date text,
  updated_at bigint not null default 0,
  deleted boolean not null default false
);

create table scenarios (
  uuid text primary key,
  name text not null default '',
  updated_at bigint not null default 0,
  deleted boolean not null default false
);

create table scenario_items (
  uuid text primary key,
  scenario_uuid text not null default '',
  title text not null default '',
  checked boolean not null default false,
  position int not null default 0,
  updated_at bigint not null default 0,
  deleted boolean not null default false
);

create index on people (updated_at);
create index on rooms (updated_at);
create index on tasks (updated_at);
create index on task_completions (updated_at);
create index on scenarios (updated_at);
create index on scenario_items (updated_at);

-- migração incremental: reordenação (rodar em bancos que já aplicaram o schema acima)
alter table tasks add column position int not null default 0;
