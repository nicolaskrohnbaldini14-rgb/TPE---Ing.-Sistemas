package trotamundos.compartir.negocio;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.compartir.datos.PermisosRepositorio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * QA-9: solo ven un viaje su dueño y las personas designadas (Authorize Actors),
 * y el dueño puede revocar el permiso en cualquier momento (Revoke Access).
 */
public final class ServicioCompartir {

    public static final class SinPermiso extends RuntimeException {
        public SinPermiso(String mensaje) {
            super(mensaje);
        }
    }

    private final PermisosRepositorio repositorio;
    private final BusDeEventos bus;

    public ServicioCompartir(PermisosRepositorio repositorio, BusDeEventos bus) {
        this.repositorio = repositorio;
        this.bus = bus;
    }

    public synchronized void registrarDuenio(String viajeId, String usuario) {
        repositorio.registrarDuenio(viajeId, usuario);
    }

    public void compartir(String duenio, String viajeId, String email) {
        Evento evento;
        synchronized (this) {
            validarDuenio(duenio, viajeId);
            repositorio.agregar(viajeId, email.trim().toLowerCase());
            evento = permisoCambiado(duenio, viajeId, email, "OTORGADO");
        }
        // Se publica fuera del bloque sincronizado para no trabar a los módulos que consultan puedeVer().
        bus.publicar(evento);
    }

    public void revocar(String duenio, String viajeId, String email) {
        Evento evento;
        synchronized (this) {
            validarDuenio(duenio, viajeId);
            repositorio.quitar(viajeId, email.trim().toLowerCase());
            evento = permisoCambiado(duenio, viajeId, email, "REVOCADO");
        }
        bus.publicar(evento);
    }

    public synchronized Set<String> invitados(String duenio, String viajeId) {
        validarDuenio(duenio, viajeId);
        return repositorio.invitados(viajeId);
    }

    public synchronized boolean puedeVer(String usuario, String viajeId) {
        return repositorio.duenio(viajeId).map(d -> d.equals(usuario)).orElse(false)
                || repositorio.invitados(viajeId).contains(usuario);
    }

    private void validarDuenio(String usuario, String viajeId) {
        String duenio = repositorio.duenio(viajeId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el viaje " + viajeId));
        if (!duenio.equals(usuario)) {
            throw new SinPermiso("Solo el dueño del viaje puede administrar con quién se comparte");
        }
    }

    private static Evento permisoCambiado(String duenio, String viajeId, String email, String cambio) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("duenio", duenio);
        datos.put("viajeId", viajeId);
        datos.put("email", email.trim().toLowerCase());
        datos.put("cambio", cambio);
        return Evento.nuevo(Evento.PERMISO_CAMBIADO, datos);
    }
}
