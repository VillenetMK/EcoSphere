/* Ecosphere · orbe reactivo al sonido (Three.js) -------------------------
 * Una esfera de ruido que respira en reposo y se deforma / cambia de color
 * con el volumen y las bandas de frecuencia del audio.
 *   modo: "reposo" | "escuchando" | "hablando" | "pensando"
 *   nivel:  0..1  (volumen general)
 *   bandas: { grave, medio, agudo }  cada uno 0..1
 * -------------------------------------------------------------------- */
(function (global) {
  "use strict";

  const VERT = `
    uniform float uTime;
    uniform float uNivel;
    uniform float uGrave;
    uniform float uMedio;
    uniform float uAgudo;
    uniform float uRespira;
    varying float vRuido;
    varying vec3 vNormal;
    varying float vEnergia;

    // Simplex noise 3D (Ashima Arts, MIT)
    vec4 permute(vec4 x){return mod(((x*34.0)+1.0)*x,289.0);}
    vec4 taylorInvSqrt(vec4 r){return 1.79284291400159-0.85373472095314*r;}
    float snoise(vec3 v){
      const vec2 C=vec2(1.0/6.0,1.0/3.0); const vec4 D=vec4(0.0,0.5,1.0,2.0);
      vec3 i=floor(v+dot(v,C.yyy)); vec3 x0=v-i+dot(i,C.xxx);
      vec3 g=step(x0.yzx,x0.xyz); vec3 l=1.0-g; vec3 i1=min(g.xyz,l.zxy); vec3 i2=max(g.xyz,l.zxy);
      vec3 x1=x0-i1+C.xxx; vec3 x2=x0-i2+C.yyy; vec3 x3=x0-D.yyy;
      i=mod(i,289.0);
      vec4 p=permute(permute(permute(i.z+vec4(0.0,i1.z,i2.z,1.0))+i.y+vec4(0.0,i1.y,i2.y,1.0))+i.x+vec4(0.0,i1.x,i2.x,1.0));
      float n_=1.0/7.0; vec3 ns=n_*D.wyz-D.xzx;
      vec4 j=p-49.0*floor(p*ns.z*ns.z);
      vec4 x_=floor(j*ns.z); vec4 y_=floor(j-7.0*x_);
      vec4 x=x_*ns.x+ns.yyyy; vec4 y=y_*ns.x+ns.yyyy; vec4 h=1.0-abs(x)-abs(y);
      vec4 b0=vec4(x.xy,y.xy); vec4 b1=vec4(x.zw,y.zw);
      vec4 s0=floor(b0)*2.0+1.0; vec4 s1=floor(b1)*2.0+1.0; vec4 sh=-step(h,vec4(0.0));
      vec4 a0=b0.xzyw+s0.xzyw*sh.xxyy; vec4 a1=b1.xzyw+s1.xzyw*sh.zzww;
      vec3 p0=vec3(a0.xy,h.x); vec3 p1=vec3(a0.zw,h.y); vec3 p2=vec3(a1.xy,h.z); vec3 p3=vec3(a1.zw,h.w);
      vec4 norm=taylorInvSqrt(vec4(dot(p0,p0),dot(p1,p1),dot(p2,p2),dot(p3,p3)));
      p0*=norm.x; p1*=norm.y; p2*=norm.z; p3*=norm.w;
      vec4 m=max(0.6-vec4(dot(x0,x0),dot(x1,x1),dot(x2,x2),dot(x3,x3)),0.0); m=m*m;
      return 42.0*dot(m*m,vec4(dot(p0,x0),dot(p1,x1),dot(p2,x2),dot(p3,x3)));
    }

    void main(){
      float t = uTime;
      float base = snoise(normal * 1.45 + vec3(t*.22, -t*.16, t*.12)) * .16;
      float masa = snoise(normal * 2.1 + vec3(-t*.42, t*.28, t*.18)) * (.045 + uGrave*.3);
      float pliegue = snoise(normal * 3.7 + vec3(t*.62, -t*.38, t*.46)) * (.025 + uMedio*.2);
      float textura = snoise(normal * 6.2 - vec3(t*.9, t*.55, -t*.72)) * (.014 + uAgudo*.105);
      float expansion = uRespira*.035 + uNivel*.17 + uGrave*.055;
      float desff = base + masa + pliegue + textura + expansion;
      vRuido = desff - expansion;
      vEnergia = clamp(uNivel*.48 + uGrave*.28 + uMedio*.18 + uAgudo*.12, 0.0, 1.0);
      vNormal = normalize(normalMatrix * normal);
      vec3 pos = position + normal * desff;
      gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
    }`;

  const FRAG = `
    uniform vec3 uColorA;
    uniform vec3 uColorB;
    uniform float uNivel;
    uniform float uGrave;
    uniform float uMedio;
    uniform float uAgudo;
    varying float vRuido;
    varying vec3 vNormal;
    varying float vEnergia;
    void main(){
      float mezcla = smoothstep(-.21, .25, vRuido + uMedio*.06);
      vec3 col = mix(uColorA, uColorB, mezcla);
      // Las bandas aportan carácter: cálido en graves, vivo en medios y frío en agudos.
      col += vec3(.18, .055, .015) * uGrave * (.35 + mezcla);
      col += vec3(.02, .16, .08) * uMedio * mezcla;
      col += vec3(.025, .13, .24) * uAgudo * (1.0 - mezcla*.35);
      float f = pow(1.0 - abs(vNormal.z), 2.25);
      col += f * mix(.2, .76, vEnergia);
      col += vRuido * .13 + uNivel*.025;
      gl_FragColor = vec4(col, 1.0);
    }`;

  const PALETA = {
    reposo:     { a: [0x06 / 255, 0x2c / 255, 0x22 / 255], b: [0x43 / 255, 0xd8 / 255, 0x9e / 255] },
    escuchando: { a: [0x05 / 255, 0x28 / 255, 0x3c / 255], b: [0x69 / 255, 0xcb / 255, 0xfa / 255] },
    hablando:   { a: [0x08 / 255, 0x35 / 255, 0x28 / 255], b: [0x8c / 255, 0xf0 / 255, 0xc6 / 255] },
    pensando:   { a: [0x32 / 255, 0x29 / 255, 0x11 / 255], b: [0xf2 / 255, 0xc9 / 255, 0x6c / 255] },
  };

  function lerp(a, b, t) { return a + (b - a) * t; }
  function lerp3(a, b, t) { return [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)]; }
  function limitar(n) { return Math.max(0, Math.min(1, Number.isFinite(n) ? n : 0)); }
  function acercar(actual, destino, rapidez, dt) {
    return lerp(actual, destino, 1 - Math.exp(-rapidez * dt));
  }

  class Visualizador {
    constructor(canvas) {
      if (!global.THREE) throw new Error("Three.js no está disponible");
      this.canvas = canvas;
      this.modo = "reposo";
      this.nivel = 0;
      this.bandas = { grave: 0, medio: 0, agudo: 0 };
      this._n = 0; this._g = 0; this._m = 0; this._ag = 0;
      this._colA = PALETA.reposo.a.slice();
      this._colB = PALETA.reposo.b.slice();
      this._reducirMovimiento = global.matchMedia("(prefers-reduced-motion: reduce)").matches;

      const T = global.THREE;
      this.renderer = new T.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: "high-performance" });
      const dprMax = navigator.hardwareConcurrency && navigator.hardwareConcurrency <= 4 ? 1.35 : 1.75;
      this.renderer.setPixelRatio(Math.min(global.devicePixelRatio || 1, dprMax));

      this.scene = new T.Scene();
      this.camera = new T.PerspectiveCamera(45, 1, 0.1, 100);
      this.camera.position.set(0, 0, 4.4);

      this.uniforms = {
        uTime: { value: 0 },
        uNivel: { value: 0 },
        uGrave: { value: 0 },
        uMedio: { value: 0 },
        uAgudo: { value: 0 },
        uRespira: { value: 0 },
        uColorA: { value: new T.Color().fromArray(this._colA) },
        uColorB: { value: new T.Color().fromArray(this._colB) },
      };

      // La silueta viene del shader; una subdivisión moderada evita trabajo de GPU innecesario.
      const detalle = navigator.hardwareConcurrency && navigator.hardwareConcurrency <= 4 ? 4 : 5;
      const geo = new T.IcosahedronGeometry(1.25, detalle);
      const mat = new T.ShaderMaterial({ uniforms: this.uniforms, vertexShader: VERT, fragmentShader: FRAG });
      this.orbe = new T.Mesh(geo, mat);
      this.scene.add(this.orbe);

      // halo de partículas
      const N = navigator.hardwareConcurrency && navigator.hardwareConcurrency <= 4 ? 380 : 560;
      const pos = new Float32Array(N * 3);
      for (let i = 0; i < N; i++) {
        const r = 1.9 + Math.random() * 1.4;
        const th = Math.random() * Math.PI * 2;
        const ph = Math.acos(2 * Math.random() - 1);
        pos[i * 3] = r * Math.sin(ph) * Math.cos(th);
        pos[i * 3 + 1] = r * Math.sin(ph) * Math.sin(th);
        pos[i * 3 + 2] = r * Math.cos(ph);
      }
      const pgeo = new T.BufferGeometry();
      pgeo.setAttribute("position", new T.BufferAttribute(pos, 3));
      this.halo = new T.Points(
        pgeo,
        new T.PointsMaterial({ color: 0x57c7ff, size: 0.02, transparent: true, opacity: 0.5, depthWrite: false })
      );
      this.scene.add(this.halo);

      this._clock = new T.Clock();
      this._onResize = () => this.redimensionar();
      this._resizeObserver = global.ResizeObserver ? new ResizeObserver(this._onResize) : null;
      if (this._resizeObserver) this._resizeObserver.observe(this.canvas.parentElement);
      else global.addEventListener("resize", this._onResize);
      this.redimensionar();
      this._bucle = this._bucle.bind(this);
      this._raf = requestAnimationFrame(this._bucle);
    }

    setModo(m) { if (PALETA[m]) this.modo = m; }

    setAudio(nivel, bandas) {
      this.nivel = limitar(nivel);
      if (bandas) {
        this.bandas = {
          grave: limitar(bandas.grave),
          medio: limitar(bandas.medio),
          agudo: limitar(bandas.agudo),
        };
      }
    }

    redimensionar() {
      const w = this.canvas.clientWidth || this.canvas.parentElement.clientWidth;
      const h = this.canvas.clientHeight || this.canvas.parentElement.clientHeight;
      this.renderer.setSize(w, h, false);
      this.camera.aspect = w / Math.max(1, h);
      this.camera.updateProjectionMatrix();
    }

    _bucle() {
      const dt = Math.min(this._clock.getDelta(), 0.05);
      // Ataque ágil y caída reposada para que la voz se sienta fluida, no nerviosa.
      this._n = acercar(this._n, this.nivel, this.nivel > this._n ? 13 : 5.5, dt);
      this._g = acercar(this._g, this.bandas.grave, 8, dt);
      this._m = acercar(this._m, this.bandas.medio, 10, dt);
      this._ag = acercar(this._ag, this.bandas.agudo, 12, dt);

      const pal = PALETA[this.modo];
      const vel = 1 - Math.exp(-(this.modo === "reposo" ? 2.3 : 7) * dt);
      this._colA = lerp3(this._colA, pal.a, vel);
      this._colB = lerp3(this._colB, pal.b, vel);

      this.uniforms.uTime.value += dt * (this._reducirMovimiento ? 0.18 : 1 + this._n * 1.65);
      this.uniforms.uNivel.value = this._n;
      this.uniforms.uGrave.value = this._g;
      this.uniforms.uMedio.value = this._m;
      this.uniforms.uAgudo.value = this._ag;
      const t = this.uniforms.uTime.value;
      this.uniforms.uRespira.value = this._reducirMovimiento ? 0.35 : (Math.sin(t * 0.92) + Math.sin(t * 0.41 + 1.4) * 0.38 + 1.38) / 2.76;
      this.uniforms.uColorA.value.fromArray(this._colA);
      this.uniforms.uColorB.value.fromArray(this._colB);

      const respiracion = this.uniforms.uRespira.value;
      const escala = 1 + this._n * 0.1 + (this.modo === "reposo" ? (respiracion - 0.5) * 0.025 : 0);
      this.orbe.scale.setScalar(escala);
      if (!this._reducirMovimiento) {
        this.orbe.rotation.y += dt * (0.055 + this._m * 0.16);
        this.orbe.rotation.x = Math.sin(t * 0.18) * 0.045;
        this.halo.rotation.y -= dt * (0.035 + this._ag * 0.32);
        this.halo.rotation.x += dt * 0.012;
      }
      this.halo.material.opacity = 0.22 + respiracion * 0.08 + this._n * 0.48;
      this.halo.material.size = 0.016 + this._ag * 0.018;

      if (!document.hidden) this.renderer.render(this.scene, this.camera);
      this._raf = requestAnimationFrame(this._bucle);
    }

    destruir() {
      cancelAnimationFrame(this._raf);
      if (this._resizeObserver) this._resizeObserver.disconnect();
      else global.removeEventListener("resize", this._onResize);
      this.orbe.geometry.dispose();
      this.orbe.material.dispose();
      this.halo.geometry.dispose();
      this.halo.material.dispose();
      this.renderer.dispose();
    }
  }

  global.EcosphereVisualizer = { Visualizador };
})(window);
