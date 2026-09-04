alter function public.set_updated_at() set search_path = pg_catalog;
alter function public.set_esp32_last_seen() set search_path = pg_catalog;

revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
revoke execute on function public.set_updated_at() from public, anon, authenticated;
revoke execute on function public.set_esp32_last_seen() from public, anon, authenticated;

revoke all privileges on table public.sensor_records from anon, authenticated;
grant select, insert on table public.sensor_records to anon, authenticated;

revoke all privileges on table public.device_control from anon, authenticated;
grant select, update on table public.device_control to anon, authenticated;

revoke all privileges on table public.sensor_history_months from anon, authenticated;
grant select on table public.sensor_history_months to anon, authenticated;

alter default privileges for role postgres in schema public
  revoke all privileges on tables from anon, authenticated;
alter default privileges for role postgres in schema public
  revoke all privileges on sequences from anon, authenticated;
alter default privileges for role postgres in schema public
  revoke execute on functions from public, anon, authenticated;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'sensor_records_temperature_range'
      and conrelid = 'public.sensor_records'::regclass
  ) then
    alter table public.sensor_records
      add constraint sensor_records_temperature_range
      check (temperature is null or (temperature >= -40 and temperature <= 85)) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'sensor_records_air_humidity_range'
      and conrelid = 'public.sensor_records'::regclass
  ) then
    alter table public.sensor_records
      add constraint sensor_records_air_humidity_range
      check (air_humidity is null or (air_humidity >= 0 and air_humidity <= 100)) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'sensor_records_soil_humidity_range'
      and conrelid = 'public.sensor_records'::regclass
  ) then
    alter table public.sensor_records
      add constraint sensor_records_soil_humidity_range
      check (soil_humidity is null or (soil_humidity >= 0 and soil_humidity <= 100)) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'sensor_records_light_lux_range'
      and conrelid = 'public.sensor_records'::regclass
  ) then
    alter table public.sensor_records
      add constraint sensor_records_light_lux_range
      check (light_lux is null or (light_lux >= 0 and light_lux <= 200000)) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'sensor_records_water_level_values'
      and conrelid = 'public.sensor_records'::regclass
  ) then
    alter table public.sensor_records
      add constraint sensor_records_water_level_values
      check (water_level is null or lower(water_level) in ('low', 'high')) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'device_control_pump_duration_range'
      and conrelid = 'public.device_control'::regclass
  ) then
    alter table public.device_control
      add constraint device_control_pump_duration_range
      check (pump_duration_ms between 100 and 10000) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'device_control_pump_request_nonnegative'
      and conrelid = 'public.device_control'::regclass
  ) then
    alter table public.device_control
      add constraint device_control_pump_request_nonnegative
      check (pump_request >= 0) not valid;
  end if;

  if not exists (
    select 1 from pg_constraint
    where conname = 'device_control_heartbeat_nonnegative'
      and conrelid = 'public.device_control'::regclass
  ) then
    alter table public.device_control
      add constraint device_control_heartbeat_nonnegative
      check (heartbeat_seq >= 0) not valid;
  end if;
end
$$;

alter table public.sensor_records validate constraint sensor_records_temperature_range;
alter table public.sensor_records validate constraint sensor_records_air_humidity_range;
alter table public.sensor_records validate constraint sensor_records_soil_humidity_range;
alter table public.sensor_records validate constraint sensor_records_light_lux_range;
alter table public.sensor_records validate constraint sensor_records_water_level_values;
alter table public.device_control validate constraint device_control_pump_duration_range;
alter table public.device_control validate constraint device_control_pump_request_nonnegative;
alter table public.device_control validate constraint device_control_heartbeat_nonnegative;
