/* EcoSphere · Copyright (c) 2026 Gabriel Enrique Villenet Montero. */

// The parent owns the Supabase session. The isolated assistant receives only
// operation results; its URL, storage and messages never contain the user JWT.
export function createHeverAssistant({ button, host, request, onRevoked = () => {} }) {
  let generation = 0;
  let allowed = false;
  let frame = null;
  let pending = new Set();
  const origin = location.origin;

  function close() {
    if (!frame) return;
    generation += 1;
    pending.clear();
    if (frame) {
      frame.contentWindow?.postMessage({ channel: 'ecosphere-ai', type: 'stop' }, origin);
      frame.remove();
    }
    frame = null;
    host.replaceChildren();
  }

  function reset() {
    generation += 1;
    close();
    allowed = false;
    button.hidden = true;
  }

  async function checkAccess() {
    reset();
    const current = generation;
    try {
      const result = await request('access');
      if (current !== generation || result?.allowed !== true) return;
      allowed = true;
      button.hidden = false;
    } catch (_) {
      // The server must confirm an eligible account and active session.
    }
  }

  function open() {
    if (!allowed) return false;
    if (frame) return true;
    frame = document.createElement('iframe');
    frame.title = 'EcoSphere · Asistente de voz';
    frame.className = 'hever-ai-frame';
    frame.src = './hever-ai/index.html';
    frame.allow = 'microphone';
    frame.referrerPolicy = 'no-referrer';
    host.appendChild(frame);
    return true;
  }

  async function handleMessage(event) {
    const message = event.data;
    if (event.origin !== origin || !frame || event.source !== frame.contentWindow || !allowed) return;
    if (message?.channel !== 'ecosphere-ai' || message.type !== 'request') return;
    if (!['data', 'token'].includes(message.action) || typeof message.id !== 'string' || message.id.length > 80) return;
    if (pending.has(message.id) || pending.size >= 4) return;
    pending.add(message.id);
    const current = generation;
    const recipient = frame.contentWindow;
    try {
      const result = await request(message.action);
      if (current !== generation || !allowed || recipient !== frame?.contentWindow) return;
      recipient.postMessage({ channel: 'ecosphere-ai', type: 'response', id: message.id, result }, origin);
    } catch (error) {
      if (current !== generation || recipient !== frame?.contentWindow) return;
      if (error?.status === 401 || error?.status === 403) {
        reset();
        onRevoked();
        return;
      }
      recipient.postMessage({ channel: 'ecosphere-ai', type: 'response', id: message.id,
        error: error?.status === 429 ? 'Espera al menos 30 segundos antes de iniciar otra conversación. Si continúa, se alcanzó el límite de sesiones de esta hora.' : 'No se pudo completar la consulta. Intenta nuevamente.' }, origin);
    } finally {
      if (current === generation) pending.delete(message.id);
    }
  }

  window.addEventListener('message', handleMessage);
  return { checkAccess, open, close, reset, isAllowed: () => allowed };
}
