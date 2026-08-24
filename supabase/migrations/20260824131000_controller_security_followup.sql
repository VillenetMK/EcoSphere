create index controller_events_actor_user_id_idx
  on private.controller_events (actor_user_id)
  where actor_user_id is not null;
create index device_controllers_activated_by_idx
  on private.device_controllers (activated_by)
  where activated_by is not null;

create policy "deny_direct_ecosystem_access"
  on private.ecosystems
  for all
  to public
  using (false)
  with check (false);
create policy "deny_direct_controller_access"
  on private.device_controllers
  for all
  to public
  using (false)
  with check (false);
create policy "deny_direct_controller_event_access"
  on private.controller_events
  for all
  to public
  using (false)
  with check (false);

drop policy if exists "admin_mfa_required_sensor_records" on public.sensor_records;
create policy "admin_mfa_required_sensor_records"
  on public.sensor_records
  as restrictive
  for select
  to authenticated
  using (
    not exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role = 'admin'
    )
    or (select auth.jwt())->>'aal' = 'aal2'
  );

drop policy if exists "admin_mfa_required_device_control_read" on public.device_control;
create policy "admin_mfa_required_device_control_read"
  on public.device_control
  as restrictive
  for select
  to authenticated
  using (
    not exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role = 'admin'
    )
    or (select auth.jwt())->>'aal' = 'aal2'
  );

drop policy if exists "admin_mfa_required_device_control_update" on public.device_control;
create policy "admin_mfa_required_device_control_update"
  on public.device_control
  as restrictive
  for update
  to authenticated
  using (
    not exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role = 'admin'
    )
    or (select auth.jwt())->>'aal' = 'aal2'
  )
  with check (
    not exists (
      select 1
      from private.user_profiles p
      where p.user_id = (select auth.uid())
        and p.status = 'approved'
        and p.role = 'admin'
    )
    or (select auth.jwt())->>'aal' = 'aal2'
  );
