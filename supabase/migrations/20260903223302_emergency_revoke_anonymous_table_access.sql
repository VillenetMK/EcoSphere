update private.ecosystems
set legacy_writes_allowed = false,
    updated_at = now()
where legacy_writes_allowed;

alter table private.ecosystems
  alter column legacy_writes_allowed set default false;

drop policy if exists "allow_legacy_select_device_control"
  on public.device_control;
drop policy if exists "allow_legacy_update_device_control"
  on public.device_control;
drop policy if exists "allow_legacy_select_sensor_records"
  on public.sensor_records;
drop policy if exists "allow_legacy_insert_sensor_records"
  on public.sensor_records;

revoke all on table public.device_control from public, anon;
revoke all on table public.sensor_records from public, anon;
revoke all on table public.sensor_history_months from public, anon;
revoke execute on function public.legacy_controller_writes_allowed()
  from public, anon;
