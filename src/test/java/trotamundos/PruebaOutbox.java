package trotamundos;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.comun.eventos.Outbox;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Prueba del outbox (ADR-0010): un evento que falla siempre no tiene que trabar a los que vienen
 * atrás, y después de varios intentos tiene que quedar apartado como fallido.
 */
public final class PruebaOutbox {

    public static void main(String[] args) throws Exception {
        BusDeEventos bus = new BusDeEventos();
        List<String> entregados = new CopyOnWriteArrayList<>();
        bus.suscribir("Roto", e -> {
            throw new IllegalStateException("el consumidor falla siempre");
        });
        bus.suscribir("Sano", e -> entregados.add(e.texto("n")));

        Outbox outbox = new Outbox(bus);
        try {
            outbox.guardar(Evento.nuevo("Roto", Map.of("n", "0")));
            outbox.guardar(Evento.nuevo("Sano", Map.of("n", "1")));
            outbox.guardar(Evento.nuevo("Sano", Map.of("n", "2")));

            esperar(() -> entregados.size() == 2);
            verificar(entregados.equals(List.of("1", "2")), "los eventos que vienen atrás de uno que falla se publican igual");

            esperar(() -> outbox.fallidos().size() == 1);
            verificar(outbox.fallidos().get(0).tipo().equals("Roto"),
                    "después de " + Outbox.MAXIMO_DE_INTENTOS + " intentos, el evento roto pasa a fallidos");
            verificar(outbox.pendientes() == 0, "el outbox queda vacío: nada queda trabado");
            System.out.println("\nPrueba del outbox OK.");
        } catch (AssertionError e) {
            System.out.println("\nFALLÓ: " + e.getMessage());
            System.exit(1);
        } finally {
            outbox.detener();
        }
    }

    private static void esperar(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        for (int i = 0; i < 50 && !condicion.getAsBoolean(); i++) {
            Thread.sleep(100);
        }
    }

    private static void verificar(boolean condicion, String descripcion) {
        if (!condicion) {
            throw new AssertionError(descripcion);
        }
        System.out.println("OK  " + descripcion);
    }
}
