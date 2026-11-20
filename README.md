# Trotamundos – Prototipo (entrega final)

Prototipo del **monolito modular** diseñado en la Wiki (ADR-0001). Muestra el flujo principal funcionando de punta a punta:

> una reserva llega por **webhook de un proveedor** o por **correo** → se traduce al **modelo común** → **Itinerary** la guarda, evita duplicados y la agrupa en un viaje → el evento sale por el **outbox** → el **panel** y los **avisos** se actualizan solos.

Además: carga manual, compartir un viaje solo con personas designadas, archivado de viajes terminados y el **sistema de recomendación** (Opción A): cuando se cancela un vuelo aparece una alternativa, y lo que el usuario acepta, rechaza o ajusta cambia sus preferencias y las sugerencias siguientes (*human-in-the-loop*).

Está hecho en **Java 17+, sin dependencias externas**: usa el servidor HTTP que trae el JDK, así que solo hace falta tener Java instalado.

---

## Cómo correrlo

**Windows (PowerShell):**

```powershell
.\ejecutar.ps1
```

**Linux / macOS:**

```bash
./ejecutar.sh
```

Después abrir **http://localhost:8080**. El puerto se cambia con la variable de entorno `PUERTO`.

**Pruebas:** primero la de fronteras entre módulos, después la del outbox, y al final la de punta a punta, que levanta el sistema, recorre los flujos por HTTP y verifica 26 cosas.

```powershell
.\probar.ps1
```

**Prueba de carga del panel** (tarda unos segundos más, por eso va aparte):

```powershell
.\carga.ps1
```

Pide el panel 10.000 veces con 50 pedidos en paralelo, primero solo con lecturas y después mientras entran webhooks. Resultado en una notebook:

| Escenario | Pedidos | p50 | p95 | p99 |
|---|---|---|---|---|
| Solo lecturas | 10.000 | 13 ms | 35 ms | 62 ms |
| Lecturas mientras entran ~1.000 webhooks | 10.000 | 12 ms | 29 ms | 47 ms |

Muy por debajo de los 800 ms de QA-2, y la ingesta no frena al panel, gracias al CQRS (ADR-0005). La segunda corrida da un poco mejor porque Java ya está "en caliente". Es una medición local con datos en memoria: sirve como orden de magnitud, no reemplaza una prueba en el entorno real.

### Demo sugerida para la entrega

En la página, con el usuario `martina@mail.com`:

1. **La aerolínea confirma un vuelo**: aparece "Viaje a MAD" con el vuelo y un aviso.
2. **Llega el correo de un hotel**: el hotel se suma al **mismo viaje** (por cercanía de fechas).
3. **Llega un correo que no es de viajes**: se descarta por el filtro de whitelist.
4. **La aerolínea cancela el vuelo**: el vuelo queda tachado, llega el aviso "Se canceló tu vuelo" y en **Sugerencias para vos** aparece una alternativa: UX42, el más barato, que tiene una escala.
5. **Prefiero sin escalas**: la sugerencia cambia por IB6844, que es directo, y "Lo que aprendimos de vos" muestra que subió el peso de "sin escalas".
6. **Rechazar**: aparece AR1134, otra opción directa (no vuelve a sugerir la rechazada). **Aceptar**: la sugerencia sale del panel.
7. **Compartir con nicolas@mail.com**: cambiar el usuario a `nicolas@mail.com` y el viaje aparece en "Compartidos conmigo".
8. **Pasó el viaje: archivar**: con `martina@mail.com`, el viaje pasa a "Archivados".

---

## Punteo de la estructura

```
trotamundos-prototipo/
├── src/main/java/trotamundos/
│   ├── Trotamundos.java            ← arma todos los módulos y los conecta por el bus (el "monolito")
│   ├── App.java                    ← punto de arranque
│   ├── comun/                      ← módulo Común
│   │   ├── modelo/                 ← modelo común de reserva + tipos como plugins (vuelo, hotel, auto)
│   │   ├── eventos/                ← bus de eventos interno + outbox
│   │   ├── http/                   ← servidor HTTP (rol del API Gateway)
│   │   └── json/                   ← lector/escritor JSON mínimo
│   ├── ingesta/
│   │   ├── mailconnector/          ← aviso de correo, filtro por whitelist
│   │   │   ├── api/  negocio/  datos/
│   │   ├── emailparser/            ← extrae la reserva del correo (plantillas)
│   │   │   └── negocio/
│   │   └── partnerintegration/     ← webhooks + un adaptador por proveedor
│   │       ├── api/  negocio/
│   ├── itinerario/                 ← fuente de verdad: dedup, agrupar en viajes, archivar
│   │   ├── api/  negocio/  datos/
│   ├── panel/                      ← Dashboard Query: panel precalculado por usuario (CQRS)
│   │   ├── api/  negocio/  datos/
│   ├── compartir/                  ← Sharing: permisos por persona designada
│   │   ├── api/  negocio/  datos/
│   ├── notificaciones/             ← avisos al usuario
│   │   ├── negocio/  datos/
│   └── recomendacion/              ← Recommendation: sugerencias + human-in-the-loop (Iteración 5)
│       ├── api/  negocio/  datos/
├── src/test/java/trotamundos/
│   ├── PruebaFronteras.java        ← revisa que ningún módulo saltee las fronteras (ADR-0001)
│   ├── PruebaOutbox.java           ← prueba del outbox
│   ├── PruebaFlujo.java            ← prueba de punta a punta (incluye el recomendador)
│   └── PruebaCarga.java            ← prueba de carga del panel (QA-2)
├── web/index.html                  ← página mínima para mostrar el prototipo
├── openapi.yaml                    ← contrato de la API (spec-driven)
└── compilar / ejecutar / probar / carga   ← scripts .ps1 (Windows) y .sh (Linux/macOS)
```

### Qué hace cada módulo y de qué decisión sale

| Módulo | Qué hace en el prototipo | Publica | Consume | Decisión de la Wiki |
|---|---|---|---|---|
| **Mail Connector** | Recibe el aviso de correo nuevo y deja pasar solo remitentes de la whitelist o del catálogo de viajes | `CorreoDeViajeRecibido` | — | ADR-0003 |
| **Email Parser** | Extrae vuelo u hotel de correos con plantilla "Campo: valor" | `ReservaDetectada` | `CorreoDeViajeRecibido` | ADR-0003, ADR-0004 |
| **Partner Integration** | Webhooks de 2 proveedores de ejemplo (aerolínea y hotel), cada uno con su adaptador y su formato; `buscarAlternativas()` para el recomendador | `ReservaDetectada` | — | ADR-0004 |
| **Itinerary** | Evita duplicados por código de confirmación, agrupa en viajes (±2 días), carga manual, archiva | `ReservaActualizada`, `ViajeArchivado` (por outbox) | `ReservaDetectada` | ADR-0001, ADR-0010 |
| **Dashboard Query** | Mantiene el panel ya armado de cada usuario; ignora eventos repetidos o viejos por versión | — | `ReservaActualizada`, `ViajeArchivado`, `PermisoCambiado`, `SugerenciaGenerada`, `SugerenciaRespondida` | ADR-0005, ADR-0018 |
| **Sharing** | Permisos por persona designada; `puedeVer()` es su interfaz pública | `PermisoCambiado` (por outbox) | `ReservaActualizada` (para saber el dueño) | ADR-0009 |
| **Notification** | Arma el aviso de cada cambio y lo "envía" (consola) | — | `ReservaActualizada` | ADR-0005 |
| **Recommendation** | Ante una cancelación busca alternativas, las puntúa con los pesos del usuario y sugiere la mejor; aceptar, rechazar o ajustar cambia esos pesos | `SugerenciaGenerada`, `SugerenciaRespondida` (por outbox) | `ReservaActualizada` | ADR-0017 a ADR-0020 |
| **Común** | Modelo común, tipos de reserva como plugins, bus, outbox | — | — | ADR-0001, ADR-0004, ADR-0010 |

### Cómo se respetan las reglas del monolito modular (ADR-0001)

- **Un solo desplegable:** `Trotamundos.java` arma todos los módulos en un mismo programa.
- **Se comunican por eventos o por interfaz pública:** los únicos llamados directos entre módulos son `panel → compartir.puedeVer()` y `recomendacion → partnerintegration.buscarAlternativas()`, igual que en la vista de módulos. Todo lo demás pasa por el bus.
- **Cada módulo tiene sus tablas:** cada uno tiene su propio repositorio en `datos/` y nadie lee el de otro.
- **Capas dentro de cada módulo:** `api` (endpoints) → `negocio` (reglas) → `datos` (repositorio).
- **Algoritmo de recomendación intercambiable (QA-14):** el módulo solo conoce la interfaz `Recomendador`; la implementación (`PuntajePorPreferencias`) se elige en una sola línea de `RecomendacionModulo`.
- **Outbox:** Itinerary, Sharing y Recommendation guardan cada cambio junto con su evento, y un hilo aparte los publica. Si un evento falla, se reintenta; después de 5 intentos pasa a "eventos fallidos" y los que vienen atrás siguen publicándose (lo verifica `PruebaOutbox`).
- **Agregar un medio de transporte (QA-10):** crear una clase en `comun/modelo/tipos/` (por ejemplo `Tren`) y registrarla en `Trotamundos.java`. No se toca nada más.

---

## Aclaraciones: qué falta para que sea el sistema real

Este prototipo muestra **la estructura y el flujo principal**, no todo el sistema. Estas son las simplificaciones que hicimos y lo que hay que refinar:

### Simplificaciones de infraestructura

| Tema | Cómo está ahora | Qué falta | Driver / ADR |
|---|---|---|---|
| Persistencia | Todo en memoria: se pierde al reiniciar | Base de datos (por ej. PostgreSQL) con un esquema por módulo; outbox como tabla en la misma transacción | ADR-0001, ADR-0010 |
| Outbox | Lo usan Itinerary, Sharing y Recommendation. Mail Connector y Partner Integration publican directo en el bus (si se pierde un evento, el proveedor puede volver a mandarlo) | Que también publiquen por outbox, y una pantalla para revisar y reenviar los eventos fallidos | QA-5, ADR-0010 |
| Login | Simulado con el encabezado `X-Usuario` | Login con proveedor de identidad (OIDC) y token en cada pedido; API Gateway real | QA-8, QA-9 |
| Réplicas, zonas y regiones | Una sola instancia, que cumple los dos roles (web y procesamiento) a la vez | Contenedores con autoescalado por rol, base replicada, rolling update, varias regiones. Los roles necesitan la base compartida: sin ella no se pueden separar | QA-4, QA-5, ADR-0002, ADR-0007, ADR-0021 |
| Pruebas | Fronteras, outbox, punta a punta y una prueba de carga local, hechas a mano | JUnit, pruebas de contrato contra `openapi.yaml`, prueba de carga en el entorno real con 4 M de usuarios | QA-2, QA-5 |
| Fronteras entre módulos | `PruebaFronteras` revisa los `import` de cada módulo y falla si alguno saltea la vista de módulos | Que el chequeo corra en cada compilación (módulos de Java o ArchUnit) | ADR-0001 |

### Funcionalidad incompleta

| Tema | Cómo está ahora | Qué falta | Driver / ADR |
|---|---|---|---|
| Correo | El aviso trae el correo entero, simulado | Conexión real con Gmail / Outlook por OAuth de solo lectura, tokens cifrados, descargar el cuerpo recién después del filtro | UC-2, QA-7, QA-8, ADR-0003, ADR-0008 |
| Parser de correos | Solo 2 plantillas fijas | Más remitentes, formatos estándar de las aerolíneas, y una bandeja "a revisar" para lo que no se entiende | UC-2, QA-7 |
| Proveedores | 2 proveedores de ejemplo, solo por webhook | Consulta periódica para los que no tienen webhooks, reintentos y corte ante proveedores caídos | UC-4, QA-1, QA-6 |
| Agencia de viajes | No está | Adaptador para abrir casos en la agencia preferida | UC-7 |
| Analítica | No está | Módulo Analytics Export y Data Warehouse separado | UC-8, CRN-2, ADR-0006 |
| App móvil y GPS | Solo una página web de demostración | App multiplataforma con caché local (primer contenido < 1,4 s) y ruta con GPS | QA-3, QA-11 |
| Avisos | Se muestran en la consola y en la página | Push real al celular | QA-1 |
| Internacionalización | Textos en español dentro del código; fechas sin zona horaria | Archivos de traducción, configuración por país, horas en UTC con zona horaria | QA-12 |
| Agrupación en viajes | Regla simple: reservas a menos de 2 días van juntas | Mejorar la regla (destinos, ida y vuelta) y permitir mover reservas de viaje a mano | UC-1, UC-6 |
| Alternativas del recomendador | Salen de un catálogo de ejemplo en cada adaptador | Buscarlas en las APIs de los proveedores, respetando sus límites (ADR-0011) | UC-9, QA-13 |
| Modelo general del recomendador | Pesos generales fijos en el código; los de cada usuario se ajustan en memoria | Reentrenamiento nocturno en el Data Warehouse con las respuestas pseudonimizadas de todos | CRN-3, ADR-0020 |
| Sugerencias de rutas | Solo se sugieren vuelos y hoteles alternativos ante una cancelación | Sugerir también rutas y opciones cuando se crea un viaje, no solo ante cancelaciones | UC-9 |

### Diferencia con la Wiki

- La Wiki dice que todo evento pasa por el outbox. En el prototipo lo hacen Itinerary, Sharing y Recommendation; los módulos de ingesta todavía publican directo (ver la primera tabla).
