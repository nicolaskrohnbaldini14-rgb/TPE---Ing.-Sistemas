package trotamundos.panel.datos;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * View Store: el panel de cada usuario ya armado (ADR-0005). Leer un panel es una sola búsqueda.
 * No es thread-safe: la proyección lo usa siempre dentro de un bloque sincronizado.
 */
public final class VistaPanelRepositorio {

    /** Un viaje tal como se muestra en el panel. */
    public static final class ViajeVista {
        public final String id;
        public final String duenio;
        public final String nombre;
        public String estado = "ACTIVO";
        public final Map<String, Map<String, Object>> reservas = new LinkedHashMap<>();

        ViajeVista(String id, String duenio, String nombre) {
            this.id = id;
            this.duenio = duenio;
            this.nombre = nombre;
        }
    }

    private final Map<String, ViajeVista> viajes = new LinkedHashMap<>();
    private final Map<String, Integer> versiones = new HashMap<>();
    private final Map<String, Set<String>> compartidosCon = new HashMap<>();

    public ViajeVista viajeOCrear(String viajeId, String duenio, String nombre) {
        return viajes.computeIfAbsent(viajeId, id -> new ViajeVista(id, duenio, nombre));
    }

    public Optional<ViajeVista> viaje(String viajeId) {
        return Optional.ofNullable(viajes.get(viajeId));
    }

    public void quitarViaje(String viajeId) {
        viajes.remove(viajeId);
    }

    public List<ViajeVista> viajesDe(String usuario) {
        return viajes.values().stream().filter(v -> v.duenio.equals(usuario)).toList();
    }

    public int version(String reservaId) {
        return versiones.getOrDefault(reservaId, 0);
    }

    public void guardarVersion(String reservaId, int version) {
        versiones.put(reservaId, version);
    }

    public void compartir(String email, String viajeId) {
        compartidosCon.computeIfAbsent(email, e -> new LinkedHashSet<>()).add(viajeId);
    }

    public void dejarDeCompartir(String email, String viajeId) {
        compartidosCon.getOrDefault(email, new LinkedHashSet<>()).remove(viajeId);
    }

    public Set<String> compartidosCon(String email) {
        return Set.copyOf(compartidosCon.getOrDefault(email, Set.of()));
    }
}
