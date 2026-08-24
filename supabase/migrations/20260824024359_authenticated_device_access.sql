drop policy if exists "approved_users_read_sensor_records" on public.sensor_records;
create policy "approved_users_read_sensor_records"
  on public.sensor_records
  for select
  to authenticated
  using (
    exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
    )
  );

drop policy if exists "approved_users_read_device_control" on public.device_control;
create policy "approved_users_read_device_control"
  on public.device_control
  for select
  to authenticated
  using (
    exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
    )
  );

drop policy if exists "operators_update_device_control" on public.device_control;
create policy "operators_update_device_control"
  on public.device_control
  for update
  to authenticated
  using (
    id = 1
    and exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role in ('operator', 'admin')
    )
  )
  with check (
    id = 1
    and exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role in ('operator', 'admin')
    )
  );
