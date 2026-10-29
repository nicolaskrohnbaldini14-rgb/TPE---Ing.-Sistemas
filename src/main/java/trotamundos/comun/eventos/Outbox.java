package trotamundos.comun.eventos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Outbox (ADR-0010): el módulo guarda el evento junto con su cambio y un publicador lo envía al bus
 * después. Si la entrega falla, el evento se reintenta en las vueltas siguientes; después de
 * {@link #MAXIMO_DE_INTENTOS} intentos pasa a "eventos fallidos" para revisarlo, así un evento con
 * problemas no traba a los que vienen atrás.
 * En el prototipo la "tabla outbox" está en memoria; en el diseño es una tabla de la base.
 */
public final class Outbox {

    public static final int MAXIMO_DE_INTENTOS = 5;

    private record Pendiente(Evento evento, int intentos) {
    }

    private final Deque<Pendiente> pendientes = new ArrayDeque<>();
    private final List<Evento> fallidos = new ArrayList<>();
    private final BusDeEventos bus;
    private final ScheduledExecutorService publicador;

    public Outbox(BusDeEventos bus) {
        this.bus = bus;
        this.publicador = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "publicador-outbox");
            hilo.setDaemon(true);
            return hilo;
        });
        publicador.scheduleWithFixedDelay(this::publicarPendientes, 100, 100, TimeUnit.MILLISECONDS);
    }

    public synchronized void guardar(Evento evento) {
        pendientes.addLast(new Pendiente(evento, 0));
    }

    public synchronized int pendientes() {
        return pendientes.size();
    }

    /** Eventos que fallaron {@link #MAXIMO_DE_INTENTOS} veces y quedaron apartados para revisar. */
    public synchronized List<Evento> fallidos() {
        return List.copyOf(fallidos);
    }

    public void detener() {
        publicador.shutdownNow();
    }

    /** Recorre una vez los eventos que estaban pendientes al empezar la vuelta. */
    private void publicarPendientes() {
        int enEstaVuelta;
        synchronized (this) {
            enEstaVuelta = pendientes.size();
        }
        for (int i = 0; i < enEstaVuelta; i++) {
            Pendiente pendiente;
            synchronized (this) {
                pendiente = pendientes.pollFirst();
            }
            if (pendiente == null) {
                return;
            }
            try {
                bus.publicar(pendiente.evento());
            } catch (RuntimeException e) {
                reintentarOApartar(pendiente, e);
            }
        }
    }

    private synchronized void reintentarOApartar(Pendiente pendiente, RuntimeException error) {
        int intentos = pendiente.intentos() + 1;
        if (intentos >= MAXIMO_DE_INTENTOS) {
            fallidos.add(pendiente.evento());
            System.err.println("[outbox] " + pendiente.evento().tipo() + " falló " + intentos
                    + " veces; pasa a eventos fallidos: " + error.getMessage());
            return;
        }
        // Va al final: los eventos que vienen atrás se siguen publicando mientras tanto.
        pendientes.addLast(new Pendiente(pendiente.evento(), intentos));
        System.err.println("[outbox] Falló la entrega de " + pendiente.evento().tipo()
                + " (intento " + intentos + "); se reintenta: " + error.getMessage());
    }
}
