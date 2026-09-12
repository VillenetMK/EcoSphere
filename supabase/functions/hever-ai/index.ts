/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

// Keep a closed endpoint for cached Eureka clients; never contact the provider.
Deno.serve(() => new Response(
  JSON.stringify({ error: "La función de voz de Eureka ha sido retirada." }),
  {
    status: 410,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  },
));
