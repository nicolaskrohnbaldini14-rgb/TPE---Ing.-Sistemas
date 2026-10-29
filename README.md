# Trotamundos – Prototipo (1ª entrega)

Prototipo del **monolito modular** diseñado en la Wiki (ADR-0001). Muestra el flujo principal funcionando de punta a punta:

> una reserva llega por **webhook de un proveedor** o por **correo** → se traduce al **modelo común** → **Itinerary** la guarda, evita duplicados y la agrupa en un viaje → el evento sale por el **outbox** → el **panel** y los **avisos** se actualizan solos.

Además: carga manual, compartir un viaje solo con personas designadas, y archivado de viajes terminados.

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

**Prueba de punta a punta** (levanta el sistema, recorre los flujos por HTTP y verifica 19 cosas):

```powershell
.\probar.ps1
```

### Demo sugerida para la entrega

En la página, con el usuario `ana@mail.com`:

1. **La aerolínea confirma un vuelo**: aparece "Viaje a MAD" con el vuelo y un aviso.
2. **Llega el correo de un hotel**: el hotel se suma al **mismo viaje** (por cercanía de fechas).
3. **Llega un correo que no es de viajes**: se descarta por el filtro de whitelist.
4. **La aerolínea cancela el vuelo**: el vuelo queda tachado y llega el aviso "Se canceló tu vuelo".
5. **Compartir con beto@mail.com**: cambiar el usuario a `beto@mail.com` y el viaje aparece en "Compartidos conmigo".
6. **Pasó el viaje: archivar**: con `ana@mail.com`, el viaje pasa a "Archivados".

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
│   └── notificaciones/             ← avisos al usuario
│       ├── negocio/  datos/
├── src/test/java/trotamundos/PruebaFlujo.java   ← prueba de punta a punta
├── web/index.html                  ← página mínima para mostrar el prototipo
├── openapi.yaml                    ← contrato de la API (spec-driven)
└── compilar / ejecutar / probar    ← scripts .ps1 (Windows) y .sh (Linux/macOS)
```

### Qué hace cada módulo y de qué decisión sale

| Módulo | Qué hace en el prototipo | Publica | Consume | Decisión de la Wiki |
|---|---|---|---|---|
| **Mail Connector** | Recibe el aviso de correo nuevo y deja pasar solo remitentes de la whitelist o del catálogo de viajes | `CorreoDeViajeRecibido` | — | ADR-0003 |
| **Email Parser** | Extrae vuelo u hotel de correos con plantilla "Campo: valor" | `ReservaDetectada` | `CorreoDeViajeRecibido` | ADR-0003, ADR-0004 |
| **Partner Integration** | Webhooks de 2 proveedores de ejemplo (aerolínea y hotel), cada uno con su adaptador y su formato | `ReservaDetectada` | — | ADR-0004 |
| **Itinerary** | Evita duplicados por código de confirmación, agrupa en viajes (±2 días), carga manual, archiva | `ReservaActualizada`, `ViajeArchivado` (por outbox) | `ReservaDetectada` | ADR-0001, ADR-0010 |
| **Dashboard Query** | Mantiene el panel ya armado de cada usuario; ignora eventos repetidos o viejos por versión | — | `ReservaActualizada`, `ViajeArchivado`, `PermisoCambiado` | ADR-0005 |
| **Sharing** | Permisos por persona designada; `puedeVer()` es su interfaz pública | `PermisoCambiado` | `ReservaActualizada` (para saber el dueño) | ADR-0009 |
| **Notification** | Arma el aviso de cada cambio y lo "envía" (consola) | — | `ReservaActualizada` | ADR-0005 |
| **Común** | Modelo común, tipos de reserva como plugins, bus, outbox | — | — | ADR-0001, ADR-0004, ADR-0010 |

### Cómo se respetan las reglas del monolito modular (ADR-0001)

- **Un solo desplegable:** `Trotamundos.java` arma todos los módulos en un mismo programa.
- **Se comunican por eventos o por interfaz pública:** el único llamado directo entre módulos es `panel → compartir.puedeVer()`, igual que en la vista de módulos. Todo lo demás pasa por el bus.
- **Cada módulo tiene sus tablas:** cada uno tiene su propio repositorio en `datos/` y nadie lee el de otro.
- **Capas dentro de cada módulo:** `api` (endpoints) → `negocio` (reglas) → `datos` (repositorio).
- **Outbox:** Itinerary guarda el cambio y su evento juntos, y un hilo aparte los publica y reintenta si falla.
- **Agregar un medio de transporte (QA-10):** crear una clase en `comun/modelo/tipos/` (por ejemplo `Tren`) y registrarla en `Trotamundos.java`. No se toca nada más.

---

## Aclaraciones: qué queda para la 2ª entrega

Este prototipo muestra **la estructura y el flujo principal**, no todo el sistema. Estas son las simplificaciones que hicimos y lo que hay que refinar:

### Simplificaciones de infraestructura

| Tema | Cómo está ahora | Qué falta para la 2ª entrega | Driver / ADR |
|---|---|---|---|
| Persistencia | Todo en memoria: se pierde al reiniciar | Base de datos (por ej. PostgreSQL) con un esquema por módulo; outbox como tabla en la misma transacción | ADR-0001, ADR-0010 |
| Outbox | Solo lo usa Itinerary; Mail Connector, Partner Integration y Sharing publican directo en el bus | Que todos los módulos que cambian datos publiquen por outbox | QA-5, ADR-0010 |
| Login | Simulado con el encabezado `X-Usuario` | Login con proveedor de identidad (OIDC) y token en cada pedido; API Gateway real | QA-8, QA-9 |
| Réplicas, zonas y regiones | Una sola instancia | Contenedores con autoescalado, base replicada, rolling update, varias regiones | QA-4, QA-5, ADR-0002, ADR-0007 |
| Pruebas | Una prueba de punta a punta hecha a mano | JUnit, pruebas de contrato contra `openapi.yaml`, prueba de carga para medir QA-2 y QA-5 | QA-2, QA-5 |
| Fronteras entre módulos | Se respetan por disciplina | Hacerlas cumplir automáticamente (módulos de Java o reglas tipo ArchUnit) | ADR-0001 |

### Funcionalidad incompleta

| Tema | Cómo está ahora | Qué falta para la 2ª entrega | Driver / ADR |
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
| Módulo avanzado | No está | Recomendador o Chatbot, como un módulo más que consume `ReservaActualizada` | 2ª entrega |

### Diferencia con la Wiki

- La Wiki dice que todo evento pasa por el outbox. En el prototipo solo lo hace Itinerary (ver la primera tabla).
