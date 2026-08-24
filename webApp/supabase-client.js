import { createClient } from '@supabase/supabase-js';

export const SUPABASE_URL = 'https://kslzmrddrhfyyrxyfmbw.supabase.co';
export const SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_oHQqSvres8b5l0qgcpXJ2w_9A33lfg3';

export const supabase = createClient(SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, {
  auth: {
    flowType: 'pkce',
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: true,
  },
});
