(() => {
  'use strict';

  if (window.top !== window.self) {
    document.documentElement.hidden = true;
    try {
      window.top.location = window.self.location.href;
    } catch {
      // If top-level navigation is blocked, the control interface stays hidden.
    }
    return;
  }

  const query = new URLSearchParams(window.location.search);
  const isAndroidReturn = query.get('ecosphere_client') === 'android';
  const hasOAuthResult = query.has('code') || query.has('error');

  if (!isAndroidReturn || !hasOAuthResult) return;

  const callback = new URL('ecosphere://auth-callback');
  for (const key of ['code', 'error', 'error_code', 'error_description']) {
    const value = query.get(key);
    if (value !== null) callback.searchParams.set(key, value);
  }
  window.location.replace(callback.toString());
})();
