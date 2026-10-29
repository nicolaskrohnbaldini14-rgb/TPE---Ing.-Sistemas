package trotamundos.notificaciones.datos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tablas propias de Notification: avisos enviados a cada usuario y eventos ya procesados. */
public final class NotificacionesRepositorio {

    private final Map<String, List<Map<String, Object>>> avisos = new HashMap<>();
    private final Set<String> eventosProcesados = new HashSet<>();

    /** Devuelve false si el evento ya se había procesado (entrega repetida). */
    public boolean marcarProcesado(String eventoId) {
        return eventosProcesados.add(eventoId);
    }

    public void guardar(String usuario, Map<String, Object> aviso) {
        avisos.computeIfAbsent(usuario, u -> new ArrayList<>()).add(0, aviso);
    }

    public List<Map<String, Object>> avisosDe(String usuario) {
        return List.copyOf(avisos.getOrDefault(usuario, List.of()));
    }
}
