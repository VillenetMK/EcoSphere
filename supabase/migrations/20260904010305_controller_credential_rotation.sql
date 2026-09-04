-- EcoSphere
-- Copyright (c) 2026 Gabriel Enrique Villenet Montero.
-- Todos los derechos reservados. Uso sujeto al archivo LICENSE.

-- Additive controller credential lifecycle. This migration does not revoke the
-- legacy controller endpoints and does not modify the active controller's
-- current credential. Deploy it before the controller-credentials Edge
-- Function; firmware migration and the legacy endpoint cutover remain staged.

begin;

create table private.controller_credential_rotations (
  id uuid primary key default extensions.gen_random_uuid(),
  controller_id bigint not null
    references private.device_controllers(id) on delete restrict,
  new_secret_hash bytea not null,
  prepare_nonce_hash bytea not null,
  challenge_hash bytea not null,
  commit_nonce_hash bytea,
  status text not null default 'prepared',
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  committed_at timestamptz,
  finished_at timestamptz,
  constraint controller_credential_rotations_new_secret_hash_size
    check (octet_length(new_secret_hash) = 32),
  constraint controller_credential_rotations_prepare_nonce_hash_size
    check (octet_length(prepare_nonce_hash) = 32),
  constraint controller_credential_rotations_challenge_hash_size
    check (octet_length(challenge_hash) = 32),
  constraint controller_credential_rotations_commit_nonce_hash_size
    check (commit_nonce_hash is null or octet_length(commit_nonce_hash) = 32),
  constraint controller_credential_rotations_status
    check (status in ('prepared', 'committed', 'cancelled', 'expired')),
  constraint controller_credential_rotations_expiry
    check (expires_at > created_at),
  constraint controller_credential_rotations_state
    check (
      (
        status = 'prepared'
        and commit_nonce_hash is null
        and committed_at is null
        and finished_at is null
      )
      or (
        status = 'committed'
        and commit_nonce_hash is not null
        and committed_at is not null
        and finished_at = committed_at
      )
      or (
        status in ('cancelled', 'expired')
        and commit_nonce_hash is null
        and committed_at is null
        and finished_at is not null
      )
    ),
  constraint controller_credential_rotations_prepare_nonce_unique
    unique (controller_id, prepare_nonce_hash)
);

create unique index controller_credential_rotations_one_prepared_idx
  on private.controller_credential_rotations (controller_id)
  where status = 'prepared';
create index controller_credential_rotations_cleanup_idx
  on private.controller_credential_rotations (created_at, id)
  where status <> 'prepared';

alter table private.controller_credential_rotations enable row level security;
alter table private.controller_credential_rotations force row level security;

create policy "deny_direct_controller_credential_rotations"
  on private.controller_credential_rotations
  as restrictive
  for all
  to public
  using (false)
  with check (false);

revoke all on table private.controller_credential_rotations
  from public, anon, authenticated, service_role;

create table private.controller_credential_rate_limits (
  bucket_scope text not null,
  bucket_key text not null,
  request_count integer not null default 0,
  window_started_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (bucket_scope, bucket_key),
  constraint controller_credential_rate_limits_scope
    check (bucket_scope in ('global', 'source', 'hardware')),
  constraint controller_credential_rate_limits_key
    check (bucket_key ~ '^[0-9a-f]{64}$'),
  constraint controller_credential_rate_limits_count
    check (request_count between 0 and 600)
);

create index controller_credential_rate_limits_cleanup_idx
  on private.controller_credential_rate_limits (updated_at, bucket_scope, bucket_key)
  where bucket_scope <> 'global';

alter table private.controller_credential_rate_limits enable row level security;
alter table private.controller_credential_rate_limits force row level security;

create policy "deny_direct_controller_credential_rate_limits"
  on private.controller_credential_rate_limits
  as restrictive
  for all
  to public
  using (false)
  with check (false);

revoke all on table private.controller_credential_rate_limits
  from public, anon, authenticated, service_role;

-- Source requests consume a global and per-source minute bucket. A second,
-- post-parse hardware bucket limits valid-size UID rotation. All attacker-
-- influenced rows are SHA-256 keys, TTL-cleaned and storage-capped. Existing
-- buckets use row locks; only new-key maintenance uses a global try-lock.
create function public.controller_credential_take_rate_limit(
  p_bucket_scope text,
  p_bucket_key text
)
returns table (allowed boolean, retry_after_seconds integer)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_scope text := lower(btrim(coalesce(p_bucket_scope, '')));
  v_global_key text;
  v_limit integer;
  v_bucket private.controller_credential_rate_limits%rowtype;
  v_global private.controller_credential_rate_limits%rowtype;
  v_row_count integer;
  v_retry integer;
begin
  if v_scope not in ('source', 'hardware')
     or coalesce(p_bucket_key, '') !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid controller credential rate-limit bucket'
      using errcode = '22023';
  end if;

  v_limit := case when v_scope = 'source' then 10 else 6 end;
  -- Existing buckets never acquire the maintenance lock. This fast path keeps
  -- one attacker creating new keys from serializing healthy known devices.
  select * into v_bucket
  from private.controller_credential_rate_limits as limit_row
  where limit_row.bucket_scope = v_scope
    and limit_row.bucket_key = p_bucket_key
  for update;

  if not found then
    -- New-key cleanup/capacity is the only globally serialized path. Never
    -- queue Edge workers behind a source-rotation flood.
    if not pg_catalog.pg_try_advisory_xact_lock(20260904045000) then
      return query select false, 1;
      return;
    end if;

    delete from private.controller_credential_rate_limits as limit_row
    where limit_row.ctid in (
      select expired.ctid
      from private.controller_credential_rate_limits as expired
      where expired.bucket_scope <> 'global'
        and expired.updated_at < now() - interval '24 hours'
      order by expired.updated_at, expired.bucket_scope, expired.bucket_key
      limit 128
    );

    select count(*)::integer into v_row_count
    from private.controller_credential_rate_limits as limit_row
    where limit_row.bucket_scope <> 'global';

    if v_row_count >= 4096 then
      delete from private.controller_credential_rate_limits as limit_row
      where limit_row.ctid = (
        select oldest.ctid
        from private.controller_credential_rate_limits as oldest
        where oldest.bucket_scope <> 'global'
        order by oldest.updated_at, oldest.bucket_scope, oldest.bucket_key
        limit 1
      );
      if not found then
        return query select false, 60;
        return;
      end if;
    end if;

    insert into private.controller_credential_rate_limits (
      bucket_scope, bucket_key, request_count, window_started_at, updated_at
    ) values (v_scope, p_bucket_key, 0, now(), now())
    on conflict (bucket_scope, bucket_key) do nothing;

    select * into v_bucket
    from private.controller_credential_rate_limits as limit_row
    where limit_row.bucket_scope = v_scope
      and limit_row.bucket_key = p_bucket_key
    for update;
  end if;

  if v_bucket.window_started_at <= now() - interval '1 minute' then
    update private.controller_credential_rate_limits as limit_row
    set request_count = 0,
        window_started_at = now(),
        updated_at = now()
    where limit_row.bucket_scope = v_scope
      and limit_row.bucket_key = p_bucket_key
    returning * into v_bucket;
  end if;

  if v_bucket.request_count >= v_limit then
    v_retry := greatest(1, ceil(extract(epoch from (
      v_bucket.window_started_at + interval '1 minute' - now()
    )))::integer);
    return query select false, v_retry;
    return;
  end if;

  if v_scope = 'source' then
    -- Sixty deterministic global shards at ten requests/minute retain a hard
    -- 600/minute aggregate ceiling without one hot global lock. A distributed
    -- flood can affect only its shards instead of blocking every controller.
    v_global_key := lpad(
      pg_catalog.to_hex(
        pg_catalog.get_byte(
          pg_catalog.decode(substring(p_bucket_key from 1 for 2), 'hex'), 0
        ) % 60
      ),
      64,
      '0'
    );

    insert into private.controller_credential_rate_limits (
      bucket_scope, bucket_key, request_count, window_started_at, updated_at
    ) values ('global', v_global_key, 0, now(), now())
    on conflict (bucket_scope, bucket_key) do nothing;

    select * into v_global
    from private.controller_credential_rate_limits as limit_row
    where limit_row.bucket_scope = 'global'
      and limit_row.bucket_key = v_global_key
    for update;

    if v_global.window_started_at <= now() - interval '1 minute' then
      update private.controller_credential_rate_limits as limit_row
      set request_count = 0,
          window_started_at = now(),
          updated_at = now()
      where limit_row.bucket_scope = 'global'
        and limit_row.bucket_key = v_global_key
      returning * into v_global;
    end if;

    if v_global.request_count >= 10 then
      v_retry := greatest(1, ceil(extract(epoch from (
        v_global.window_started_at + interval '1 minute' - now()
      )))::integer);
      return query select false, v_retry;
      return;
    end if;

    update private.controller_credential_rate_limits as limit_row
    set request_count = limit_row.request_count + 1,
        updated_at = now()
    where limit_row.bucket_scope = 'global'
      and limit_row.bucket_key = v_global_key;
  end if;

  update private.controller_credential_rate_limits as limit_row
  set request_count = limit_row.request_count + 1,
      updated_at = now()
  where limit_row.bucket_scope = v_scope
    and limit_row.bucket_key = p_bucket_key;

  return query select true, 0;
end;
$function$;

revoke all on function public.controller_credential_take_rate_limit(text, text)
  from public, anon, authenticated, service_role;
grant execute on function public.controller_credential_take_rate_limit(text, text)
  to service_role;

create function private.controller_credential_prepare_impl(
  p_hardware_uid text,
  p_current_secret text,
  p_new_secret text,
  p_prepare_nonce text
)
returns table (
  rotation_id uuid,
  challenge text,
  expires_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_hardware_uid text;
  v_current_secret text;
  v_new_secret text;
  v_prepare_nonce text;
  v_current_hash bytea;
  v_new_hash bytea;
  v_prepare_nonce_hash bytea;
  v_challenge text;
  v_challenge_hash bytea;
  v_rotation_id uuid;
  v_expires_at timestamptz := now() + interval '5 minutes';
  v_controller private.device_controllers%rowtype;
  v_strict_protocol boolean;
  v_recent_rotations integer;
begin
  if octet_length(coalesce(p_hardware_uid, '')) > 64
     or octet_length(coalesce(p_current_secret, '')) > 128
     or octet_length(coalesce(p_new_secret, '')) > 128
     or octet_length(coalesce(p_prepare_nonce, '')) > 64 then
    raise exception 'invalid controller credential rotation request'
      using errcode = '22023';
  end if;

  v_hardware_uid := upper(btrim(coalesce(p_hardware_uid, '')));
  v_current_secret := lower(btrim(coalesce(p_current_secret, '')));
  v_new_secret := lower(btrim(coalesce(p_new_secret, '')));
  v_prepare_nonce := lower(btrim(coalesce(p_prepare_nonce, '')));

  if v_hardware_uid !~ '^[0-9A-F]{12}$'
     or v_current_secret !~ '^[0-9a-f]{64}$'
     or v_new_secret !~ '^[0-9a-f]{64}$'
     or v_prepare_nonce !~ '^[0-9a-f]{32}$'
     or v_current_secret = v_new_secret then
    raise exception 'invalid controller credential rotation request'
      using errcode = '22023';
  end if;

  v_current_hash := extensions.digest(v_current_secret, 'sha256');
  v_new_hash := extensions.digest(v_new_secret, 'sha256');
  v_prepare_nonce_hash := extensions.digest(v_prepare_nonce, 'sha256');

  -- Reject cheaply before taking the ecosystem lock, then authenticate again
  -- after acquiring the global ecosystem -> controller lock order.
  select controller.* into v_controller
  from private.device_controllers as controller
  join private.ecosystems as ecosystem
    on ecosystem.id = controller.ecosystem_id
   and ecosystem.active_controller_id = controller.id
  where controller.hardware_uid = v_hardware_uid
    and controller.secret_hash = v_current_hash
    and controller.status = 'active';

  if not found then
    raise exception 'controller credential rotation rejected'
      using errcode = '42501';
  end if;

  select ecosystem.strict_controller_protocol into v_strict_protocol
  from private.ecosystems as ecosystem
  where ecosystem.id = v_controller.ecosystem_id
  for update;

  if not found or not v_strict_protocol then
    raise exception 'controller credential rotation rejected'
      using errcode = '42501';
  end if;

  select controller.* into v_controller
  from private.device_controllers as controller
  join private.ecosystems as ecosystem
    on ecosystem.id = controller.ecosystem_id
   and ecosystem.active_controller_id = controller.id
  where controller.id = v_controller.id
    and controller.hardware_uid = v_hardware_uid
    and controller.secret_hash = v_current_hash
    and controller.status = 'active'
  for update of controller;

  if not found
     or v_controller.last_seen_at is null
     or v_controller.last_seen_at <= now() - interval '2 minutes' then
    raise exception 'controller credential rotation rejected'
      using errcode = '42501';
  end if;

  if exists (
    select 1
    from private.controller_credential_rotations as rotation
    where rotation.controller_id = v_controller.id
      and rotation.prepare_nonce_hash = v_prepare_nonce_hash
  ) then
    raise exception 'controller credential rotation rejected'
      using errcode = '42501';
  end if;

  select count(*)::integer into v_recent_rotations
  from private.controller_credential_rotations as rotation
  where rotation.controller_id = v_controller.id
    and rotation.created_at > now() - interval '1 hour';

  if v_recent_rotations >= 4 then
    raise exception 'controller credential rotation rate limit exceeded'
      using errcode = '42900';
  end if;

  update private.controller_credential_rotations as rotation
  set status = 'expired',
      finished_at = now()
  where rotation.controller_id = v_controller.id
    and rotation.status = 'prepared'
    and rotation.expires_at <= now();

  update private.controller_credential_rotations as rotation
  set status = 'cancelled',
      finished_at = now()
  where rotation.controller_id = v_controller.id
    and rotation.status = 'prepared';

  delete from private.controller_credential_rotations as rotation
  where rotation.ctid in (
    select old_rotation.ctid
    from private.controller_credential_rotations as old_rotation
    where old_rotation.controller_id = v_controller.id
      and old_rotation.status <> 'prepared'
      and old_rotation.finished_at < now() - interval '7 days'
    order by old_rotation.finished_at, old_rotation.id
    limit 64
  );

  v_challenge := pg_catalog.encode(extensions.gen_random_bytes(32), 'hex');
  v_challenge_hash := extensions.digest(v_challenge, 'sha256');

  insert into private.controller_credential_rotations (
    controller_id, new_secret_hash, prepare_nonce_hash, challenge_hash,
    expires_at
  ) values (
    v_controller.id, v_new_hash, v_prepare_nonce_hash, v_challenge_hash,
    v_expires_at
  ) returning id into v_rotation_id;

  insert into private.controller_events (
    ecosystem_id, controller_id, event_type, details
  ) values (
    v_controller.ecosystem_id,
    v_controller.id,
    'credential_rotation_prepared',
    pg_catalog.jsonb_build_object(
      'rotation_id', v_rotation_id,
      'expires_at', v_expires_at
    )
  );

  return query select v_rotation_id, v_challenge, v_expires_at;
end;
$function$;

revoke all on function private.controller_credential_prepare_impl(
  text, text, text, text
) from public, anon, authenticated, service_role;

create function public.controller_credential_prepare(
  p_hardware_uid text,
  p_current_secret text,
  p_new_secret text,
  p_prepare_nonce text
)
returns table (
  rotation_id uuid,
  challenge text,
  expires_at timestamptz
)
language sql
volatile
security definer
set search_path = ''
as $function$
  select * from private.controller_credential_prepare_impl($1, $2, $3, $4);
$function$;

revoke all on function public.controller_credential_prepare(
  text, text, text, text
) from public, anon, authenticated, service_role;
grant execute on function public.controller_credential_prepare(
  text, text, text, text
) to service_role;

create function private.controller_credential_commit_impl(
  p_hardware_uid text,
  p_new_secret text,
  p_rotation_id uuid,
  p_challenge text,
  p_commit_nonce text
)
returns table (committed boolean, already_committed boolean)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_hardware_uid text;
  v_new_secret text;
  v_challenge text;
  v_commit_nonce text;
  v_new_hash bytea;
  v_challenge_hash bytea;
  v_commit_nonce_hash bytea;
  v_controller private.device_controllers%rowtype;
  v_rotation private.controller_credential_rotations%rowtype;
  v_strict_protocol boolean;
begin
  if p_rotation_id is null
     or octet_length(coalesce(p_hardware_uid, '')) > 64
     or octet_length(coalesce(p_new_secret, '')) > 128
     or octet_length(coalesce(p_challenge, '')) > 128
     or octet_length(coalesce(p_commit_nonce, '')) > 64 then
    raise exception 'invalid controller credential commit request'
      using errcode = '22023';
  end if;

  v_hardware_uid := upper(btrim(coalesce(p_hardware_uid, '')));
  v_new_secret := lower(btrim(coalesce(p_new_secret, '')));
  v_challenge := lower(btrim(coalesce(p_challenge, '')));
  v_commit_nonce := lower(btrim(coalesce(p_commit_nonce, '')));

  if v_hardware_uid !~ '^[0-9A-F]{12}$'
     or v_new_secret !~ '^[0-9a-f]{64}$'
     or v_challenge !~ '^[0-9a-f]{64}$'
     or v_commit_nonce !~ '^[0-9a-f]{32}$' then
    raise exception 'invalid controller credential commit request'
      using errcode = '22023';
  end if;

  v_new_hash := extensions.digest(v_new_secret, 'sha256');
  v_challenge_hash := extensions.digest(v_challenge, 'sha256');
  v_commit_nonce_hash := extensions.digest(v_commit_nonce, 'sha256');

  select ecosystem.strict_controller_protocol into v_strict_protocol
  from private.ecosystems as ecosystem
  where ecosystem.id = 1
  for update;

  if not found or not v_strict_protocol then
    raise exception 'controller credential commit rejected'
      using errcode = '42501';
  end if;

  select controller.* into v_controller
  from private.device_controllers as controller
  join private.ecosystems as ecosystem
    on ecosystem.id = controller.ecosystem_id
   and ecosystem.active_controller_id = controller.id
  where controller.hardware_uid = v_hardware_uid
    and controller.status = 'active'
  for update of controller;

  if not found then
    raise exception 'controller credential commit rejected'
      using errcode = '42501';
  end if;

  select rotation.* into v_rotation
  from private.controller_credential_rotations as rotation
  where rotation.id = p_rotation_id
    and rotation.controller_id = v_controller.id
  for update;

  if not found
     or v_rotation.new_secret_hash <> v_new_hash
     or v_rotation.challenge_hash <> v_challenge_hash then
    raise exception 'controller credential commit rejected'
      using errcode = '42501';
  end if;

  if v_rotation.status = 'committed' then
    if v_rotation.commit_nonce_hash = v_commit_nonce_hash
       and v_controller.secret_hash = v_new_hash then
      return query select true, true;
      return;
    end if;
    raise exception 'controller credential commit rejected'
      using errcode = '42501';
  end if;

  if v_rotation.status <> 'prepared' then
    raise exception 'controller credential commit rejected'
      using errcode = '42501';
  end if;

  if v_rotation.expires_at <= now() then
    update private.controller_credential_rotations as rotation
    set status = 'expired',
        finished_at = now()
    where rotation.id = v_rotation.id;
    raise exception 'controller credential commit rejected'
      using errcode = '42501';
  end if;

  if v_controller.secret_hash = v_new_hash then
    raise exception 'controller credential commit rejected'
      using errcode = '42501';
  end if;

  update private.device_controllers as controller
  set secret_hash = v_new_hash,
      updated_at = now()
  where controller.id = v_controller.id;

  update private.controller_credential_rotations as rotation
  set status = 'committed',
      commit_nonce_hash = v_commit_nonce_hash,
      committed_at = now(),
      finished_at = now()
  where rotation.id = v_rotation.id;

  -- A credential epoch change retires every boot nonce from the previous
  -- epoch. Firmware generates a fresh boot nonce before its next sync.
  update private.controller_boot_sessions as boot
  set retired_at = coalesce(boot.retired_at, now()),
      last_seen_at = now()
  where boot.controller_id = v_controller.id
    and boot.retired_at is null;

  insert into private.controller_events (
    ecosystem_id, controller_id, event_type, details
  ) values (
    v_controller.ecosystem_id,
    v_controller.id,
    'credential_rotation_committed',
    pg_catalog.jsonb_build_object('rotation_id', v_rotation.id)
  );

  return query select true, false;
end;
$function$;

revoke all on function private.controller_credential_commit_impl(
  text, text, uuid, text, text
) from public, anon, authenticated, service_role;

create function public.controller_credential_commit(
  p_hardware_uid text,
  p_new_secret text,
  p_rotation_id uuid,
  p_challenge text,
  p_commit_nonce text
)
returns table (committed boolean, already_committed boolean)
language sql
volatile
security definer
set search_path = ''
as $function$
  select * from private.controller_credential_commit_impl($1, $2, $3, $4, $5);
$function$;

revoke all on function public.controller_credential_commit(
  text, text, uuid, text, text
) from public, anon, authenticated, service_role;
grant execute on function public.controller_credential_commit(
  text, text, uuid, text, text
) to service_role;

create function private.controller_revoke_active_impl(p_reason text)
returns table (
  controller_id bigint,
  hardware_uid_masked text,
  controller_status text,
  revoked_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_actor_id uuid := private.require_admin_aal2();
  v_reason text := btrim(coalesce(p_reason, ''));
  v_controller private.device_controllers%rowtype;
  v_revoked_at timestamptz := now();
begin
  if octet_length(coalesce(p_reason, '')) > 400
     or char_length(v_reason) not between 10 and 240 then
    raise exception 'revocation reason must contain 10 to 240 characters'
      using errcode = '22023';
  end if;

  perform 1
  from private.ecosystems as ecosystem
  where ecosystem.id = 1
  for update;

  select controller.* into v_controller
  from private.device_controllers as controller
  join private.ecosystems as ecosystem
    on ecosystem.id = controller.ecosystem_id
   and ecosystem.active_controller_id = controller.id
  where controller.status = 'active'
  for update of controller;

  if not found then
    raise exception 'there is no active controller to revoke'
      using errcode = 'P0002';
  end if;

  update private.controller_credential_rotations as rotation
  set status = 'cancelled',
      finished_at = v_revoked_at
  where rotation.controller_id = v_controller.id
    and rotation.status = 'prepared';

  update private.controller_boot_sessions as boot
  set retired_at = coalesce(boot.retired_at, v_revoked_at),
      last_seen_at = v_revoked_at
  where boot.controller_id = v_controller.id
    and boot.retired_at is null;

  update private.device_controllers as controller
  set status = 'revoked',
      -- Destroy the usable credential even though status is also checked.
      secret_hash = extensions.digest(extensions.gen_random_bytes(32), 'sha256'),
      pairing_code_hash = null,
      pairing_expires_at = null,
      updated_at = v_revoked_at
  where controller.id = v_controller.id;

  update private.ecosystems as ecosystem
  set active_controller_id = null,
      legacy_writes_allowed = false,
      pairing_open_until = null,
      pairing_expected_hardware_uid = null,
      pairing_expected_claim_proof = null,
      updated_at = v_revoked_at
  where ecosystem.id = v_controller.ecosystem_id;

  update public.device_control as control
  set active_controller_id = null,
      fan_target = false,
      fan_power = 0,
      led_target = false,
      led_power = 0,
      auto_mode = false,
      pump_expires_at = null,
      esp32_online = false,
      last_seen_at = null,
      heartbeat_seq = 0
  where control.id = 1;

  insert into private.controller_events (
    ecosystem_id, controller_id, event_type, actor_user_id, details
  ) values (
    v_controller.ecosystem_id,
    v_controller.id,
    'controller_revoked',
    v_actor_id,
    pg_catalog.jsonb_build_object('reason', v_reason)
  );

  return query select
    v_controller.id,
    '********' || right(v_controller.hardware_uid, 4),
    'revoked'::text,
    v_revoked_at;
end;
$function$;

revoke all on function private.controller_revoke_active_impl(text)
  from public, anon, authenticated, service_role;

create function public.controller_revoke_active(p_reason text)
returns table (
  controller_id bigint,
  hardware_uid_masked text,
  controller_status text,
  revoked_at timestamptz
)
language sql
volatile
security definer
set search_path = ''
as $function$
  select * from private.controller_revoke_active_impl($1);
$function$;

revoke all on function public.controller_revoke_active(text)
  from public, anon, authenticated, service_role;
grant execute on function public.controller_revoke_active(text)
  to authenticated;

alter table private.controller_events
  drop constraint controller_events_type;
alter table private.controller_events
  add constraint controller_events_type check (
    event_type in (
      'pairing_window_opened',
      'pairing_started',
      'controller_activated',
      'controller_replaced',
      'secure_mode_enabled',
      'strict_protocol_enabled',
      'credential_rotation_prepared',
      'credential_rotation_committed',
      'controller_revoked'
    )
  );

comment on table private.controller_credential_rotations is
  'Two-phase, hash-only ESP32 credential rotations with an idempotent commit receipt.';
comment on table private.controller_credential_rate_limits is
  'Bounded SHA-256 source and hardware quotas for the controller credential Edge endpoint.';
comment on function public.controller_credential_take_rate_limit(text, text) is
  'Service-role-only bounded source/hardware request quota for controller-credentials.';
comment on function public.controller_credential_prepare(text, text, text, text) is
  'Service-role-only authenticated old-secret preparation of a hash-only pending controller credential.';
comment on function public.controller_credential_commit(text, text, uuid, text, text) is
  'Service-role-only challenge-bound atomic promotion of a pending controller credential.';
comment on function public.controller_revoke_active(text) is
  'AAL2 administrator emergency revocation that destroys the active credential and de-energizes requested outputs.';

commit;

