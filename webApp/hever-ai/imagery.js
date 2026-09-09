/* Ecosphere · galería de ilustraciones contextuales -----------------------
 * Muestra un SVG relacionado con el tema del que se está hablando.
 * Todo es SVG en línea: sin dependencias de red ni licencias de imágenes.
 * ---------------------------------------------------------------------- */
(function (global) {
  "use strict";

  const C = {
    hoja: "#35e0a1",
    hoja2: "#1f9d76",
    agua: "#57c7ff",
    sol: "#ffd166",
    tierra: "#8a5a3b",
    tierra2: "#5e3d28",
    trazo: "#04110d",
    critico: "#ff8179",
  };

  // Cada tema: SVG + título + palabras clave que lo disparan.
  const TEMAS = {
    general: {
      titulo: "Vista general del biohuerto",
      claves: ["huerto", "biohuerto", "general", "resumen", "hola", "estado"],
      svg: `
        <svg viewBox="0 0 320 200" xmlns="http://www.w3.org/2000/svg">
          <defs><linearGradient id="g-dome" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="${C.agua}" stop-opacity=".25"/>
            <stop offset="1" stop-color="${C.hoja}" stop-opacity=".05"/></linearGradient></defs>
          <path d="M40 150a120 70 0 0 1 240 0z" fill="url(#g-dome)" stroke="${C.hoja}" stroke-opacity=".4"/>
          <rect x="40" y="150" width="240" height="14" rx="4" fill="${C.tierra}"/>
          <g stroke="${C.hoja2}" stroke-width="4" fill="none" stroke-linecap="round">
            <path d="M110 150c0-22-14-30-24-34 14-4 24 6 24 18"/>
            <path d="M160 150c0-30 0-44 0-58M160 110c10-8 22-6 26 2-6 8-20 8-26-2zM160 122c-10-8-22-6-26 2 6 8 20 8 26-2z"/>
            <path d="M212 150c0-20 12-28 22-32-12-6-24 4-24 16"/>
          </g>
          <circle cx="250" cy="52" r="16" fill="${C.sol}"/>
        </svg>`,
    },
    temperatura: {
      titulo: "Temperatura del aire",
      claves: ["temperatura", "calor", "frío", "frio", "grados", "clima", "caluroso", "fresco"],
      svg: `
        <svg viewBox="0 0 320 200" xmlns="http://www.w3.org/2000/svg">
          <g stroke="${C.hoja2}" stroke-width="4" fill="none" stroke-linecap="round">
            <path d="M70 160c0-28-16-34-26-38 16-4 26 8 26 20"/>
            <path d="M250 160c0-24 14-32 24-36-14-6-24 6-24 18"/>
          </g>
          <rect x="150" y="40" width="20" height="96" rx="10" fill="#0e2a22" stroke="${C.agua}" stroke-opacity=".5"/>
          <circle cx="160" cy="150" r="20" fill="${C.critico}"/>
          <rect x="154" y="86" width="8" height="58" rx="4" fill="${C.critico}"/>
          <g stroke="${C.sol}" stroke-width="3" stroke-linecap="round">
            <path d="M210 60h26M214 48l22 12M214 72l22-12"/>
          </g>
          <circle cx="250" cy="48" r="12" fill="${C.sol}"/>
        </svg>`,
    },
    humedad: {
      titulo: "Humedad ambiental",
      claves: ["humedad", "húmedo", "humedo", "vapor", "rocío", "rocio", "ambiente", "aire"],
      svg: `
        <svg viewBox="0 0 320 200" xmlns="http://www.w3.org/2000/svg">
          <g fill="${C.agua}">
            <path d="M120 60c14 20 22 32 22 44a22 22 0 0 1-44 0c0-12 8-24 22-44z"/>
            <path d="M200 40c16 24 26 38 26 52a26 26 0 0 1-52 0c0-14 10-28 26-52z" opacity=".8"/>
          </g>
          <path d="M60 150h200" stroke="${C.tierra}" stroke-width="14" stroke-linecap="round"/>
          <g stroke="${C.hoja2}" stroke-width="4" fill="none" stroke-linecap="round">
            <path d="M100 150c0-24 0-36 0-48M100 112c9-7 20-5 24 2-6 7-18 7-24-2z"/>
            <path d="M220 150c0-20 0-30 0-40M220 118c-9-7-20-5-24 2 6 7 18 7 24-2z"/>
          </g>
        </svg>`,
    },
    riego: {
      titulo: "Riego y humedad del sustrato",
      claves: ["riego", "regar", "agua", "sustrato", "suelo húmedo", "bomba", "goteo", "reserva", "tanque", "nivel de agua"],
      svg: `
        <svg viewBox="0 0 320 200" xmlns="http://www.w3.org/2000/svg">
          <path d="M60 70c0-10 8-16 18-16h40l14 14h60v40H78c-10 0-18-8-18-18z" fill="#0e2a22" stroke="${C.agua}" stroke-opacity=".6"/>
          <path d="M118 92c26 8 40 20 46 40" stroke="${C.agua}" stroke-width="4" fill="none"/>
          <g fill="${C.agua}"><circle cx="170" cy="140" r="3"/><circle cx="182" cy="150" r="3"/><circle cx="160" cy="152" r="3"/></g>
          <rect x="60" y="158" width="200" height="16" rx="4" fill="${C.tierra}"/>
          <rect x="60" y="170" width="200" height="10" fill="${C.tierra2}"/>
          <path d="M175 158c0-22 0-34 0-46M175 118c10-8 24-6 28 2-7 9-22 9-28-2z" stroke="${C.hoja2}" stroke-width="4" fill="none" stroke-linecap="round"/>
        </svg>`,
    },
    luz: {
      titulo: "Luz solar",
      claves: ["luz", "sol", "solar", "iluminación", "iluminacion", "par", "fotosíntesis", "fotosintesis", "sombra", "lux"],
      svg: `
        <svg viewBox="0 0 320 200" xmlns="http://www.w3.org/2000/svg">
          <circle cx="160" cy="70" r="30" fill="${C.sol}"/>
          <g stroke="${C.sol}" stroke-width="5" stroke-linecap="round">
            <path d="M160 20v-6M160 126v6M104 70h-6M222 70h6M120 30l-4-4M200 30l4-4M120 110l-4 4M200 110l4 4"/>
          </g>
          <path d="M60 160h200" stroke="${C.tierra}" stroke-width="14" stroke-linecap="round"/>
          <g stroke="${C.hoja2}" stroke-width="4" fill="none" stroke-linecap="round">
            <path d="M130 160c0-26 0-40 0-54M130 116c10-8 24-6 28 2-7 9-22 9-28-2z"/>
            <path d="M190 160c0-22 0-34 0-46M190 124c-10-8-24-6-28 2 7 9 22 9 28-2z"/>
          </g>
        </svg>`,
    },
    ph: {
      titulo: "pH del sustrato",
      claves: ["ph", "acidez", "ácido", "acido", "alcalino", "básico", "basico"],
      svg: `
        <svg viewBox="0 0 320 200" xmlns="http://www.w3.org/2000/svg">
          <rect x="40" y="92" width="240" height="16" rx="8" fill="none" stroke="#0e2a22" stroke-width="2"/>
          <rect x="40" y="92" width="240" height="16" rx="8" fill="url(#ph)"/>
          <defs><linearGradient id="ph" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stop-color="${C.critico}"/><stop offset=".5" stop-color="${C.hoja}"/>
            <stop offset="1" stop-color="${C.agua}"/></linearGradient></defs>
          <path d="M164 74l10 18h-20z" fill="${C.texto || '#e8fff6'}"/>
          <g fill="#8fb9ac" font-family="monospace" font-size="12">
            <text x="40" y="130">4</text><text x="150" y="130">6.5</text><text x="266" y="130">9</text>
          </g>
          <path d="M150 150c0-18 0-28 0-38M150 118c9-7 21-5 25 2-6 8-19 8-25-2z" stroke="${C.hoja2}" stroke-width="4" fill="none" stroke-linecap="round"/>
        </svg>`,
    },
    plagas: {
      titulo: "Control de plagas",
      claves: ["plaga", "plagas", "insecto", "pulgón", "pulgon", "mariquita", "hongo", "enfermedad", "bicho"],
      svg: `
        <svg viewBox="0 0 320 200" xmlns="http://www.w3.org/2000/svg">
          <path d="M70 150c40-8 60-30 70-70 10 40 30 62 70 70-40 8-60 20-70 40-10-20-30-32-70-40z" fill="${C.hoja}" opacity=".9"/>
          <path d="M140 60c0 40 0 70 0 90" stroke="${C.hoja2}" stroke-width="3"/>
          <g transform="translate(196 96)">
            <ellipse cx="0" cy="0" rx="16" ry="18" fill="${C.critico}"/>
            <path d="M0 -18v36" stroke="${C.trazo}" stroke-width="2"/>
            <circle cx="-6" cy="-4" r="2.5" fill="${C.trazo}"/><circle cx="6" cy="2" r="2.5" fill="${C.trazo}"/>
            <circle cx="-5" cy="8" r="2.5" fill="${C.trazo}"/><circle cx="7" cy="-8" r="2.5" fill="${C.trazo}"/>
            <circle cx="0" cy="-20" r="5" fill="${C.trazo}"/>
          </g>
        </svg>`,
    },
    compost: {
      titulo: "Compostaje y CO₂",
      claves: ["compost", "compostaje", "abono", "co2", "co₂", "materia orgánica", "organico", "descomposición", "descomposicion"],
      svg: `
        <svg viewBox="0 0 320 200" xmlns="http://www.w3.org/2000/svg">
          <path d="M90 90h140l-14 80H104z" fill="${C.tierra2}" stroke="${C.tierra}" stroke-width="3"/>
          <path d="M96 120h128M100 148h120" stroke="${C.tierra}" stroke-width="3" opacity=".6"/>
          <g fill="${C.hoja}"><path d="M120 96c10-6 18-4 22 4-8 6-18 4-22-4z"/><path d="M180 96c-10-6-18-4-22 4 8 6 18 4 22-4z"/></g>
          <g stroke="#8fb9ac" stroke-width="3" fill="none" stroke-linecap="round" opacity=".7">
            <path d="M150 80c-8-10 8-16 0-28M170 76c-8-10 8-16 0-28"/>
          </g>
        </svg>`,
    },
    cosecha: {
      titulo: "Siembra y cosecha",
      claves: ["cosecha", "cosechar", "sembrar", "siembra", "recolectar", "fruto", "maduro", "planta", "crecimiento", "germinar"],
      svg: `
        <svg viewBox="0 0 320 200" xmlns="http://www.w3.org/2000/svg">
          <path d="M96 120h128l-10 54H106z" fill="none" stroke="${C.tierra}" stroke-width="6" stroke-linecap="round"/>
          <g fill="${C.critico}"><circle cx="128" cy="118" r="12"/><circle cx="160" cy="112" r="13"/><circle cx="192" cy="118" r="12"/></g>
          <g stroke="${C.hoja2}" stroke-width="3"><path d="M128 106v-8M160 99v-8M192 106v-8"/></g>
          <path d="M60 150c30-6 44-24 50-52 6 28 20 46 50 52" fill="none" stroke="${C.hoja}" stroke-width="4"/>
        </svg>`,
    },
  };

  const ORDEN = Object.keys(TEMAS);

  function elegirTema(texto) {
    if (!texto) return null;
    const t = texto.toLowerCase();
    let mejor = null;
    let puntos = 0;
    for (const nombre of ORDEN) {
      let p = 0;
      for (const clave of TEMAS[nombre].claves) {
        if (t.includes(clave)) p += clave.length > 4 ? 2 : 1;
      }
      if (p > puntos) {
        puntos = p;
        mejor = nombre;
      }
    }
    return puntos > 0 ? mejor : null;
  }

  class Galeria {
    constructor({ contenedor, pie, chip, chipIcono, chipTexto }) {
      this.cont = contenedor;
      this.pie = pie;
      this.chip = chip;
      this.chipIcono = chipIcono;
      this.chipTexto = chipTexto;
      this.actual = null;
      this._debounce = 0;
      this._cambio = 0;
      this._reducirMovimiento = global.matchMedia("(prefers-reduced-motion: reduce)").matches;
      this.mostrarTema("general");
    }

    // Analiza un fragmento de conversación y cambia la ilustración.
    reaccionar(texto, icono) {
      clearTimeout(this._debounce);
      this._debounce = setTimeout(() => {
        const tema = elegirTema(texto);
        if (tema) this.mostrarTema(tema, icono);
      }, 250);
    }

    mostrarTema(nombre, icono) {
      if (!TEMAS[nombre] || nombre === this.actual) return;
      this.actual = nombre;
      const t = TEMAS[nombre];
      const cambio = ++this._cambio;
      const capa = document.createElement("div");
      capa.className = "ilustracion-capa";
      capa.innerHTML = t.svg;

      const anteriores = [...this.cont.querySelectorAll(".ilustracion-capa")];
      if (!anteriores.length || this._reducirMovimiento) {
        this.cont.replaceChildren(capa);
      } else {
        anteriores.forEach((anterior) => anterior.classList.add("saliente"));
        capa.classList.add("entrante");
        this.cont.appendChild(capa);
        global.setTimeout(() => {
          if (cambio !== this._cambio) return;
          [...this.cont.children].forEach((nodo) => { if (nodo !== capa) nodo.remove(); });
          capa.classList.remove("entrante");
        }, 540);
      }
      if (this.pie) this.pie.textContent = t.titulo;
      if (this.chip) {
        this.chip.hidden = false;
        this.chipIcono.textContent = icono || "🌱";
        this.chipTexto.textContent = t.titulo;
        this.chip.classList.remove("cambio");
        void this.chip.offsetWidth;
        this.chip.classList.add("cambio");
      }
    }
  }

  global.EcosphereImagery = { Galeria, TEMAS, elegirTema };
})(window);
