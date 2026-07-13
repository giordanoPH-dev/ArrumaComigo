-- Arruma Comigo — migração: contas + famílias (households) + RLS.
--
-- ATENÇÃO: rodar no SQL Editor do dashboard SÓ DEPOIS do app novo (com login
-- Google) estar instalado nos devices do casal. Ao ligar o RLS, o app antigo
-- (anon key sem usuário) para de sincronizar imediatamente.
--
-- Adoção dos dados atuais: a família do casal é criada aqui com id fixo e
-- código de convite CASA26; os dados existentes recebem esse household_id.
-- Giordano e Amanda entram na família pelo fluxo normal do app ("Entrar na
-- família" com o código CASA26).

-- ---------- Tabelas ----------

create table households (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  invite_code text not null unique,
  created_at timestamptz not null default now()
);

create table household_members (
  household_id uuid not null references households (id),
  user_id uuid not null,
  primary key (household_id, user_id)
);

-- (scenarios/scenario_items exigem o DDL novo do schema.sql já aplicado)
alter table people add column household_id uuid;
alter table rooms add column household_id uuid;
alter table tasks add column household_id uuid;
alter table task_completions add column household_id uuid;
alter table scenarios add column household_id uuid;
alter table scenario_items add column household_id uuid;

-- ---------- Adoção dos dados do casal ----------

insert into households (id, name, invite_code)
values ('00000000-0000-0000-0000-000000000001', 'Casa Giordano & Amanda', 'CASA26');

update people set household_id = '00000000-0000-0000-0000-000000000001';
update rooms set household_id = '00000000-0000-0000-0000-000000000001';
update tasks set household_id = '00000000-0000-0000-0000-000000000001';
update task_completions set household_id = '00000000-0000-0000-0000-000000000001';
update scenarios set household_id = '00000000-0000-0000-0000-000000000001';
update scenario_items set household_id = '00000000-0000-0000-0000-000000000001';

alter table people alter column household_id set not null;
alter table rooms alter column household_id set not null;
alter table tasks alter column household_id set not null;
alter table task_completions alter column household_id set not null;
alter table scenarios alter column household_id set not null;
alter table scenario_items alter column household_id set not null;

-- ---------- Helper para as policies ----------

create function my_households() returns setof uuid
language sql stable security definer set search_path = public as $$
  select household_id from household_members where user_id = auth.uid();
$$;

-- ---------- RLS ----------

alter table households enable row level security;
alter table household_members enable row level security;
alter table people enable row level security;
alter table rooms enable row level security;
alter table tasks enable row level security;
alter table task_completions enable row level security;
alter table scenarios enable row level security;
alter table scenario_items enable row level security;

create policy "membros veem as proprias memberships" on household_members
  for select using (user_id = auth.uid());

create policy "membros veem as proprias familias" on households
  for select using (id in (select my_households()));

create policy "dados da familia" on people
  for all using (household_id in (select my_households()))
  with check (household_id in (select my_households()));

create policy "dados da familia" on rooms
  for all using (household_id in (select my_households()))
  with check (household_id in (select my_households()));

create policy "dados da familia" on tasks
  for all using (household_id in (select my_households()))
  with check (household_id in (select my_households()));

create policy "dados da familia" on task_completions
  for all using (household_id in (select my_households()))
  with check (household_id in (select my_households()));

create policy "dados da familia" on scenarios
  for all using (household_id in (select my_households()))
  with check (household_id in (select my_households()));

create policy "dados da familia" on scenario_items
  for all using (household_id in (select my_households()))
  with check (household_id in (select my_households()));

-- ---------- RPCs (SECURITY DEFINER: inserem memberships por baixo do RLS) ----------

create function create_household(p_name text) returns json
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
  v_code text;
begin
  v_code := upper(substr(md5(random()::text), 1, 6));
  insert into households (name, invite_code) values (p_name, v_code) returning id into v_id;
  insert into household_members (household_id, user_id) values (v_id, auth.uid());
  return json_build_object('id', v_id, 'invite_code', v_code);
end;
$$;

create function join_household(p_code text) returns json
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
  v_code text;
begin
  select id, invite_code into v_id, v_code from households where invite_code = upper(p_code);
  if v_id is null then
    raise exception 'Código de convite inválido';
  end if;
  insert into household_members (household_id, user_id) values (v_id, auth.uid())
    on conflict do nothing;
  return json_build_object('id', v_id, 'invite_code', v_code);
end;
$$;

revoke execute on function create_household(text) from anon, public;
revoke execute on function join_household(text) from anon, public;
grant execute on function create_household(text) to authenticated;
grant execute on function join_household(text) to authenticated;
