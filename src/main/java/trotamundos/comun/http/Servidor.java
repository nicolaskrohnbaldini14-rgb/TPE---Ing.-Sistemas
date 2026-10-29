package trotamundos.comun.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import trotamundos.comun.json.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Punto de entrada HTTP del monolito. Cumple el rol del API Gateway del diseño:
 * cada módulo registra acá sus rutas desde su capa api.
 */
public final class Servidor {

    public record Pedido(String metodo, String ruta, Map<String, String> parametros,
                         Map<String, String> consulta, String usuario, Map<String, Object> cuerpo) {

        public String parametro(String nombre) {
            return parametros.get(nombre);
        }

        /** Simula el login: el usuario viene en el encabezado X-Usuario en lugar de un token OIDC. */
        public String requerirUsuario() {
            if (usuario == null || usuario.isBlank()) {
                throw new ErrorHttp(401, "Falta el encabezado X-Usuario (simula el login)");
            }
            return usuario.trim().toLowerCase();
        }

        public String texto(String campo) {
            Object valor = cuerpo.get(campo);
            if (valor == null || valor.toString().isBlank()) {
                throw new ErrorHttp(400, "Falta el campo '" + campo + "'");
            }
            return valor.toString();
        }
    }

    public record Resultado(int estado, Object cuerpo) {
        public static Resultado ok(Object cuerpo) {
            return new Resultado(200, cuerpo);
        }

        public static Resultado creado(Object cuerpo) {
            return new Resultado(201, cuerpo);
        }

        public static Resultado aceptado(Object cuerpo) {
            return new Resultado(202, cuerpo);
        }

        public static Resultado sinContenido() {
            return new Resultado(204, null);
        }
    }

    public static final class ErrorHttp extends RuntimeException {
        private final int estado;

        public ErrorHttp(int estado, String mensaje) {
            super(mensaje);
            this.estado = estado;
        }

        public int estado() {
            return estado;
        }
    }

    @FunctionalInterface
    public interface Manejador {
        Resultado manejar(Pedido pedido);
    }

    private record Ruta(String metodo, String[] partes, Manejador manejador) {
    }

    private final List<Ruta> rutas = new ArrayList<>();
    private final Path carpetaWeb;
    private HttpServer http;
    private ExecutorService hilos;

    public Servidor(Path carpetaWeb) {
        this.carpetaWeb = carpetaWeb.toAbsolutePath().normalize();
    }

    public void agregar(String metodo, String patron, Manejador manejador) {
        rutas.add(new Ruta(metodo, partir(patron), manejador));
    }

    /** Arranca el servidor y devuelve el puerto real (sirve pasar 0 para que elija uno libre). */
    public int iniciar(int puerto) throws IOException {
        http = HttpServer.create(new InetSocketAddress(puerto), 0);
        http.createContext("/", this::atender);
        hilos = Executors.newFixedThreadPool(8);
        http.setExecutor(hilos);
        http.start();
        return http.getAddress().getPort();
    }

    public void detener() {
        if (http != null) {
            http.stop(0);
            hilos.shutdownNow();
        }
    }

    private void atender(HttpExchange intercambio) throws IOException {
        try {
            String metodo = intercambio.getRequestMethod();
            String ruta = intercambio.getRequestURI().getPath();
            if ("GET".equals(metodo) && !ruta.startsWith("/v1/")) {
                servirArchivo(intercambio, ruta);
                return;
            }
            for (Ruta r : rutas) {
                Map<String, String> parametros = coincide(r, metodo, ruta);
                if (parametros == null) {
                    continue;
                }
                String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                Pedido pedido = new Pedido(metodo, ruta, parametros,
                        consulta(intercambio.getRequestURI().getRawQuery()),
                        intercambio.getRequestHeaders().getFirst("X-Usuario"),
                        Json.objeto(cuerpo));
                Resultado resultado = r.manejador().manejar(pedido);
                responder(intercambio, resultado.estado(), resultado.cuerpo());
                return;
            }
            responder(intercambio, 404, error("No existe la ruta " + metodo + " " + ruta));
        } catch (ErrorHttp e) {
            responder(intercambio, e.estado(), error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            responder(intercambio, 400, error(e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            responder(intercambio, 500, error("Error interno: " + e.getMessage()));
        }
    }

    private static Map<String, Object> error(String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", String.valueOf(mensaje));
        return cuerpo;
    }

    private static String[] partir(String ruta) {
        return java.util.Arrays.stream(ruta.split("/")).filter(p -> !p.isEmpty()).toArray(String[]::new);
    }

    private static Map<String, String> coincide(Ruta ruta, String metodo, String camino) {
        if (!ruta.metodo().equals(metodo)) {
            return null;
        }
        String[] partes = partir(camino);
        if (partes.length != ruta.partes().length) {
            return null;
        }
        Map<String, String> parametros = new HashMap<>();
        for (int i = 0; i < partes.length; i++) {
            String esperado = ruta.partes()[i];
            String valor = URLDecoder.decode(partes[i], StandardCharsets.UTF_8);
            if (esperado.startsWith("{") && esperado.endsWith("}")) {
                parametros.put(esperado.substring(1, esperado.length() - 1), valor);
            } else if (!esperado.equals(valor)) {
                return null;
            }
        }
        return parametros;
    }

    private static Map<String, String> consulta(String consulta) {
        Map<String, String> valores = new HashMap<>();
        if (consulta == null || consulta.isBlank()) {
            return valores;
        }
        for (String par : consulta.split("&")) {
            String[] clave = par.split("=", 2);
            valores.put(URLDecoder.decode(clave[0], StandardCharsets.UTF_8),
                    clave.length > 1 ? URLDecoder.decode(clave[1], StandardCharsets.UTF_8) : "");
        }
        return valores;
    }

    private void servirArchivo(HttpExchange intercambio, String ruta) throws IOException {
        String nombre = ruta.equals("/") ? "index.html" : ruta.substring(1);
        Path archivo = carpetaWeb.resolve(nombre).normalize();
        if (!archivo.startsWith(carpetaWeb) || !Files.isRegularFile(archivo)) {
            responder(intercambio, 404, error("No existe " + ruta));
            return;
        }
        String tipo = nombre.endsWith(".html") ? "text/html; charset=utf-8"
                : nombre.endsWith(".css") ? "text/css; charset=utf-8"
                : nombre.endsWith(".js") ? "text/javascript; charset=utf-8"
                : "application/octet-stream";
        enviar(intercambio, 200, tipo, Files.readAllBytes(archivo));
    }

    private static void responder(HttpExchange intercambio, int estado, Object cuerpo) throws IOException {
        if (cuerpo == null) {
            intercambio.sendResponseHeaders(estado, -1);
            intercambio.close();
            return;
        }
        enviar(intercambio, estado, "application/json; charset=utf-8",
                Json.escribir(cuerpo).getBytes(StandardCharsets.UTF_8));
    }

    private static void enviar(HttpExchange intercambio, int estado, String tipo, byte[] datos) throws IOException {
        intercambio.getResponseHeaders().set("Content-Type", tipo);
        intercambio.sendResponseHeaders(estado, datos.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(datos);
        }
    }
}
