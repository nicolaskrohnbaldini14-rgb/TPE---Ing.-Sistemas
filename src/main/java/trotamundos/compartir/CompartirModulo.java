package trotamundos.compartir;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.comun.eventos.Outbox;
import trotamundos.comun.http.Servidor;
import trotamundos.compartir.api.CompartirApi;
import trotamundos.compartir.datos.PermisosRepositorio;
import trotamundos.compartir.negocio.ServicioCompartir;

/**
 * Módulo Sharing. Su interfaz pública para otros módulos es {@link #puedeVer(String, String)}.
 * Consume ReservaActualizada para saber quién es el dueño de cada viaje; publica PermisoCambiado
 * a través del outbox.
 */
public final class CompartirModulo {

    private final ServicioCompartir servicio;

    public CompartirModulo(Servidor servidor, BusDeEventos bus, Outbox outbox) {
        servicio = new ServicioCompartir(new PermisosRepositorio(), outbox);
        new CompartirApi(servicio).registrar(servidor);
        bus.suscribir(Evento.RESERVA_ACTUALIZADA, e -> servicio.registrarDuenio(e.texto("viajeId"), e.texto("usuario")));
    }

    public boolean puedeVer(String usuario, String viajeId) {
        return servicio.puedeVer(usuario, viajeId);
    }
}
