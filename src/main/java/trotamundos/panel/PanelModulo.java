package trotamundos.panel;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.comun.http.Servidor;
import trotamundos.compartir.CompartirModulo;
import trotamundos.panel.api.PanelApi;
import trotamundos.panel.datos.VistaPanelRepositorio;
import trotamundos.panel.negocio.ProyeccionPanel;

/**
 * Módulo Dashboard Query. Consume ReservaActualizada, ViajeArchivado y PermisoCambiado.
 * Usa a Sharing solo a través de su interfaz pública (puedeVer).
 */
public final class PanelModulo {

    public PanelModulo(Servidor servidor, BusDeEventos bus, CompartirModulo compartir) {
        ProyeccionPanel proyeccion = new ProyeccionPanel(new VistaPanelRepositorio(), compartir);
        new PanelApi(proyeccion).registrar(servidor);
        bus.suscribir(Evento.RESERVA_ACTUALIZADA, proyeccion::alActualizarReserva);
        bus.suscribir(Evento.VIAJE_ARCHIVADO, proyeccion::alArchivarViaje);
        bus.suscribir(Evento.PERMISO_CAMBIADO, proyeccion::alCambiarPermiso);
    }
}
