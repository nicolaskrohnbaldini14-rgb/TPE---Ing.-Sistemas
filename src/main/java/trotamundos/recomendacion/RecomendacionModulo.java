package trotamundos.recomendacion;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.comun.eventos.Outbox;
import trotamundos.comun.http.Servidor;
import trotamundos.ingesta.partnerintegration.PartnerIntegrationModulo;
import trotamundos.recomendacion.api.RecomendacionApi;
import trotamundos.recomendacion.datos.RecomendacionRepositorio;
import trotamundos.recomendacion.negocio.PuntajePorPreferencias;
import trotamundos.recomendacion.negocio.ServicioRecomendacion;

/**
 * Módulo Recommendation (Iteración 5). Consume ReservaActualizada; publica SugerenciaGenerada y
 * SugerenciaRespondida por el outbox. Usa a Partner Integration solo por buscarAlternativas().
 */
public final class RecomendacionModulo {

    public RecomendacionModulo(Servidor servidor, BusDeEventos bus, Outbox outbox, PartnerIntegrationModulo proveedores) {
        // El algoritmo entra por la interfaz Recomendador: cambiarlo es cambiar esta línea (QA-14).
        ServicioRecomendacion servicio = new ServicioRecomendacion(
                new RecomendacionRepositorio(), new PuntajePorPreferencias(), proveedores, outbox);
        new RecomendacionApi(servicio).registrar(servidor);
        bus.suscribir(Evento.RESERVA_ACTUALIZADA, servicio::alActualizarReserva);
    }
}
