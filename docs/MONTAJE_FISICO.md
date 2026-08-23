# Montaje físico canónico de EcoSphere

> Reconstruido del historial técnico y separado entre lo ya armado, lo decidido y lo que sigue pendiente. No se deducen dimensiones a partir de imágenes ausentes.

## Contenedor principal

- Contenedor transparente: **53,0 cm de largo × 36,0 cm de ancho × 30,0 cm de alto**.
- Capacidad indicada: **37,5 L**.
- Identificación conservada del historial: caja móvil transparente Rey Plast “Supreme”.
- Uso: cámara principal de plantas, sustrato, drenaje, sensores ambientales, entrada de agua y ventilación.
- Estado descrito: casi cerrado/semisellado; no debe convertirse en una caja hermética sin ventilación controlada.

Las recomendaciones antiguas de 35 × 20 × 20, 45 × 25 × 25 o 50 × 30 × 30 cm eran criterios de compra y no sustituyen las medidas del contenedor adquirido.

## Zonas del sistema

```text
Depósito lateral de agua
  ├─ bomba sumergible
  └─ flotador horizontal
          │
          └── manguera ── entrada alta ──► contenedor principal
                                             ├─ plantas/sustrato
                                             ├─ sensor de suelo
                                             ├─ BME280
                                             ├─ BH1750
                                             ├─ ventilador
                                             └─ drenaje inferior

Electrónica y fuente: fuera del contenedor húmedo
```

El depósito lateral de agua no tiene medidas ni capacidad confirmadas en texto. También se mencionó otro recipiente lateral de recuperación “del mismo tamaño”, pero no quedó inequívoco a qué depósito se comparaba. No debe confundirse con la caja principal de 37,5 L.

## Capas del contenedor

Orden vigente, de abajo hacia arriba:

1. grava/granalla blanca como drenaje;
2. malla plástica fina;
3. mezcla de sustrato;
4. cobertura ligera de musgo.

La malla de hierro inicialmente colocada fue retirada/sustituida por malla plástica debido al riesgo de oxidación en humedad constante.

### Medidas y mezcla conservadas

- Capa de grava indicada: **2–3 cm**.
- Altura de sustrato inicialmente recomendada: **8–12 cm**; otra respuesta acotó **8–10 cm**.
- Receta medida indicada al usuario: **3 L de sustrato para anturios + 2 L de fibra de coco**.
- Esa mezcla produce 5 L y no alcanza por sí sola para cubrir toda la caja a la altura recomendada; se decidió formar una zona elevada/isla en lugar de inferir material adicional.
- Perlita disponible: bolsa de **2 L**.
- Uso indicado de perlita: mezclar aproximadamente **1–1,5 L** (media a tres cuartas partes de la bolsa), pero la cantidad realmente incorporada no quedó medida en texto.
- Musgo: cobertura superficial; no mezclarlo como componente principal. El usuario confirmó que ya lo colocó encima.

La relación preliminar de “2 partes de sustrato por 1 de coco” fue reemplazada para el lote disponible por la instrucción medida de 3 L + 2 L. La posterior propuesta conceptual “3 partes sustrato, 2 coco, 1 perlita” describe proporciones, pero no confirma cuánto se añadió físicamente.

## Estado físico al último punto confirmado

Confirmado por el diálogo:

- grava colocada;
- malla plástica colocada sobre la grava;
- sustrato y fibra de coco mezclados;
- perlita distribuida visualmente, sin cantidad final registrada;
- superficie con una pendiente suave;
- musgo colocado encima.

Aún no estaba confirmado como instalado:

- ESP32;
- sensores;
- módulos MOSFET;
- ventilador;
- bomba;
- LM2596/fuente;
- manguera fijada y sellada;
- cableado final.

El último estado explícito fue que los componentes electrónicos todavía no se habían abierto para el montaje integral. Por eso ningún componente se marca como físicamente instalado solo porque exista un plano de ubicación.

## Ubicación prevista de sensores

### Humedad de suelo

- zona media del sustrato;
- lejos de la pared;
- no debajo del punto de goteo;
- introducir solo la parte sensora negra;
- mantener la electrónica y unión del cable fuera del sustrato húmedo.

### BME280

- zona media-alta;
- expuesto al aire interior;
- lejos de gotas, sustrato y condensación directa;
- no encapsular de forma que impida el intercambio de aire.

### BH1750

- parte alta, con visión de la iluminación interior;
- sin sombra estructural permanente;
- protegido de agua y condensación;
- no enterrado ni apoyado en el fondo.

### Flotador horizontal

- en el depósito lateral de agua;
- altura de conmutación pendiente de decidir con el volumen mínimo seguro para la bomba;
- comprobar mecánicamente flotador arriba/abajo antes de sellar.

## Agua y drenaje

Configuración vigente:

- una bomba en el depósito lateral principal;
- una manguera entra por una esquina o pared alta del contenedor;
- el agua se dirige al sustrato, nunca directamente al sensor capacitivo;
- el exceso baja por el sustrato, atraviesa la malla y llega a la zona de drenaje/recuperación inferior.

La automatización de retorno con una segunda bomba se discutió, pero quedó **diferida**. El montaje actual usa una sola bomba; cualquier agua recuperada puede devolverse manualmente.

Se propusieron malla fina, esponja/filtro y rebose para una futura recirculación. No se consideran instalados.

### Manguera

Se recomendó comprar alrededor de 1 m y se propuso 6 mm de diámetro interior / 8 mm exterior, con 4/6 mm como alternativa. El diámetro de la boquilla de la bomba no se midió, por lo que **ninguna de esas medidas es aún especificación final**. Se debe medir el espigo y elegir una manguera compatible y firmemente sujeta.

## Ventilación

- Ventilador previsto en lateral alto o tapa.
- Debe mover/intercambiar aire sin apuntar directamente y de manera permanente a una planta o al BME280.
- La abertura necesita protección mecánica y contra entrada/salida no controlada de agua.
- La orientación definitiva de flujo no quedó confirmada.

## Electrónica fuera de la cámara húmeda

Fuera del contenedor principal:

- ESP32;
- protoboard usada únicamente en pruebas de señal;
- módulos MOSFET;
- LM2596;
- fuente;
- distribución de 12 V/5 V/GND;
- empalmes y protecciones.

El historial concluyó que no hacía falta comprar otra caja si los componentes ya incluían alojamientos adecuados, pero no confirmó su grado de protección. Deben mantenerse fuera de condensación, con ventilación, alivio de tensión y acceso para medir.

La protoboard de 830 puntos y cables Dupont macho-macho de 20 cm son material de prueba, no distribución final de potencia.

## Sellado y pasacables

- Material seleccionado: silicona neutra transparente.
- Sellar únicamente después de validar cada sensor y actuador.
- No usar silicona acética sobre electrónica o metal susceptible.
- Las entradas de cable deben incorporar alivio de tensión; la silicona por sí sola no sustituye un prensaestopas cuando el cable pueda moverse.
- Formar bucle de goteo antes de cualquier caja electrónica.

## Elementos fuera del alcance vigente

- panel solar;
- pack de tres celdas 18650;
- carga solar, MPPT/BMS y funcionamiento autónomo;
- segundo circuito automático de bombeo/recuperación;
- MB102 como fuente del sistema;
- sensor flotador vertical;
- mulch como base principal.

El panel se leyó visualmente como “12 V – 200 m…”, posiblemente 200 mA, pero la cifra no se confirmó. Al quedar descartado, no se incorpora a ningún cálculo.

## Lista de validación antes del montaje definitivo

- [ ] Leer etiqueta/modelo del LED grow y definir su tensión.
- [ ] Medir la boquilla de la bomba y seleccionar manguera.
- [ ] Definir volumen mínimo del depósito y altura del flotador.
- [ ] Comprobar polaridad del flotador.
- [ ] Confirmar orientación del ventilador.
- [ ] Medir consumos de bomba, ventilador, LED y rama de 5 V.
- [ ] Dimensionar fusibles y protección inductiva.
- [ ] Verificar continuidad interna y ratings de conectores de distribución.
- [ ] Validar sensores y actuadores en mesa.
- [ ] Montar electrónica fuera de humedad y añadir alivio de tensión.
- [ ] Sellar solo tras una prueba completa sin fugas.
