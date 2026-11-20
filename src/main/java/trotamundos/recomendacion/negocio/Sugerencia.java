package trotamundos.recomendacion.negocio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Una alternativa sugerida al usuario para reemplazar una reserva cancelada. */
public final class Sugerencia {

    public enum Estado { PENDIENTE, ACEPTADA, RECHAZADA, AJUSTADA }

    private final String id;
    private final String usuario;
    private final String viajeId;
    private final String tipo;
    private final Map<String, Object> alternativa;
    private final double puntaje;
    private final String motivo;
    /** Todas las alternativas que había, para poder recalcular cuando el usuario responde. */
    private final List<Map<String, Object>> candidatas;
    /** Alternativas que el usuario ya rechazó: no se vuelven a sugerir. */
    private final Set<String> descartadas;
    private Estado estado = Estado.PENDIENTE;

    public Sugerencia(String id, String usuario, String viajeId, String tipo, Map<String, Object> alternativa,
                      double puntaje, String motivo, List<Map<String, Object>> candidatas, Set<String> descartadas) {
        this.id = id;
        this.usuario = usuario;
        this.viajeId = viajeId;
        this.tipo = tipo;
        this.alternativa = alternativa;
        this.puntaje = puntaje;
        this.motivo = motivo;
        this.candidatas = List.copyOf(candidatas);
        this.descartadas = Set.copyOf(descartadas);
    }

    public String id() {
        return id;
    }

    public String usuario() {
        return usuario;
    }

    public String viajeId() {
        return viajeId;
    }

    public String tipo() {
        return tipo;
    }

    public Map<String, Object> alternativa() {
        return alternativa;
    }

    public List<Map<String, Object>> candidatas() {
        return candidatas;
    }

    public Set<String> descartadas() {
        return descartadas;
    }

    public Estado estado() {
        return estado;
    }

    void responder(Estado nuevo) {
        estado = nuevo;
    }

    public Map<String, Object> aMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", id);
        mapa.put("viajeId", viajeId);
        mapa.put("tipo", tipo);
        mapa.put("alternativa", alternativa);
        mapa.put("puntaje", Math.round(puntaje * 100) / 100.0);
        mapa.put("motivo", motivo);
        mapa.put("estado", estado.name());
        return mapa;
    }
}
