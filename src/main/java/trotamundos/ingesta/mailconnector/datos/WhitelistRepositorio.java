package trotamundos.ingesta.mailconnector.datos;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Tabla propia de Mail Connector: remitentes seguros (whitelist) de cada usuario. */
public final class WhitelistRepositorio {

    private final Map<String, Set<String>> remitentes = new HashMap<>();

    public synchronized Set<String> remitentesDe(String usuario) {
        return Set.copyOf(remitentes.getOrDefault(usuario, Set.of()));
    }

    public synchronized void reemplazar(String usuario, Set<String> nuevos) {
        remitentes.put(usuario, new LinkedHashSet<>(nuevos));
    }
}
