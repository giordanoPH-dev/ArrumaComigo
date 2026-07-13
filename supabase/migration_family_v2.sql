-- Arruma Comigo — migração incremental v2 (rodar UMA vez no SQL Editor).
-- Consolida: fix tasks.position + nomes nas memberships + membros se veem +
-- auto-Person no join/create + remove_member/sair da família.

-- 1) FIX PENDENTE: o web já envia "position" e o PostgREST rejeita
--    ("Could not find the 'position' column of 'tasks' in the schema cache").
alter table tasks add column if not exists position int not null default 0;

-- 2) Nome do membro na membership (canônico para a tela de família).
alter table household_members add column if not exists name text not null default '';

-- 3) Membros da MESMA família se veem. Sem recursão de RLS: my_households()
--    é SECURITY DEFINER e não repassa pela policy de household_members.
drop policy if exists "membros veem as proprias memberships" on household_members;
create policy "membros da familia se veem" on household_members
  for select using (household_id in (select my_households()));

-- 4) Nome da conta a partir do JWT (user_metadata.name gravado no signup).
--    Fallback: prefixo do e-mail (contas antigas sem metadata).
create or replace function member_name() returns text
language sql stable as $$
  select coalesce(
    nullif(auth.jwt() -> 'user_metadata' ->> 'name', ''),
    split_part(auth.jwt() ->> 'email', '@', 1)
  );
$$;

-- 5) Auto-cria a Person (avatar de atribuição) com o nome da conta,
--    se ainda não existir pessoa com esse nome (case-insensitive) na família.
create or replace function ensure_person(p_household uuid, p_name text) returns void
language plpgsql security definer set search_path = public as $$
declare
  -- mesmas 8 cores de PersonColors (Color.kt) / domain.js
  v_colors text[] := array['#6C4DDB','#9C8CF0','#E5739D','#F0A35C',
                           '#52B6A4','#5C9CE5','#C77DD6','#E5B84D'];
  v_count int;
begin
  if exists (select 1 from people
             where household_id = p_household and deleted = false
               and lower(name) = lower(p_name)) then
    return;
  end if;
  select count(*) into v_count from people
    where household_id = p_household and deleted = false;
  insert into people (uuid, name, color_hex, emoji, updated_at, deleted, household_id)
  values (replace(gen_random_uuid()::text, '-', ''), p_name,
          v_colors[(v_count % 8) + 1], '🙂',
          (extract(epoch from now()) * 1000)::bigint, false, p_household);
end;
$$;

-- 6) RPCs: MESMAS assinaturas (apps antigos seguem funcionando).
create or replace function create_household(p_name text) returns json
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
  v_code text;
begin
  v_code := upper(substr(md5(random()::text), 1, 6));
  insert into households (name, invite_code) values (p_name, v_code) returning id into v_id;
  insert into household_members (household_id, user_id, name)
    values (v_id, auth.uid(), member_name());
  perform ensure_person(v_id, member_name());
  return json_build_object('id', v_id, 'invite_code', v_code);
end;
$$;

create or replace function join_household(p_code text) returns json
language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
  v_code text;
begin
  select id, invite_code into v_id, v_code from households where invite_code = upper(p_code);
  if v_id is null then
    raise exception 'Código de convite inválido';
  end if;
  insert into household_members (household_id, user_id, name)
    values (v_id, auth.uid(), member_name())
    on conflict (household_id, user_id) do update set name = excluded.name;
  perform ensure_person(v_id, member_name());
  return json_build_object('id', v_id, 'invite_code', v_code);
end;
$$;

-- 7) Remover membro (auth.uid() precisa ser da mesma família).
--    Remover a si mesmo = sair da família. A Person (avatar) NÃO é apagada:
--    o histórico e as atribuições da casa continuam íntegros.
create or replace function remove_member(p_user_id uuid) returns void
language plpgsql security definer set search_path = public as $$
declare
  v_household uuid;
begin
  select household_id into v_household from household_members where user_id = auth.uid();
  if v_household is null then
    raise exception 'Você não está numa família';
  end if;
  delete from household_members
    where household_id = v_household and user_id = p_user_id;
end;
$$;

revoke execute on function remove_member(uuid) from anon, public;
grant execute on function remove_member(uuid) to authenticated;

-- 8) Backfill: memberships existentes sem nome ganham o prefixo do e-mail.
update household_members m
  set name = split_part(u.email, '@', 1)
  from auth.users u
  where u.id = m.user_id and m.name = '';

-- 9) PostgREST recarrega o schema (mata o erro de "schema cache" na hora).
notify pgrst, 'reload schema';
