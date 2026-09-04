-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

begin;

-- A verified account gets at most five completion attempts per fixed
-- 15-minute window. The table is private, bounded, and automatically cleaned.
create table if not exists private.profile_completion_rate_limits (
  user_id uuid primary key references auth.users(id) on delete cascade,
  window_started_at timestamptz not null,
  attempt_count smallint not null,
  last_attempt_at timestamptz not null,
  constraint profile_completion_attempt_count
    check (attempt_count between 1 and 5)
);

create index if not exists profile_completion_rate_limits_cleanup_idx
  on private.profile_completion_rate_limits (last_attempt_at);

alter table private.profile_completion_rate_limits enable row level security;
alter table private.profile_completion_rate_limits force row level security;

drop policy if exists "deny_all_profile_completion_rate_limits"
  on private.profile_completion_rate_limits;
create policy "deny_all_profile_completion_rate_limits"
  on private.profile_completion_rate_limits
  for all
  to public
  using (false)
  with check (false);

revoke all on table private.profile_completion_rate_limits
  from public, anon, authenticated, service_role;

create or replace function private.consume_profile_completion_attempt()
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_user_id uuid := (select auth.uid());
  v_now timestamptz := clock_timestamp();
  v_allowed boolean := false;
  v_has_counter boolean;
begin
  if v_user_id is null then
    return false;
  end if;

  -- The global lock makes both cleanup and the capacity check exact under
  -- concurrent registrations. Profile completion is a low-frequency action.
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('profile-completion-rate-capacity', 0)
  );

  -- Bounded cleanup avoids an attacker turning one request into unbounded
  -- work. Expired rows no longer contribute to the hard table capacity.
  delete from private.profile_completion_rate_limits as limits
  where limits.user_id in (
    select expired.user_id
    from private.profile_completion_rate_limits as expired
    where expired.last_attempt_at <= v_now - interval '15 minutes'
    order by expired.last_attempt_at
    limit 256
  );

  select exists (
    select 1
    from private.profile_completion_rate_limits as limits
    where limits.user_id = v_user_id
  ) into v_has_counter;

  if not v_has_counter
     and (select count(*) from private.profile_completion_rate_limits) >= 10000 then
    return false;
  end if;

  insert into private.profile_completion_rate_limits as limits (
    user_id, window_started_at, attempt_count, last_attempt_at
  )
  values (v_user_id, v_now, 1, v_now)
  on conflict (user_id) do update
    set window_started_at = case
          when limits.window_started_at <= v_now - interval '15 minutes'
          then v_now
          else limits.window_started_at
        end,
        attempt_count = case
          when limits.window_started_at <= v_now - interval '15 minutes'
          then 1
          else limits.attempt_count + 1
        end,
        last_attempt_at = v_now
    where limits.window_started_at <= v_now - interval '15 minutes'
       or limits.attempt_count < 5
  returning true into v_allowed;

  return coalesce(v_allowed, false);
end;
$function$;

revoke all on function private.consume_profile_completion_attempt()
  from public, anon, authenticated, service_role;

-- Preserve the single-completion implementation behind a rate-limited
-- wrapper. The nested exception block rolls back only the failed profile
-- operation; the already-consumed counter remains and the RPC returns false.
alter function private.complete_user_profile_impl(
  text, text, text, text, text, text
) rename to complete_user_profile_once_impl;

revoke all on function private.complete_user_profile_once_impl(
  text, text, text, text, text, text
) from public, anon, authenticated, service_role;

create function private.complete_user_profile_impl(
  p_username text,
  p_first_name text,
  p_last_name text,
  p_dni text,
  p_phone text,
  p_expected_email text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_completed boolean;
begin
  if (select auth.uid()) is null
     or not private.current_session_is_active(false) then
    raise exception 'active authenticated session required'
      using errcode = '42501';
  end if;

  if not private.consume_profile_completion_attempt() then
    return false;
  end if;

  begin
    v_completed := private.complete_user_profile_once_impl(
      p_username,
      p_first_name,
      p_last_name,
      p_dni,
      p_phone,
      p_expected_email
    );
  exception
    when sqlstate '22023'
      or sqlstate '23505'
      or sqlstate '23514'
      or sqlstate '42501' then
      return false;
  end;

  return coalesce(v_completed, false);
end;
$function$;

revoke all on function private.complete_user_profile_impl(
  text, text, text, text, text, text
) from public, anon, authenticated, service_role;

-- Rebind the sole public gateway to the new private wrapper explicitly.
create or replace function public.complete_user_profile(
  p_username text,
  p_first_name text,
  p_last_name text,
  p_dni text,
  p_phone text,
  p_expected_email text
)
returns boolean
language sql
volatile
security definer
set search_path = ''
as $function$
  select private.complete_user_profile_impl($1, $2, $3, $4, $5, $6);
$function$;

revoke all on function public.complete_user_profile(
  text, text, text, text, text, text
) from public, anon, authenticated, service_role;
grant execute on function public.complete_user_profile(
  text, text, text, text, text, text
) to authenticated;

commit;

