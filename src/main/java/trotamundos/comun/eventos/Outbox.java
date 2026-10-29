package trotamundos.comun.eventos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Outbox (ADR-0010): el módulo guarda el evento junto con su cambio y un publicador lo envía al bus
 * después. Si la entrega falla, el evento queda pendiente y se reintenta en la próxima vuelta.
 * En el prototipo la "tabla outbox" está en memoria; en el diseño es una tabla de la base.
 */
public final class Outbox {

    private final Deque<Evento> pendientes = new ArrayDeque<>();
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
        pendientes.addLast(evento);
    }

    public synchronized int pendientes() {
        return pendientes.size();
    }

    public void detener() {
        publicador.shutdownNow();
    }

    private void publicarPendientes() {
        while (true) {
            Evento evento;
            synchronized (this) {
                evento = pendientes.peekFirst();
            }
            if (evento == null) {
                return;
            }
            try {
                bus.publicar(evento);
            } catch (RuntimeException e) {
                System.err.println("[outbox] Falló la entrega de " + evento.tipo() + "; se reintenta: " + e.getMessage());
                return;
            }
            synchronized (this) {
                pendientes.pollFirst();
            }
        }
    }
}
