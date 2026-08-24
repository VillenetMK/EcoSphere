-- pgcrypto provides gen_random_bytes, while encode is a PostgreSQL built-in.
create or replace function public.controller_begin_pairing(
  p_hardware_uid text,
  p_device_secret text,
  p_firmware_version text default null
)
returns table (
  pairing_code text,
  expires_at timestamptz,
  controller_status text
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_hardware_uid text := upper(regexp_replace(coalesce(p_hardware_uid, ''), '[^0-9A-Fa-f]', '', 'g'));
  v_secret text := lower(trim(coalesce(p_device_secret, '')));
  v_secret_hash bytea;
  v_code_plain text := upper(pg_catalog.encode(extensions.gen_random_bytes(6), 'hex'));
  v_code_hash bytea;
  v_controller private.device_controllers%rowtype;
  v_expires_at timestamptz := now() + interval '15 minutes';
begin
  if v_hardware_uid !~ '^[0-9A-F]{12}$' then
    raise exception 'invalid controller hardware uid' using errcode = '22023';
  end if;

  if v_secret !~ '^[0-9a-f]{64}$' then
    raise exception 'invalid controller secret' using errcode = '22023';
  end if;

  if p_firmware_version is not null
     and char_length(trim(p_firmware_version)) not between 1 and 40 then
    raise exception 'invalid firmware version' using errcode = '22023';
  end if;

  v_secret_hash := extensions.digest(v_secret, 'sha256');
  v_code_hash := extensions.digest(v_code_plain, 'sha256');

  select *
    into v_controller
  from private.device_controllers c
  where c.hardware_uid = v_hardware_uid
  for update;

  if found then
    if v_controller.secret_hash <> v_secret_hash then
      raise exception 'controller identity mismatch' using errcode = '42501';
    end if;

    if v_controller.status = 'active' then
      return query
        select null::text, null::timestamptz, 'active'::text;
      return;
    end if;

    if v_controller.status = 'revoked' then
      raise exception 'controller has been revoked' using errcode = '42501';
    end if;

    update private.device_controllers
      set pairing_code_hash = v_code_hash,
          pairing_expires_at = v_expires_at,
          status = 'pending',
          firmware_version = coalesce(nullif(trim(p_firmware_version), ''), firmware_version),
          updated_at = now()
    where id = v_controller.id
    returning * into v_controller;
  else
    insert into private.device_controllers (
      ecosystem_id,
      hardware_uid,
      secret_hash,
      pairing_code_hash,
      pairing_expires_at,
      firmware_version
    )
    values (
      1,
      v_hardware_uid,
      v_secret_hash,
      v_code_hash,
      v_expires_at,
      nullif(trim(p_firmware_version), '')
    )
    returning * into v_controller;
  end if;

  insert into private.controller_events (
    ecosystem_id,
    controller_id,
    event_type,
    details
  )
  values (
    1,
    v_controller.id,
    'pairing_started',
    jsonb_build_object('firmware_version', v_controller.firmware_version)
  );

  return query
    select
      substring(v_code_plain from 1 for 4) || '-' ||
        substring(v_code_plain from 5 for 4) || '-' ||
        substring(v_code_plain from 9 for 4),
      v_expires_at,
      'pending'::text;
end;
$$;

revoke all on function public.controller_begin_pairing(text, text, text)
  from public, anon, authenticated;
grant execute on function public.controller_begin_pairing(text, text, text)
  to anon;
