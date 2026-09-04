create or replace view public.sensor_history_months
with (security_invoker = true)
as
select
  to_char(created_at at time zone 'America/Lima', 'YYYY-MM') as month_key,
  min(created_at) as first_record,
  max(created_at) as last_record,
  count(*)::bigint as record_count
from public.sensor_records
group by 1;

grant select on public.sensor_history_months to anon, authenticated;

notify pgrst, 'reload schema';
