package trotamundos.panel.negocio;

import trotamundos.comun.eventos.Evento;
import trotamundos.compartir.CompartirModulo;
import trotamundos.panel.datos.VistaPanelRepositorio;
import trotamundos.panel.datos.VistaPanelRepositorio.ViajeVista;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Proyección CQRS del panel (ADR-0005): arma el panel de cada usuario a partir de los eventos,
 * así leerlo no exige consultar las tablas de Itinerary.
 */
public final class ProyeccionPanel {

    public static final class SinAcceso extends RuntimeException {
        public SinAcceso(String mensaje) {
            super(mensaje);
        }
    }

    private final VistaPanelRepositorio repositorio;
    private final CompartirModulo compartir;

    public ProyeccionPanel(VistaPanelRepositorio repositorio, CompartirModulo compartir) {
        this.repositorio = repositorio;
        this.compartir = compartir;
    }

    public synchronized void alActualizarReserva(Evento evento) {
        Map<String, Object> reserva = evento.mapa("reserva");
        String reservaId = String.valueOf(reserva.get("id"));
        int version = evento.entero("version");
        if (version <= repositorio.version(reservaId)) {
            return; // evento repetido o más viejo que lo que ya se muestra
        }
        repositorio.guardarVersion(reservaId, version);
        ViajeVista viaje = repositorio.viajeOCrear(evento.texto("viajeId"), evento.texto("usuario"), evento.texto("viajeNombre"));
        if ("ELIMINADA".equals(evento.texto("cambio"))) {
            viaje.reservas.remove(reservaId);
            if (viaje.reservas.isEmpty()) {
                repositorio.quitarViaje(viaje.id);
            }
        } else {
            viaje.reservas.put(reservaId, reserva);
        }
    }

    public synchronized void alArchivarViaje(Evento evento) {
        repositorio.viaje(evento.texto("viajeId")).ifPresent(v -> v.estado = "ARCHIVADO");
    }

    public synchronized void alCambiarPermiso(Evento evento) {
        if ("OTORGADO".equals(evento.texto("cambio"))) {
            repositorio.compartir(evento.texto("email"), evento.texto("viajeId"));
        } else {
            repositorio.dejarDeCompartir(evento.texto("email"), evento.texto("viajeId"));
        }
    }

    public synchronized Map<String, Object> panelDe(String usuario) {
        List<Map<String, Object>> activos = new ArrayList<>();
        List<Map<String, Object>> archivados = new ArrayList<>();
        for (ViajeVista viaje : repositorio.viajesDe(usuario)) {
            ("ARCHIVADO".equals(viaje.estado) ? archivados : activos).add(aMapa(viaje, "DUENIO"));
        }
        List<Map<String, Object>> compartidos = new ArrayList<>();
        for (String viajeId : repositorio.compartidosCon(usuario)) {
            // Se vuelve a preguntar a Sharing: si el permiso se revocó, el viaje deja de verse al instante.
            if (compartir.puedeVer(usuario, viajeId)) {
                repositorio.viaje(viajeId).ifPresent(v -> compartidos.add(aMapa(v, "INVITADO")));
            }
        }
        Comparator<Map<String, Object>> porInicio = Comparator.comparing(m -> String.valueOf(m.get("inicio")));
        activos.sort(porInicio);
        archivados.sort(porInicio);
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("usuario", usuario);
        panel.put("viajes", activos);
        panel.put("archivados", archivados);
        panel.put("compartidosConmigo", compartidos);
        return panel;
    }

    /** Devuelve el viaje si el usuario es el dueño o una persona designada. */
    public synchronized Optional<Map<String, Object>> viaje(String usuario, String viajeId) {
        Optional<ViajeVista> viaje = repositorio.viaje(viajeId);
        if (viaje.isEmpty()) {
            return Optional.empty();
        }
        if (viaje.get().duenio.equals(usuario)) {
            return Optional.of(aMapa(viaje.get(), "DUENIO"));
        }
        if (compartir.puedeVer(usuario, viajeId)) {
            return Optional.of(aMapa(viaje.get(), "INVITADO"));
        }
        throw new SinAcceso("No tenés permiso para ver este viaje");
    }

    private static Map<String, Object> aMapa(ViajeVista viaje, String rol) {
        List<Map<String, Object>> reservas = new ArrayList<>(viaje.reservas.values());
        reservas.sort(Comparator.comparing(r -> String.valueOf(r.get("inicio"))));
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", viaje.id);
        mapa.put("nombre", viaje.nombre);
        mapa.put("estado", viaje.estado);
        mapa.put("rol", rol);
        mapa.put("inicio", reservas.isEmpty() ? null : reservas.get(0).get("inicio"));
        mapa.put("fin", reservas.stream().map(r -> String.valueOf(r.get("fin") != null ? r.get("fin") : r.get("inicio")))
                .max(Comparator.naturalOrder()).orElse(null));
        mapa.put("reservas", reservas);
        return mapa;
    }
}
