/* EcoSphere · Copyright (c) 2026 Gabriel Enrique Villenet Montero. */
import * as THREE from 'three';
import './visualizer.js';
import './imagery.js';
import { AudioIO } from './audio.js';
import { Panel } from './panel.js';
window.THREE = THREE;

const $ = id => document.getElementById(id);
const requests = new Map();
let requestCounter = 0;
function request(action) {
  if (parent === window) return Promise.reject(new Error('Abre el asistente desde tu cuenta de EcoSphere.'));
  const id = `${Date.now()}-${++requestCounter}`;
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => { requests.delete(id); reject(new Error('La consulta tardó demasiado. Intenta nuevamente.')); }, 35000);
    requests.set(id, { resolve, reject, timeout });
    parent.postMessage({ channel: 'ecosphere-ai', type: 'request', action, id }, location.origin);
  });
}
window.addEventListener('message', event => {
  if (event.origin !== location.origin || event.source !== parent || event.data?.channel !== 'ecosphere-ai') return;
  if (event.data.type === 'stop') { destroy(); return; }
  if (event.data.type !== 'response') return;
  const pending = requests.get(event.data.id);
  if (!pending) return;
  clearTimeout(pending.timeout);
  requests.delete(event.data.id);
  if (event.data.error) pending.reject(new Error(event.data.error)); else pending.resolve(event.data.result);
});

const audio = new AudioIO();
const panel = new Panel();
const gallery = new window.EcosphereImagery.Galeria({ contenedor: $('ilustracion-svg'), pie: $('ilustracion-pie'), chip: $('chip-contexto'), chipIcono: $('chip-icono'), chipTexto: $('chip-texto') });
let visual = null;
try { visual = new window.EcosphereVisualizer.Visualizador($('lienzo-orbe')); } catch (_) { /* Voice remains available without WebGL. */ }
let alive = true;
let active = false;
let generation = 0;
let socket = null;
let setupDone = false;
let thinking = false;
let closedTurn = true;
let setupTimer = null;
let sessionTimer = null;
let pollBusy = false;
const cancelledTools = new Set();

function status(message) { $('estado').textContent = message; }
function notice(message) { $('aviso').textContent = message; $('aviso').hidden = false; }
function send(message) {
  if (!socket || socket.readyState !== WebSocket.OPEN) return false;
  if (socket.bufferedAmount > 1_000_000) { stop('La conexión de audio es lenta. Inicia otra conversación.'); return false; }
  socket.send(JSON.stringify(message)); return true;
}
function transcript(who, text) {
  if (!text) return;
  if (closedTurn) {
    for (const id of ['linea-usuario', 'linea-ecosphere']) { $(id).textContent = ''; $(id).hidden = true; }
    closedTurn = false;
  }
  const node = $(who === 'user' ? 'linea-usuario' : 'linea-ecosphere');
  node.hidden = false;
  node.textContent = (node.textContent + text).slice(-12000);
  gallery.reaccionar(node.textContent, who === 'user' ? '🗣️' : '🌱');
}
async function refreshPanel() {
  if (!alive || pollBusy) return;
  pollBusy = true;
  try { const data = await request('data'); if (alive) panel.render(data); }
  catch (_) { if (alive) panel.invalidate(); }
  finally { pollBusy = false; }
}
async function answerTools(calls, current) {
  thinking = true; status('Consultando las lecturas reales…');
  const responses = [];
  for (const call of calls) {
    if (typeof call.id !== 'string' || typeof call.name !== 'string') continue;
    let result;
    if (call.name !== 'consultar_biohuerto') result = { error: 'Herramienta no disponible. El asistente solo consulta datos.' };
    else {
      try { result = await request('data'); if (alive) panel.render(result); }
      catch (_) {
        if (alive) panel.invalidate();
        result = { error: 'No hay datos verificables disponibles en este momento. No inventes lecturas.' };
      }
    }
    if (current !== generation || !active) return;
    if (!cancelledTools.has(call.id)) responses.push({ id: call.id, name: call.name, response: result });
  }
  if (responses.length && current === generation) send({ toolResponse: { functionResponses: responses } });
  thinking = false;
}
function handleServer(message, current) {
  if (!active || current !== generation) return;
  if (message.setupComplete) {
    setupDone = true; clearTimeout(setupTimer); status('Escuchando…');
    audio.onAudio = data => { if (setupDone && active) send({ realtimeInput: { audio: { mimeType: 'audio/pcm;rate=16000', data } } }); };
  }
  if (message.error) { stop('Gemini no pudo iniciar la conversación. Intenta nuevamente.'); return; }
  const content = message.serverContent;
  if (content) {
    if (content.interrupted) { audio.interrupt(); thinking = false; closedTurn = true; }
    transcript('user', content.inputTranscription?.text);
    transcript('assistant', content.outputTranscription?.text);
    for (const part of content.modelTurn?.parts || []) {
      const inline = part.inlineData;
      if (inline?.data && inline.mimeType?.startsWith('audio/pcm')) {
        const rate = Number(/rate=(\d+)/.exec(inline.mimeType)?.[1] || 24000);
        if (rate >= 8000 && rate <= 48000) audio.play(inline.data, rate);
        status('EcoSphere responde…');
      }
    }
    if (content.turnComplete) { closedTurn = true; thinking = false; status('Escuchando…'); }
  }
  for (const id of message.toolCallCancellation?.ids || []) cancelledTools.add(id);
  if (message.toolCall?.functionCalls) answerTools(message.toolCall.functionCalls.slice(0, 4), current).catch(() => stop('No se pudo consultar el huerto.'));
  if (message.goAway) status('La sesión está por finalizar. Podrás iniciar otra conversación.');
}
async function start() {
  if (active) return;
  active = true; setupDone = false; closedTurn = true; cancelledTools.clear();
  const current = ++generation;
  $('aviso').hidden = true;
  $('btn-voz').setAttribute('aria-pressed', 'true');
  $('btn-voz').setAttribute('aria-label', 'Detener conversación por voz');
  for (const id of ['linea-usuario', 'linea-ecosphere']) { $(id).textContent = ''; $(id).hidden = true; }
  status('Preparando el micrófono…');
  try {
    if (!await audio.start() || !active || current !== generation) return;
    status('Conectando con Gemini Live…');
    const credentials = await request('token');
    if (!active || current !== generation) return;
    if (typeof credentials?.token !== 'string' || !credentials.token.startsWith('auth_tokens/') || !/^gemini-[a-z0-9.-]+$/.test(credentials.model || '')) throw new Error('No se pudo obtener una sesión de voz válida.');
    const remaining = Date.parse(credentials.expiresAt) - Date.now();
    if (!Number.isFinite(remaining) || remaining <= 5000) throw new Error('La sesión de voz venció. Intenta nuevamente.');
    const url = 'wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained?access_token=' + encodeURIComponent(credentials.token);
    socket = new WebSocket(url);
    const liveSocket = socket;
    socket.onopen = () => { if (current === generation) send({ setup: { model: `models/${credentials.model}` } }); };
    let messages = Promise.resolve();
    socket.onmessage = event => {
      messages = messages.then(async () => {
        const raw = event.data instanceof Blob ? await event.data.text() : event.data instanceof ArrayBuffer ? new TextDecoder().decode(event.data) : event.data;
        if (typeof raw !== 'string' || raw.length > 4_000_000) return;
        handleServer(JSON.parse(raw), current);
      }).catch(() => { if (current === generation) stop('Se interrumpió el audio. Inicia nuevamente la conversación.'); });
    };
    socket.onerror = () => { if (current === generation) stop('No se pudo conectar con Gemini Live. Intenta nuevamente.'); };
    socket.onclose = () => { if (current === generation && socket === liveSocket) stop('Conversación finalizada. Toca para iniciar otra.'); };
    setupTimer = setTimeout(() => { if (current === generation && !setupDone) stop('Gemini tardó demasiado en responder. Intenta nuevamente.'); }, 20000);
    sessionTimer = setTimeout(() => { if (current === generation) stop('Sesión completada. Toca para iniciar otra conversación.'); }, Math.min(9.5 * 60000, remaining - 1000));
  } catch (error) {
    if (current === generation) stop(error?.name === 'NotAllowedError' ? 'Permite el uso del micrófono para conversar.' : error.message || 'No se pudo iniciar el audio.');
  }
}
function stop(message = 'Toca para hablar con EcoSphere') {
  generation++; active = false; setupDone = false; thinking = false;
  clearTimeout(setupTimer); clearTimeout(sessionTimer);
  audio.stop();
  if (socket) {
    const old = socket; socket = null;
    old.onopen = old.onmessage = old.onerror = old.onclose = null;
    if (old.readyState === WebSocket.OPEN) { try { old.send(JSON.stringify({ realtimeInput: { audioStreamEnd: true } })); } catch (_) {} }
    try { old.close(); } catch (_) {}
  }
  $('btn-voz').setAttribute('aria-pressed', 'false');
  $('btn-voz').setAttribute('aria-label', 'Iniciar conversación por voz');
  status(message);
}
function destroy() {
  if (!alive) return;
  alive = false; stop(); clearInterval(pollTimer); clearInterval(freshnessTimer);
  visual?.destruir();
  for (const pending of requests.values()) { clearTimeout(pending.timeout); pending.reject(new Error('Sesión finalizada')); }
  requests.clear();
  panel.history = {};
  $('tarjetas').replaceChildren(); $('sistema-chips').replaceChildren();
  $('linea-usuario').textContent = $('linea-ecosphere').textContent = '';
}
$('btn-voz').addEventListener('click', () => active ? stop() : start());
window.addEventListener('pagehide', destroy);
document.addEventListener('visibilitychange', () => { if (document.hidden && active) stop(); });
window.addEventListener('resize', () => { if (alive) panel.draw(); });
function animate() {
  if (!alive) return;
  const measure = audio.measure();
  const mode = audio.reproduciendo ? 'hablando' : thinking ? 'pensando' : active && measure.nivel > .06 ? 'escuchando' : 'reposo';
  if (visual) { visual.setAudio(measure.nivel, measure); visual.setModo(mode); }
  $('controles').dataset.modo = mode;
  requestAnimationFrame(animate);
}
animate();
refreshPanel();
const pollTimer = setInterval(refreshPanel, 15000);
const freshnessTimer = setInterval(() => { if (alive) panel.updateFreshness(); }, 1000);
if (parent === window) { $('btn-voz').disabled = true; notice('Abre este asistente desde tu cuenta en EcoSphere.'); }
