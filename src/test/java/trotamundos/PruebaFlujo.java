package trotamundos;

import trotamundos.comun.json.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Prueba de punta a punta del prototipo: levanta el monolito en un puerto libre y recorre los flujos
 * principales por HTTP. Se corre con probar.ps1 / probar.sh. Termina con código 1 si algo falla.
 */
public final class PruebaFlujo {

    private static final String ANA = "ana@mail.com";
    private static final String BETO = "beto@mail.com";

    private static HttpClient cliente;
    private static String base;
    private static int pasadas;

    public static void main(String[] args) throws Exception {
        Trotamundos app = Trotamundos.iniciar(0, Path.of("web"));
        cliente = HttpClient.newHttpClient();
        base = "http://localhost:" + app.puerto();
        try {
            probar();
            System.out.println("\nTodas las pruebas pasaron (" + pasadas + " verificaciones).");
        } catch (AssertionError e) {
            System.out.println("\nFALLÓ: " + e.getMessage());
            System.exit(1);
        } finally {
            app.detener();
        }
    }

    private static void probar() throws Exception {
        // UC-4 + QA-6: la aerolínea confirma un vuelo por webhook, en su propio formato.
        int estado = post("/v1/webhooks/aerolinea-demo", null, """
                {"pnr":"ABC123","pasajero_email":"ana@mail.com","vuelo":"AR1132","desde":"EZE","hacia":"MAD",
                 "salida":"2026-12-01T10:00","llegada":"2026-12-02T04:00","estado":"CONFIRMED"}""").statusCode();
        verificar(estado == 202, "el webhook de la aerolínea se acepta (202)");

        // UC-2 + UC-3: llega un correo de un hotel del catálogo; pasa el filtro y se parsea.
        Map<String, Object> correo = cuerpo(post("/v1/correos/aviso", null, """
                {"buzon":"ana@mail.com","remitente":"reservas@hotel-demo.com","asunto":"Tu reserva",
                 "cuerpo":"Código: H-778\\nHotel: Gran Vía\\nCiudad: MAD\\nEntrada: 2026-12-02T15:00\\nSalida: 2026-12-06T11:00"}"""));
        verificar(Boolean.TRUE.equals(correo.get("procesado")), "el correo del hotel pasa el filtro");

        Map<String, Object> spam = cuerpo(post("/v1/correos/aviso", null, """
                {"buzon":"ana@mail.com","remitente":"ofertas@spam.com","asunto":"Promo","cuerpo":"Comprá ya"}"""));
        verificar(Boolean.FALSE.equals(spam.get("procesado")), "un correo que no es de viajes queda filtrado");

        // UC-1 + UC-6: el panel muestra un solo viaje con el vuelo y el hotel juntos.
        Map<String, Object> panel = esperarPanel(ANA, p -> viajes(p).size() == 1 && reservas(viajes(p).get(0)).size() == 2);
        String viajeId = String.valueOf(viajes(panel).get(0).get("id"));
        verificar(true, "el panel agrupa vuelo y hotel en un mismo viaje (" + viajes(panel).get(0).get("nombre") + ")");

        // QA-1: la aerolínea cancela; el panel y las notificaciones se actualizan.
        String cancelacion = """
                {"pnr":"ABC123","pasajero_email":"ana@mail.com","vuelo":"AR1132","desde":"EZE","hacia":"MAD",
                 "salida":"2026-12-01T10:00","llegada":"2026-12-02T04:00","estado":"CANCELLED"}""";
        post("/v1/webhooks/aerolinea-demo", null, cancelacion);
        esperarPanel(ANA, p -> reservas(viajes(p).get(0)).stream().anyMatch(r -> "CANCELADA".equals(r.get("estado"))));
        verificar(true, "la cancelación llega al panel");
        verificar(String.valueOf(cuerpo(get("/v1/notificaciones", ANA)).get("avisos")).contains("Se canceló tu vuelo AR1132"),
                "se avisa la cancelación al usuario");

        // Idempotencia: el mismo webhook repetido no duplica nada.
        post("/v1/webhooks/aerolinea-demo", null, cancelacion);
        Thread.sleep(300);
        verificar(reservas(viajes(cuerpo(get("/v1/panel", ANA))).get(0)).size() == 2, "un webhook repetido no duplica la reserva");

        // UC-5: carga manual de un auto en las mismas fechas.
        HttpResponse<String> manual = post("/v1/reservas", ANA, """
                {"tipo":"auto","codigoConfirmacion":"AUTO-9","inicio":"2026-12-03T09:00","fin":"2026-12-05T18:00",
                 "destino":"MAD","detalles":{"rentadora":"RentaDemo"}}""");
        verificar(manual.statusCode() == 201, "la carga manual responde 201 con la versión");
        esperarPanel(ANA, p -> reservas(viajes(p).get(0)).size() == 3);
        verificar(true, "la reserva manual aparece en el mismo viaje");

        HttpResponse<String> crucero = post("/v1/reservas", ANA, """
                {"tipo":"crucero","inicio":"2026-12-03T09:00"}""");
        verificar(crucero.statusCode() == 400, "un tipo de reserva no registrado se rechaza (400)");

        // QA-9: compartir solo con personas designadas.
        verificar(get("/v1/panel/viajes/" + viajeId, BETO).statusCode() == 403, "una persona no designada no ve el viaje (403)");
        verificar(post("/v1/viajes/" + viajeId + "/compartir", BETO, "{\"email\":\"carla@mail.com\"}").statusCode() == 403,
                "solo el dueño puede compartir (403)");
        verificar(post("/v1/viajes/" + viajeId + "/compartir", ANA, "{\"email\":\"beto@mail.com\"}").statusCode() == 201,
                "la dueña comparte el viaje con Beto (201)");
        verificar(get("/v1/panel/viajes/" + viajeId, BETO).statusCode() == 200, "Beto ya puede ver el viaje (200)");
        verificar(((List<?>) cuerpo(get("/v1/panel", BETO)).get("compartidosConmigo")).size() == 1,
                "el viaje aparece en 'compartidos conmigo' de Beto");
        verificar(enviar("DELETE", "/v1/viajes/" + viajeId + "/compartir/beto@mail.com", ANA, null).statusCode() == 204,
                "la dueña revoca el permiso (204)");
        verificar(get("/v1/panel/viajes/" + viajeId, BETO).statusCode() == 403, "después de revocar, Beto ya no lo ve (403)");

        // UC-6: al pasar la fecha del viaje, se archiva.
        verificar(cuerpo(post("/v1/admin/archivar?ahora=2027-01-01T00:00", null, "")).get("archivados").toString().equals("1"),
                "el viaje terminado se archiva");
        esperarPanel(ANA, p -> viajes(p).isEmpty() && ((List<?>) p.get("archivados")).size() == 1);
        verificar(true, "el viaje pasa a la sección de archivados del panel");
    }

    // ----- ayudas -----

    private static void verificar(boolean condicion, String descripcion) {
        if (!condicion) {
            throw new AssertionError(descripcion);
        }
        pasadas++;
        System.out.println("OK  " + descripcion);
    }

    private static Map<String, Object> esperarPanel(String usuario, Predicate<Map<String, Object>> condicion) throws Exception {
        for (int intento = 0; intento < 30; intento++) {
            Map<String, Object> panel = cuerpo(get("/v1/panel", usuario));
            try {
                if (condicion.test(panel)) {
                    return panel;
                }
            } catch (IndexOutOfBoundsException e) {
                // todavía no llegaron los eventos
            }
            Thread.sleep(100);
        }
        throw new AssertionError("el panel de " + usuario + " no llegó al estado esperado: " + cuerpo(get("/v1/panel", usuario)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> viajes(Map<String, Object> panel) {
        return (List<Map<String, Object>>) panel.get("viajes");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> reservas(Map<String, Object> viaje) {
        return (List<Map<String, Object>>) viaje.get("reservas");
    }

    private static Map<String, Object> cuerpo(HttpResponse<String> respuesta) {
        return Json.objeto(respuesta.body());
    }

    private static HttpResponse<String> get(String ruta, String usuario) throws Exception {
        return enviar("GET", ruta, usuario, null);
    }

    private static HttpResponse<String> post(String ruta, String usuario, String json) throws Exception {
        return enviar("POST", ruta, usuario, json);
    }

    private static HttpResponse<String> enviar(String metodo, String ruta, String usuario, String json) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base + ruta))
                .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json");
        if (usuario != null) {
            pedido.header("X-Usuario", usuario);
        }
        return cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }
}
