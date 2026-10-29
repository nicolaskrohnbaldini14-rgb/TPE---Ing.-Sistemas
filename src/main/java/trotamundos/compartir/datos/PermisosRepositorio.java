package trotamundos.compartir.datos;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Tablas propias de Sharing: dueño de cada viaje y personas designadas. */
public final class PermisosRepositorio {

    private final Map<String, String> duenios = new HashMap<>();
    private final Map<String, Set<String>> invitados = new HashMap<>();

    public void registrarDuenio(String viajeId, String usuario) {
        duenios.putIfAbsent(viajeId, usuario);
    }

    public Optional<String> duenio(String viajeId) {
        return Optional.ofNullable(duenios.get(viajeId));
    }

    public void agregar(String viajeId, String email) {
        invitados.computeIfAbsent(viajeId, v -> new LinkedHashSet<>()).add(email);
    }

    public boolean quitar(String viajeId, String email) {
        return invitados.getOrDefault(viajeId, Set.of()).remove(email);
    }

    public Set<String> invitados(String viajeId) {
        return Set.copyOf(invitados.getOrDefault(viajeId, Set.of()));
    }
}
