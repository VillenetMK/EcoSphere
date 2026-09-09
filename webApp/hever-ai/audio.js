/* EcoSphere · Copyright (c) 2026 Gabriel Enrique Villenet Montero. */
export class AudioIO {
  constructor() {
    this.generation = 0;
    this.sources = new Set();
    this.nextOutputAt = 0;
    this.onAudio = null;
  }
  async start() {
    this.stop();
    const generation = this.generation;
    const Context = window.AudioContext || window.webkitAudioContext;
    if (!Context || !navigator.mediaDevices?.getUserMedia) throw new Error('Este navegador no admite audio. Usa Chrome o Edge actualizado.');
    // Created during the user's click so playback is permitted on mobile.
    this.output = new Context();
    this.outputMeter = this.output.createAnalyser();
    this.outputMeter.fftSize = 512;
    this.outputMeter.connect(this.output.destination);
    await this.output.resume();
    const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } });
    if (generation !== this.generation) { stream.getTracks().forEach(track => track.stop()); return false; }
    this.stream = stream;
    this.input = new Context();
    await this.input.resume();
    await this.input.audioWorklet.addModule(new URL('./pcm-worklet.js', import.meta.url));
    if (generation !== this.generation) return false;
    this.inputMeter = this.input.createAnalyser();
    this.inputMeter.fftSize = 512;
    const source = this.input.createMediaStreamSource(stream);
    source.connect(this.inputMeter);
    this.worklet = new AudioWorkletNode(this.input, 'ecosphere-pcm');
    this.worklet.port.onmessage = ({ data }) => {
      if (!this.onAudio) return;
      const bytes = new Uint8Array(data.buffer);
      let binary = '';
      for (const byte of bytes) binary += String.fromCharCode(byte);
      this.onAudio(btoa(binary));
    };
    source.connect(this.worklet);
    const silence = this.input.createGain();
    silence.gain.value = 0;
    this.worklet.connect(silence).connect(this.input.destination);
    return true;
  }
  play(data, rate = 24000) {
    if (!this.output || this.output.state === 'closed') return;
    const raw = atob(data);
    if (raw.length % 2 || raw.length > 2_000_000) return;
    const bytes = Uint8Array.from(raw, char => char.charCodeAt(0));
    const view = new DataView(bytes.buffer);
    const buffer = this.output.createBuffer(1, bytes.length / 2, rate);
    const samples = buffer.getChannelData(0);
    for (let i = 0; i < samples.length; i++) samples[i] = view.getInt16(i * 2, true) / 32768;
    if (this.nextOutputAt - this.output.currentTime > 30) this.interrupt();
    const node = this.output.createBufferSource();
    node.buffer = buffer;
    node.connect(this.outputMeter);
    this.sources.add(node);
    node.onended = () => { this.sources.delete(node); node.disconnect(); };
    node.start(Math.max(this.output.currentTime + .025, this.nextOutputAt));
    this.nextOutputAt = Math.max(this.output.currentTime + .025, this.nextOutputAt) + buffer.duration;
  }
  interrupt() {
    for (const source of this.sources) { try { source.stop(); } catch (_) {} }
    this.sources.clear();
    this.nextOutputAt = 0;
  }
  get reproduciendo() { return this.sources.size > 0; }
  measure() {
    const meter = this.reproduciendo ? this.outputMeter : this.inputMeter;
    if (!meter) return { nivel: 0, grave: 0, medio: 0, agudo: 0 };
    const values = new Uint8Array(meter.frequencyBinCount);
    meter.getByteFrequencyData(values);
    const band = (low, high) => {
      const factor = meter.fftSize / meter.context.sampleRate;
      const a = Math.floor(low * factor), b = Math.min(values.length, Math.ceil(high * factor));
      let sum = 0;
      for (let i = a; i < b; i++) sum += values[i];
      return sum / Math.max(1, b - a) / 255;
    };
    const grave = band(70, 280), medio = band(280, 2200), agudo = band(2200, 7000);
    return { nivel: Math.min(1, (grave * .42 + medio * .43 + agudo * .15) * 2), grave, medio, agudo };
  }
  stop() {
    this.generation++;
    this.onAudio = null;
    if (this.worklet) { this.worklet.port.onmessage = null; this.worklet.disconnect(); }
    this.stream?.getTracks().forEach(track => track.stop());
    this.interrupt();
    for (const context of [this.input, this.output]) {
      if (context && context.state !== 'closed') context.close().catch(() => {});
    }
    this.input = this.output = this.inputMeter = this.outputMeter = this.stream = this.worklet = null;
  }
}
