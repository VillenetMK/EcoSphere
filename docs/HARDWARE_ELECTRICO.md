# Hardware eléctrico canónico de EcoSphere

> Estado reconstruido del historial técnico “Compra placa ESP32” y comparado con el software de `main` el 23 de agosto de 2026.
>
> Esta es la referencia de cableado. Las fotografías históricas no están almacenadas en el repositorio, por lo que un dato que no quedó confirmado por texto se mantiene como **pendiente**, sin inferirlo.

## Convenciones

- **Confirmado:** comprado, identificado o decidido explícitamente.
- **Pendiente de validar:** falta leer una etiqueta, medir o probar físicamente.
- **Descartado/sustituido:** apareció antes en el proyecto, pero una decisión posterior lo reemplazó.
- Los valores “iniciales” de firmware son configuración de partida, no mediciones del montaje.

## Arquitectura eléctrica vigente

```text
Red CA
  └─ Fuente WODE 12 V CC / 5 A / 60 W
       ├─ distribución +12 V y GND ── MOSFET ── ventilador 12 V
       ├─ distribución +12 V y GND ── MOSFET ── bomba 12 V
       ├─ distribución de potencia ── MOSFET ── LED grow
       │    └─ tensión PENDIENTE: depende de la etiqueta/modelo instalado
       └─ LM2596 ajustado a 5,00 V ── pin VIN/5V del ESP32
              └─ ESP32 3V3 ── sensores
```

Todos los retornos comparten una única referencia de GND: negativo de la fuente, LM2596 IN−/OUT−, GND del ESP32, sensores y GND/VIN− de los módulos MOSFET. Los positivos de 12 V, 5 V y 3,3 V son redes distintas y **no deben unirse**.

Durante las pruebas de mesa, el ESP32 se alimenta solamente por USB. No se conecta simultáneamente USB y 5 V externo hasta comprobar el circuito de alimentación/reversa de la placa concreta.

## Controlador

| Elemento | Estado | Datos conservados |
|---|---|---|
| ESP32 | Confirmado | Placa de 38 pines, familia ESP32-WROOM-32 / NodeMCU ESP-32S. En Arduino IDE se utilizó “ESP32 Dev Module”. |
| Variante exacta de PCB y puente USB-UART | Pendiente | No quedó confirmado si usa CP210x, CH340, CH9102 u otro. |
| Unidades | Confirmado | Se compraron dos placas de 38 pines: una para el sistema y una de repuesto. Una placa anterior sufrió un corto y emitió humo; quedó fuera de servicio. |

Una identificación intermedia como “DevKit V1 de 30 pines” provenía de una interpretación de fotografía y contradice la compra final de dos placas de 38 pines. No se usa como referencia de montaje.

## Sensores y conexiones

### Bus I2C compartido

| Señal | ESP32 |
|---|---:|
| SDA | GPIO21 |
| SCL | GPIO22 |
| Alimentación de sensores | 3V3 |
| Referencia | GND |

### BME280 de 6 pines

| Pin del módulo | Conexión |
|---|---|
| VCC/VIN | ESP32 3V3 |
| GND | GND común |
| SDA | GPIO21 |
| SCL/SCK | GPIO22 |
| CSB/CS | 3V3 |
| SDO | GND |

Con SDO a GND, la dirección I2C esperada es `0x76`. Debe confirmarse con un escáner I2C al montar. La instrucción antigua de dejar CSB y SDO flotantes está sustituida: en este módulo de 6 pines se fijan como muestra la tabla.

### BH1750 / GY-302

| Pin del módulo | Conexión |
|---|---|
| VCC | ESP32 3V3 |
| GND | GND común |
| SDA | GPIO21 |
| SCL | GPIO22 |
| ADDR | GND |

Con ADDR a GND, la dirección esperada es `0x23`; debe comprobarse en la prueba I2C.

### Humedad de suelo

| Elemento | Conexión |
|---|---|
| Modelo | Sensor capacitivo de humedad de suelo v1.2 |
| VCC | ESP32 3V3 |
| GND | GND común |
| AO/AOUT | GPIO34 (ADC, solo entrada) |
| Cable observado | Rojo/negro/amarillo; verificar continuidad antes de asumir el orden en el conector |

Valores iniciales de calibración conservados del firmware histórico:

- seco: ADC `3000`;
- mojado: ADC `1400`.

No son medidas confirmadas de esta unidad. Deben reemplazarse solo después de registrar el ADC en seco y en sustrato húmedo con el sensor instalado.

### Nivel de agua

| Elemento | Estado vigente |
|---|---|
| Sensor usado | Un sensor flotador horizontal |
| Señal | GPIO32 |
| Otro terminal | GND |
| Entrada | `INPUT_PULLUP` |
| Polaridad inicial | `WATER_SENSOR_ACTIVE_LOW = true` |
| Sensor vertical | No se usa |
| GPIO27 | Libre |

La polaridad activa en bajo no se considera confirmada hasta hacer la prueba con el flotador arriba y abajo. Si la lectura resulta invertida, se cambia la configuración; no se altera el cableado por suposición.

## Actuadores

| Actuador | Datos confirmados | GPIO | Etapa de potencia |
|---|---|---:|---|
| Ventilador | Axial 12 V, 40 × 40 mm | GPIO25, PWM | Módulo MOSFET |
| Bomba | Sumergible 12 V CC, 3 m, 240 L/h | GPIO26, encendido/apagado | Módulo MOSFET |
| LED grow | Tensión nominal pendiente de leer/validar | GPIO33, PWM | Módulo MOSFET |

La referencia aproximada de 350 mA para la bomba apareció como dato comercial, no como corriente medida. La bomba se prueba únicamente sumergida.

### LED grow: corrección de alimentación

El historial contiene descripciones incompatibles: compra buscada a 12 V, artículo recibido como USB/5 V y una prueba en la que encendió con 12 V. Encender no demuestra que 12 V sea una tensión continua segura.

Hasta leer la etiqueta/modelo y medir corriente y temperatura:

- no se documenta el LED como definitivamente de 5 V ni de 12 V;
- si es nominalmente 5 V, se alimenta desde una conversión de 5 V correctamente dimensionada;
- si la placa/etiqueta valida 12 V, puede ir en la rama de 12 V;
- el software no debe diagnosticar una tensión concreta para este LED.

## Módulos MOSFET

- Modelo confirmado: driver MOSFET 15 A con dos AOD4184A en paralelo.
- Cantidad final declarada: 5 unidades; se usan 3 y quedan 2 de repuesto.
- Entrada de control documentada en el historial: 3,3–20 V.
- Potencia documentada en el historial: 5–36 V CC.
- Límite PWM documentado: hasta 20 kHz; mantener margen respecto de ese máximo.

Conexión por canal:

| Terminal | Conexión |
|---|---|
| TRIG/PWM | GPIO del actuador |
| GND de control | GND común/ESP32 |
| VIN+ | Positivo de la alimentación correspondiente al actuador |
| VIN− | GND común |
| OUT+ / OUT− | Positivo y negativo del actuador según la serigrafía del módulo |

La decisión más reciente fue no añadir resistencias externas de `10 kΩ` por defecto porque el módulo seleccionado incorpora red de entrada. Esta afirmación se debe revalidar contra la placa recibida antes de un montaje definitivo.

## Alimentación y distribución

| Elemento | Estado |
|---|---|
| Fuente | WODE 12 V CC, 5 A, 60 W |
| LM2596 | Step-down ajustable, anunciado 3 A |
| Salida LM2596 | Ajustar y medir a 5,00 V antes de conectar el ESP32 |
| Entrada ESP32 | VIN/5V, no 3V3 |
| Adaptadores de 19,5 V | Descartados para este montaje |
| MB102 de protoboard | No se usa en el circuito final; solo podría reservarse para pruebas de baja potencia |
| Solar/baterías 18650 | Descartados del alcance actual; no existe topología, BMS ni cargador validados |

Las ramas de bomba, ventilador y LED no pasan por los rieles de una protoboard. Usan conductores y terminales de potencia directos.

## Conductores y terminales

La sección métrica es la especificación de referencia. Las equivalencias AWG usadas durante la compra eran aproximadas:

| Uso | Sección comprada/decidida | Longitud |
|---|---:|---:|
| Sensores, señales y GPIO | 0,20–0,25 mm² | 10 m |
| 5 V y auxiliares | 0,50 mm² | 10 m |
| Ramas de bomba, ventilador y LED | 0,75–1,0 mm² | 10 m |
| Distribución principal 12 V/GND | 1,5 mm² | 5 m |

Corrección: AWG18 equivale aproximadamente a 0,82 mm² y AWG16 a 1,31 mm²; por ello “1,0 mm² = AWG18” y “1,5 mm² = AWG16” no son equivalencias exactas.

Punteras/ferrules previstas:

- 0,25 mm² × 50;
- 0,50 mm² × 50;
- 1,0 mm² × 30;
- 1,5 mm² × 20.

Las punteras se usan principalmente en borneras de tornillo. En conectores de palanca solo se emplean si el fabricante del conector admite explícitamente el conductor ferrulado.

Hay conectores grises de 5 vías y conectores transparentes. Antes de usarlos como barras de distribución se debe comprobar con continuidad qué entradas están internamente unidas y validar tensión, corriente y sección admitida.

## Protecciones: estado real

Aún no se confirmó la compra ni instalación de fusibles o diodos. Por tanto, son requisitos de ingeniería pendientes, no piezas instaladas:

- portafusible y fusible principal: se propuso 5 A, pero debe validarse contra el consumo medido y el conductor;
- fusibles por rama: se propusieron valores cercanos a 1 A para algunas ramas, sin dimensionamiento final;
- supresión inductiva: debe definirse a partir del modelo eléctrico real de cada carga. La bomba y un ventilador brushless de dos hilos no deben tratarse como cargas idénticas;
- no instalar un diodo de parte/orientación no especificada;
- añadir alivio de tensión, aislamiento y protección contra humedad en las entradas de cable.

## Secuencia segura de puesta en marcha

1. Trabajar sin energía y comprobar continuidad/ausencia de corto entre +12 V–GND, +5 V–GND y 3V3–GND.
2. Ajustar el LM2596 sin el ESP32 conectado.
3. Probar el ESP32 solo por USB.
4. Añadir sensores: primero I2C, luego sensor de suelo y flotador.
5. Verificar direcciones I2C y polaridad real del flotador.
6. Probar un actuador por vez: ventilador, LED y finalmente bomba sumergida.
7. Medir tensión y corriente de cada rama antes de fijar fusibles y alimentación del LED.
8. Montar la electrónica fuera del ambiente húmedo.

## Asignación consolidada de GPIO

| GPIO | Función |
|---:|---|
| 21 | SDA I2C: BME280 + BH1750 |
| 22 | SCL I2C: BME280 + BH1750 |
| 34 | AO del sensor capacitivo de suelo |
| 32 | Sensor horizontal de nivel de agua |
| 25 | PWM del ventilador |
| 26 | Bomba |
| 33 | PWM del LED grow |
| 27 | Libre |

## Datos que no deben inventarse

Permanecen pendientes:

- variante exacta de ESP32 y puente USB-UART;
- corriente real de bomba, ventilador y LED;
- tensión nominal definitiva del LED;
- polaridad física del flotador;
- valores calibrados seco/mojado del sensor de suelo;
- modelo, orientación y dimensionamiento de protección inductiva;
- valores finales de fusibles;
- topología/rating de los conectores transparentes y grises;
- frecuencia PWM final realmente cargada en el firmware.
