alter table public.device_control
  add column if not exists fan_power integer not null default 0,
  add column if not exists led_power integer not null default 0,
  add column if not exists heartbeat_seq bigint not null default 0;

alter table public.device_control
  drop constraint if exists device_control_fan_power_check,
  add constraint device_control_fan_power_check check (fan_power between 0 and 100),
  drop constraint if exists device_control_led_power_check,
  add constraint device_control_led_power_check check (led_power between 0 and 100);

alter table public.sensor_records
  add column if not exists fan_power integer,
  add column if not exists led_power integer;

alter table public.sensor_records
  drop constraint if exists sensor_records_fan_power_check,
  add constraint sensor_records_fan_power_check check (fan_power is null or fan_power between 0 and 100),
  drop constraint if exists sensor_records_led_power_check,
  add constraint sensor_records_led_power_check check (led_power is null or led_power between 0 and 100);

update public.device_control
set fan_power = case when fan_target then 100 else 0 end,
    led_power = case when led_target then 100 else 0 end,
    esp32_online = false,
    last_seen_at = null;

create or replace function public.set_esp32_last_seen()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();

  if new.heartbeat_seq is distinct from old.heartbeat_seq then
    new.last_seen_at = now();
    new.esp32_online = true;
  end if;

  return new;
end;
$$;

drop trigger if exists trg_esp32_last_seen on public.device_control;

create trigger trg_esp32_last_seen
before update on public.device_control
for each row
execute function public.set_esp32_last_seen();
