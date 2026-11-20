package trotamundos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prueba de carga del panel (evidencia para QA-2 y, en parte, QA-5). Mide el tiempo de respuesta de
 * GET /v1/panel con muchos pedidos en paralelo: primero solo con lecturas y después mientras entran
 * webhooks de proveedores, para ver si la ingesta afecta la lectura (gracias al CQRS, no debería).
 * Es una medición en una sola computadora, no en el entorno real: sirve como orden de magnitud.
 */
public final class PruebaCarga {

    private static final int USUARIOS = 200;
    private static final int PEDIDOS = 10_000;
    private static final int CONCURRENCIA = 50;
    private static final long LIMITE_P95_MS = 800;

    private static final HttpClient CLIENTE = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static String base;

    public static void main(String[] args) throws Exception {
        Trotamundos app = Trotamundos.iniciar(0, Path.of("web"));
        base = "http://localhost:" + app.puerto();
        try {
            System.out.println("Cargando " + USUARIOS + " usuarios con un vuelo y un hotel cada uno...");
            for (int i = 0; i < USUARIOS; i++) {
                webhook("aerolinea-demo", vuelo(i, "CONFIRMED"));
                webhook("hotel-demo", hotel(i));
            }
            esperarPanelListo();

            List<Long> soloLectura = medirLecturas(false);
            List<Long> conIngesta = medirLecturas(true);

            System.out.println();
            System.out.println("| Escenario | Pedidos | p50 | p95 | p99 | Máximo |");
            System.out.println("|---|---|---|---|---|---|");
            imprimir("Solo lecturas", soloLectura);
            imprimir("Lecturas mientras entran webhooks", conIngesta);

            long p95Solo = percentil(soloLectura, 95);
            long p95Ingesta = percentil(conIngesta, 95);
            System.out.println();
            if (p95Solo > LIMITE_P95_MS || p95Ingesta > LIMITE_P95_MS) {
                System.out.println("FALLÓ: el p95 supera los " + LIMITE_P95_MS + " ms de QA-2");
                System.exit(1);
            }
            System.out.println("OK  el p95 del panel queda por debajo de los " + LIMITE_P95_MS + " ms de QA-2 en los dos escenarios");
        } finally {
            app.detener();
        }
    }

    /** Pide el panel PEDIDOS veces con CONCURRENCIA hilos; si conIngesta, en paralelo entran webhooks. */
    private static List<Long> medirLecturas(boolean conIngesta) throws Exception {
        List<Long> tiempos = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean seguir = new AtomicBoolean(true);
        AtomicInteger webhooks = new AtomicInteger();
        ExecutorService ingesta = Executors.newFixedThreadPool(5);
        if (conIngesta) {
            for (int h = 0; h < 5; h++) {
                ingesta.submit(() -> {
                    while (seguir.get()) {
                        int usuario = ThreadLocalRandom.current().nextInt(USUARIOS);
                        webhook("aerolinea-demo", vuelo(usuario, webhooks.incrementAndGet() % 2 == 0 ? "CHANGED" : "CONFIRMED"));
                    }
                    return null;
                });
            }
        }
        ExecutorService lectores = Executors.newFixedThreadPool(CONCURRENCIA);
        List<Future<?>> tareas = new ArrayList<>();
        long inicio = System.nanoTime();
        for (int i = 0; i < PEDIDOS; i++) {
            tareas.add(lectores.submit(() -> {
                String usuario = "usuario" + ThreadLocalRandom.current().nextInt(USUARIOS) + "@mail.com";
                HttpRequest pedido = HttpRequest.newBuilder(URI.create(base + "/v1/panel")).header("X-Usuario", usuario).GET().build();
                long antes = System.nanoTime();
                HttpResponse<String> respuesta = CLIENTE.send(pedido, HttpResponse.BodyHandlers.ofString());
                tiempos.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - antes));
                if (respuesta.statusCode() != 200) {
                    throw new IllegalStateException("El panel respondió " + respuesta.statusCode());
                }
                return null;
            }));
        }
        for (Future<?> tarea : tareas) {
            tarea.get();
        }
        double segundos = (System.nanoTime() - inicio) / 1e9;
        seguir.set(false);
        lectores.shutdown();
        ingesta.shutdown();
        ingesta.awaitTermination(10, TimeUnit.SECONDS);
        System.out.printf("%s: %d lecturas en %.1f s (%.0f por segundo)%s%n",
                conIngesta ? "Con ingesta" : "Solo lecturas", PEDIDOS, segundos, PEDIDOS / segundos,
                conIngesta ? ", mientras entraban " + webhooks.get() + " webhooks" : "");
        return tiempos;
    }

    private static void esperarPanelListo() throws Exception {
        String ultimo = "usuario" + (USUARIOS - 1) + "@mail.com";
        for (int intento = 0; intento < 100; intento++) {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(base + "/v1/panel")).header("X-Usuario", ultimo).GET().build();
            if (CLIENTE.send(pedido, HttpResponse.BodyHandlers.ofString()).body().contains("hotel-demo.com")) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Los paneles no se terminaron de armar");
    }

    private static void webhook(String proveedor, String cuerpo) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(base + "/v1/webhooks/" + proveedor))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        CLIENTE.send(pedido, HttpResponse.BodyHandlers.discarding());
    }

    private static String vuelo(int usuario, String estado) {
        return "{\"pnr\":\"P" + usuario + "\",\"pasajero_email\":\"usuario" + usuario + "@mail.com\",\"vuelo\":\"AR" + (1000 + usuario)
                + "\",\"desde\":\"EZE\",\"hacia\":\"MAD\",\"salida\":\"2026-12-01T10:00\",\"llegada\":\"2026-12-02T04:00\",\"estado\":\"" + estado + "\"}";
    }

    private static String hotel(int usuario) {
        return "{\"booking_ref\":\"H" + usuario + "\",\"guest\":\"usuario" + usuario + "@mail.com\",\"hotel_name\":\"Gran Vía\",\"city\":\"MAD\""
                + ",\"check_in\":\"2026-12-02T15:00\",\"check_out\":\"2026-12-06T11:00\",\"status\":\"OK\"}";
    }

    private static void imprimir(String escenario, List<Long> tiempos) {
        System.out.printf("| %s | %d | %d ms | %d ms | %d ms | %d ms |%n", escenario, tiempos.size(),
                percentil(tiempos, 50), percentil(tiempos, 95), percentil(tiempos, 99), percentil(tiempos, 100));
    }

    private static long percentil(List<Long> tiempos, int percentil) {
        List<Long> ordenados = new ArrayList<>(tiempos);
        Collections.sort(ordenados);
        int indice = (int) Math.ceil(percentil / 100.0 * ordenados.size()) - 1;
        return ordenados.get(Math.max(0, Math.min(indice, ordenados.size() - 1)));
    }
}
