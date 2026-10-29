package trotamundos.notificaciones.negocio;

import trotamundos.comun.eventos.Evento;
import trotamundos.comun.modelo.RegistroDeTipos;
import trotamundos.comun.modelo.Reserva;
import trotamundos.notificaciones.datos.NotificacionesRepositorio;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Avisa al usuario cada cambio de sus reservas (cierra QA-1: el cambio llega a la persona). */
public final class ServicioNotificaciones {

    private final NotificacionesRepositorio repositorio;
    private final RegistroDeTipos tipos;

    public ServicioNotificaciones(NotificacionesRepositorio repositorio, RegistroDeTipos tipos) {
        this.repositorio = repositorio;
        this.tipos = tipos;
    }

    public synchronized void alActualizarReserva(Evento evento) {
        if (!repositorio.marcarProcesado(evento.id())) {
            return;
        }
        Reserva reserva = Reserva.desdeMapa(evento.mapa("reserva"));
        String que = tipos.obtener(reserva.tipo()).describir(reserva);
        String mensaje = switch (evento.texto("cambio")) {
            case "CREADA" -> "Agregamos tu " + que + " a \"" + evento.texto("viajeNombre") + "\"";
            case "CANCELADA" -> "Se canceló tu " + que;
            case "ELIMINADA" -> "Eliminaste tu " + que;
            default -> "Cambió tu " + que;
        };
        Map<String, Object> aviso = new LinkedHashMap<>();
        aviso.put("fecha", LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString());
        aviso.put("mensaje", mensaje);
        aviso.put("viajeId", evento.texto("viajeId"));
        repositorio.guardar(reserva.usuario(), aviso);
        // En el diseño este aviso sale por push al celular (APNs/FCM); acá se muestra en la consola.
        System.out.println("[push a " + reserva.usuario() + "] " + mensaje);
    }

    public synchronized List<Map<String, Object>> avisosDe(String usuario) {
        return repositorio.avisosDe(usuario);
    }
}
