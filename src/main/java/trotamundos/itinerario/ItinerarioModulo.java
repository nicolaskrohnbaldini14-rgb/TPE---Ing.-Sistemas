package trotamundos.itinerario;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.comun.eventos.Outbox;
import trotamundos.comun.http.Servidor;
import trotamundos.comun.modelo.RegistroDeTipos;
import trotamundos.comun.modelo.Reserva;
import trotamundos.itinerario.api.ItinerarioApi;
import trotamundos.itinerario.datos.ItinerarioRepositorio;
import trotamundos.itinerario.negocio.ServicioItinerario;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Módulo Itinerary: fuente de verdad de viajes y reservas.
 * Consume ReservaDetectada; publica ReservaActualizada y ViajeArchivado a través del outbox.
 */
public final class ItinerarioModulo {

    private final ScheduledExecutorService archivador;

    public ItinerarioModulo(Servidor servidor, BusDeEventos bus, Outbox outbox, RegistroDeTipos tipos) {
        ServicioItinerario servicio = new ServicioItinerario(new ItinerarioRepositorio(), outbox, tipos);
        new ItinerarioApi(servicio).registrar(servidor);
        bus.suscribir(Evento.RESERVA_DETECTADA, e -> servicio.registrar(Reserva.desdeMapa(e.mapa("reserva"))));

        archivador = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "archivador-de-viajes");
            hilo.setDaemon(true);
            return hilo;
        });
        archivador.scheduleWithFixedDelay(() -> servicio.archivarTerminados(LocalDateTime.now()), 1, 1, TimeUnit.MINUTES);
    }

    public void detener() {
        archivador.shutdownNow();
    }
}
