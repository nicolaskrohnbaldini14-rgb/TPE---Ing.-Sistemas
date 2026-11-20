package trotamundos.recomendacion.datos;

import trotamundos.recomendacion.negocio.Sugerencia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tablas propias de Recommendation: pesos de cada usuario, sugerencias y respuestas.
 * No es thread-safe: el servicio lo usa siempre dentro de un bloque sincronizado.
 */
public final class RecomendacionRepositorio {

    private final Map<String, Map<String, Double>> pesosPorUsuario = new HashMap<>();
    private final Map<String, Sugerencia> sugerencias = new LinkedHashMap<>();
    private final List<Map<String, Object>> respuestas = new ArrayList<>();
    private Map<String, Double> pesosGenerales = Map.of(
            "precio", 1.0, "sinEscalas", 0.5, "horarioTemprano", 0.3, "estrellas", 0.5);

    /**
     * Reemplaza los pesos del modelo general (en el diseño, los publica el reentrenamiento nocturno
     * en el Data Warehouse; ADR-0020). Los usan los usuarios que todavía no respondieron nada.
     */
    public void cargarPesosGenerales(Map<String, Double> pesos) {
        pesosGenerales = Map.copyOf(pesos);
    }

    /** Pesos del usuario; si nunca respondió, arranca con los del modelo general. */
    public Map<String, Double> pesosDe(String usuario) {
        return new LinkedHashMap<>(pesosPorUsuario.getOrDefault(usuario, pesosGenerales));
    }

    public void guardarPesos(String usuario, Map<String, Double> pesos) {
        pesosPorUsuario.put(usuario, new LinkedHashMap<>(pesos));
    }

    public void borrarPesos(String usuario) {
        pesosPorUsuario.remove(usuario);
    }

    public void guardar(Sugerencia sugerencia) {
        sugerencias.put(sugerencia.id(), sugerencia);
    }

    public Optional<Sugerencia> sugerencia(String id) {
        return Optional.ofNullable(sugerencias.get(id));
    }

    public List<Sugerencia> sugerenciasDe(String usuario) {
        return sugerencias.values().stream().filter(s -> s.usuario().equals(usuario)).toList();
    }

    public void guardarRespuesta(Map<String, Object> respuesta) {
        respuestas.add(respuesta);
    }
}
