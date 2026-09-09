/* EcoSphere · streaming resampler. No microphone recordings are retained. */
class EcoSpherePCM extends AudioWorkletProcessor {
  constructor() {
    super();
    this.ratio = sampleRate / 16000;
    this.phase = 0;
    this.samples = [];
    this.chunk = [];
  }
  process(inputs) {
    const input = inputs[0]?.[0];
    if (!input) return true;
    for (const sample of input) this.samples.push(sample);
    while (this.phase + 1 < this.samples.length) {
      const i = Math.floor(this.phase);
      const fraction = this.phase - i;
      const value = Math.max(-1, Math.min(1, this.samples[i] * (1 - fraction) + this.samples[i + 1] * fraction));
      this.chunk.push(Math.round(value * (value < 0 ? 32768 : 32767)));
      this.phase += this.ratio;
      if (this.chunk.length === 320) {
        const output = Int16Array.from(this.chunk);
        this.port.postMessage(output, [output.buffer]);
        this.chunk = [];
      }
    }
    const consumed = Math.min(Math.floor(this.phase), this.samples.length);
    this.samples.splice(0, consumed);
    this.phase -= consumed;
    return true;
  }
}
registerProcessor('ecosphere-pcm', EcoSpherePCM);
