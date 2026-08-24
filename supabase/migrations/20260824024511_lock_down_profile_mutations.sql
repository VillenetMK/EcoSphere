revoke insert, update on table private.user_profiles from authenticated;

drop policy if exists "users_insert_own_private_profile" on private.user_profiles;
drop policy if exists "users_update_own_private_identity" on private.user_profiles;

alter function public.complete_user_profile(text, text, text, text)
  security definer;

revoke all on function public.complete_user_profile(text, text, text, text)
  from public, anon;
grant execute on function public.complete_user_profile(text, text, text, text)
  to authenticated;
