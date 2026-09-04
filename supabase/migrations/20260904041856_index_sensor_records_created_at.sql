create index if not exists sensor_records_created_at_desc_idx
  on public.sensor_records (created_at desc);

analyze public.sensor_records;
