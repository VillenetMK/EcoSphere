(() => {
  'use strict';

  const query = new URLSearchParams(window.location.search);
  const fragment = new URLSearchParams(window.location.hash.replace(/^#/, ''));
  const isAndroidReturn = query.get('ecosphere_client') === 'android';
  const hasOAuthResult =
    query.has('code') ||
    query.has('error') ||
    fragment.has('access_token') ||
    fragment.has('error');

  if (!isAndroidReturn || !hasOAuthResult) return;

  query.delete('ecosphere_client');
  const callback = new URL('ecosphere://auth-callback');
  query.forEach((value, key) => callback.searchParams.append(key, value));
  callback.hash = window.location.hash;
  window.location.replace(callback.toString());
})();
