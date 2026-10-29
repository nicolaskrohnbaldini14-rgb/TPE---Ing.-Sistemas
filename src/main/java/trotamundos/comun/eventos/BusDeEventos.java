package trotamundos.comun.eventos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Bus de eventos interno del monolito (patrón publicador-suscriptor).
 * Los módulos se comunican por acá sin depender unos de otros.
 */
public final class BusDeEventos {

    private final Map<String, List<Consumer<Evento>>> suscriptores = new ConcurrentHashMap<>();

    public void suscribir(String tipo, Consumer<Evento> suscriptor) {
        suscriptores.computeIfAbsent(tipo, t -> new CopyOnWriteArrayList<>()).add(suscriptor);
    }

    /**
     * Entrega el evento a todos los suscriptores de su tipo. Si alguno falla, la excepción sube
     * para que quien publicó (por ejemplo, el outbox) pueda reintentar.
     */
    public void publicar(Evento evento) {
        for (Consumer<Evento> suscriptor : suscriptores.getOrDefault(evento.tipo(), List.of())) {
            suscriptor.accept(evento);
        }
    }
}
