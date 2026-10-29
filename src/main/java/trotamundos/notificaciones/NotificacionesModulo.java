package trotamundos.notificaciones;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.comun.http.Servidor;
import trotamundos.comun.http.Servidor.Resultado;
import trotamundos.comun.modelo.RegistroDeTipos;
import trotamundos.notificaciones.datos.NotificacionesRepositorio;
import trotamundos.notificaciones.negocio.ServicioNotificaciones;

import java.util.Map;

/** Módulo Notification: consume ReservaActualizada. Su única ruta es la de lectura de avisos. */
public final class NotificacionesModulo {

    public NotificacionesModulo(Servidor servidor, BusDeEventos bus, RegistroDeTipos tipos) {
        ServicioNotificaciones servicio = new ServicioNotificaciones(new NotificacionesRepositorio(), tipos);
        bus.suscribir(Evento.RESERVA_ACTUALIZADA, servicio::alActualizarReserva);
        // Capa api: con una sola ruta no hace falta una clase aparte.
        servidor.agregar("GET", "/v1/notificaciones", p ->
                Resultado.ok(Map.of("avisos", servicio.avisosDe(p.requerirUsuario()))));
    }
}
