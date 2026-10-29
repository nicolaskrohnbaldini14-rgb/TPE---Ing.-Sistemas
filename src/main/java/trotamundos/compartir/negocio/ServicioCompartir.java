package trotamundos.compartir.negocio;

import trotamundos.comun.eventos.Evento;
import trotamundos.comun.eventos.Outbox;
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
    private final Outbox outbox;

    public ServicioCompartir(PermisosRepositorio repositorio, Outbox outbox) {
        this.repositorio = repositorio;
        this.outbox = outbox;
    }

    public synchronized void registrarDuenio(String viajeId, String usuario) {
        repositorio.registrarDuenio(viajeId, usuario);
    }

    /** El permiso y su evento se guardan juntos (ADR-0010): si se cae la instancia, el evento no se pierde. */
    public synchronized void compartir(String duenio, String viajeId, String email) {
        validarDuenio(duenio, viajeId);
        repositorio.agregar(viajeId, email.trim().toLowerCase());
        outbox.guardar(permisoCambiado(duenio, viajeId, email, "OTORGADO"));
    }

    public synchronized void revocar(String duenio, String viajeId, String email) {
        validarDuenio(duenio, viajeId);
        repositorio.quitar(viajeId, email.trim().toLowerCase());
        outbox.guardar(permisoCambiado(duenio, viajeId, email, "REVOCADO"));
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
