-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

begin;

create table private.username_login_limits_v2 (
  bucket_scope text not null,
  bucket_key text not null,
  failures integer not null default 0,
  in_flight integer not null default 0,
  window_started_at timestamptz not null default now(),
  blocked_until timestamptz,
  updated_at timestamptz not null default now(),
  primary key (bucket_scope, bucket_key),
  constraint username_login_limits_v2_scope
    check (bucket_scope in ('ip', 'account_ip')),
  constraint username_login_limits_v2_key
    check (bucket_key ~ '^[0-9a-f]{64}$'),
  constraint username_login_limits_v2_failures
    check (failures between 0 and 1000),
  constraint username_login_limits_v2_in_flight
    check (in_flight between 0 and 1000)
);

create index username_login_limits_v2_cleanup_idx
  on private.username_login_limits_v2 (updated_at);

alter table private.username_login_limits_v2 enable row level security;
alter table private.username_login_limits_v2 force row level security;

create policy "deny_direct_username_login_limits_v2"
  on private.username_login_limits_v2
  as restrictive
  for all
  to public
  using (false)
  with check (false);

revoke all on table private.username_login_limits_v2
  from public, anon, authenticated, service_role;

create function private.username_login_reserve_v2(
  p_bucket_scope text,
  p_bucket_key text
)
returns table (allowed boolean, retry_after_seconds integer)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_capacity integer;
  v_threshold integer;
  v_count integer;
  v_row private.username_login_limits_v2%rowtype;
begin
  if p_bucket_scope not in ('ip', 'account_ip')
     or p_bucket_key !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid login rate-limit bucket' using errcode = '22023';
  end if;

  -- These controls are deliberately server constants. No request parameter can
  -- weaken the thresholds or enlarge the storage budget.
  if p_bucket_scope = 'ip' then
    v_capacity := 4096;
    v_threshold := 25;
  else
    v_capacity := 16384;
    v_threshold := 5;
  end if;

  select l.*
    into v_row
  from private.username_login_limits_v2 l
  where l.bucket_scope = p_bucket_scope
    and l.bucket_key = p_bucket_key
  for update;

  if not found then
    select count(*)::integer
      into v_count
    from private.username_login_limits_v2 l
    where l.bucket_scope = p_bucket_scope;

    if v_count >= v_capacity then
      delete from private.username_login_limits_v2 l
      where (l.bucket_scope, l.bucket_key) = (
        select oldest.bucket_scope, oldest.bucket_key
        from private.username_login_limits_v2 oldest
        where oldest.bucket_scope = p_bucket_scope
          and oldest.in_flight = 0
        order by oldest.updated_at, oldest.bucket_key
        limit 1
      );

      if not found then
        return query select false, 60;
        return;
      end if;
    end if;

    insert into private.username_login_limits_v2 (
      bucket_scope,
      bucket_key,
      failures,
      in_flight,
      window_started_at,
      blocked_until,
      updated_at
    ) values (
      p_bucket_scope,
      p_bucket_key,
      0,
      1,
      now(),
      null,
      now()
    );
    return query select true, 0;
    return;
  end if;

  if v_row.window_started_at < now() - interval '15 minutes' then
    update private.username_login_limits_v2 l
      set failures = 0,
          in_flight = 1,
          window_started_at = now(),
          blocked_until = null,
          updated_at = now()
    where l.bucket_scope = p_bucket_scope
      and l.bucket_key = p_bucket_key;
    return query select true, 0;
    return;
  end if;

  if v_row.blocked_until is not null and v_row.blocked_until > now() then
    return query select false, greatest(
      1,
      ceil(extract(epoch from (v_row.blocked_until - now())))::integer
    );
    return;
  end if;

  if v_row.failures + v_row.in_flight >= v_threshold then
    update private.username_login_limits_v2 l
      set blocked_until = now() + interval '15 minutes',
          updated_at = now()
    where l.bucket_scope = p_bucket_scope
      and l.bucket_key = p_bucket_key;
    return query select false, 900;
    return;
  end if;

  update private.username_login_limits_v2 l
    set in_flight = least(1000, l.in_flight + 1),
        updated_at = now()
  where l.bucket_scope = p_bucket_scope
    and l.bucket_key = p_bucket_key;

  return query select true, 0;
end;
$$;

create function private.username_login_finish_v2(
  p_bucket_scope text,
  p_bucket_key text,
  p_failed boolean
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_threshold integer;
  v_row private.username_login_limits_v2%rowtype;
  v_failures integer;
  v_in_flight integer;
begin
  if p_bucket_scope not in ('ip', 'account_ip')
     or p_bucket_key !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid login rate-limit bucket' using errcode = '22023';
  end if;

  v_threshold := case when p_bucket_scope = 'ip' then 25 else 5 end;

  select l.*
    into v_row
  from private.username_login_limits_v2 l
  where l.bucket_scope = p_bucket_scope
    and l.bucket_key = p_bucket_key
  for update;

  -- A finalizer never creates storage. Missing reservations are harmless after
  -- TTL cleanup or a capacity eviction.
  if not found then
    return;
  end if;

  v_in_flight := greatest(0, v_row.in_flight - 1);
  v_failures := least(1000, v_row.failures + case when p_failed then 1 else 0 end);

  if v_failures = 0 and v_in_flight = 0 then
    delete from private.username_login_limits_v2 l
    where l.bucket_scope = p_bucket_scope
      and l.bucket_key = p_bucket_key;
    return;
  end if;

  update private.username_login_limits_v2 l
    set failures = v_failures,
        in_flight = v_in_flight,
        blocked_until = case
          when v_failures + v_in_flight >= v_threshold
            then coalesce(v_row.blocked_until, now() + interval '15 minutes')
          else v_row.blocked_until
        end,
        updated_at = now()
  where l.bucket_scope = p_bucket_scope
    and l.bucket_key = p_bucket_key;
end;
$$;

create function public.username_login_lookup_v2(p_username text)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select p.email
  from private.user_profiles p
  where lower(p.username) = lower(trim(p_username))
  limit 1;
$$;

create function public.username_login_begin_ip_v2(p_ip_bucket text)
returns table (allowed boolean, retry_after_seconds integer)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_allowed boolean;
  v_retry integer;
begin
  if p_ip_bucket !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid login rate-limit bucket' using errcode = '22023';
  end if;

  -- One lock protects cleanup, capacity and reservations. Every function
  -- acquires it first, eliminating lock-order inversions.
  perform pg_catalog.pg_advisory_xact_lock(20260903920000);

  delete from private.username_login_limits_v2 l
  where l.updated_at < now() - interval '24 hours';

  select r.allowed, r.retry_after_seconds
    into v_allowed, v_retry
  from private.username_login_reserve_v2('ip', p_ip_bucket) r;

  if not v_allowed then
    return query select false, v_retry;
    return;
  end if;

  return query select true, 0;
end;
$$;

create function public.username_login_begin_account_v2(
  p_ip_bucket text,
  p_account_bucket text
)
returns table (allowed boolean, retry_after_seconds integer)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_allowed boolean;
  v_retry integer;
  v_ip private.username_login_limits_v2%rowtype;
begin
  if p_ip_bucket !~ '^[0-9a-f]{64}$'
     or p_account_bucket !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid login rate-limit bucket' using errcode = '22023';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(20260903920000);

  delete from private.username_login_limits_v2 l
  where l.updated_at < now() - interval '24 hours';

  -- The IP reservation must already exist. Recheck its block while holding the
  -- same lock, but never increment it a second time.
  select l.*
    into v_ip
  from private.username_login_limits_v2 l
  where l.bucket_scope = 'ip'
    and l.bucket_key = p_ip_bucket
  for update;

  if not found or v_ip.window_started_at < now() - interval '15 minutes' then
    return query select false, 60;
    return;
  end if;

  if v_ip.blocked_until is not null and v_ip.blocked_until > now() then
    return query select false, greatest(
      1,
      ceil(extract(epoch from (v_ip.blocked_until - now())))::integer
    );
    return;
  end if;

  select r.allowed, r.retry_after_seconds
    into v_allowed, v_retry
  from private.username_login_reserve_v2('account_ip', p_account_bucket) r;

  return query select v_allowed, v_retry;
end;
$$;

create function public.username_login_failure_v2(
  p_ip_bucket text,
  p_account_bucket text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_ip_bucket !~ '^[0-9a-f]{64}$'
     or (p_account_bucket is not null and p_account_bucket !~ '^[0-9a-f]{64}$') then
    raise exception 'invalid login rate-limit bucket' using errcode = '22023';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(20260903920000);
  perform private.username_login_finish_v2('ip', p_ip_bucket, true);
  if p_account_bucket is not null then
    perform private.username_login_finish_v2('account_ip', p_account_bucket, true);
  end if;
end;
$$;

create function public.username_login_success_v2(
  p_ip_bucket text,
  p_account_bucket text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_ip_bucket !~ '^[0-9a-f]{64}$'
     or (p_account_bucket is not null and p_account_bucket !~ '^[0-9a-f]{64}$') then
    raise exception 'invalid login rate-limit bucket' using errcode = '22023';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(20260903920000);
  perform private.username_login_finish_v2('ip', p_ip_bucket, false);
  if p_account_bucket is not null then
    perform private.username_login_finish_v2('account_ip', p_account_bucket, false);
  end if;
end;
$$;

revoke all on function private.username_login_reserve_v2(text, text)
  from public, anon, authenticated, service_role;
revoke all on function private.username_login_finish_v2(text, text, boolean)
  from public, anon, authenticated, service_role;
revoke all on function public.username_login_lookup_v2(text)
  from public, anon, authenticated;
revoke all on function public.username_login_begin_ip_v2(text)
  from public, anon, authenticated;
revoke all on function public.username_login_begin_account_v2(text, text)
  from public, anon, authenticated;
revoke all on function public.username_login_failure_v2(text, text)
  from public, anon, authenticated;
revoke all on function public.username_login_success_v2(text, text)
  from public, anon, authenticated;

grant execute on function public.username_login_lookup_v2(text)
  to service_role;
grant execute on function public.username_login_begin_ip_v2(text)
  to service_role;
grant execute on function public.username_login_begin_account_v2(text, text)
  to service_role;
grant execute on function public.username_login_failure_v2(text, text)
  to service_role;
grant execute on function public.username_login_success_v2(text, text)
  to service_role;

comment on table private.username_login_limits_v2 is
  'Bounded, TTL-cleaned username-login rate limits keyed only by SHA-256 buckets.';

commit;

