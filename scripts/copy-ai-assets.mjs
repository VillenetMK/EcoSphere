/* EcoSphere · Copyright (c) 2026 Gabriel Enrique Villenet Montero. */
import { copyFile, mkdir } from 'node:fs/promises';
const source = new URL('../webApp/hever-ai/', import.meta.url);
const output = new URL('../dist/hever-ai/', import.meta.url);
await mkdir(output, { recursive: true });
for (const name of ['index.html', 'styles.css', 'pcm-worklet.js']) {
  await copyFile(new URL(name, source), new URL(name, output));
}
