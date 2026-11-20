package trotamundos.recomendacion.negocio;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interfaz del algoritmo de recomendación (ADR-0019, QA-14): el resto del módulo solo depende de esto,
 * así el algoritmo se puede reemplazar sin tocar nada más.
 */
public interface Recomendador {

    /**
     * Una alternativa con su puntaje total.
     *
     * @param valores cuánto cumple la alternativa cada criterio, de 0 a 1 (sirve para aprender del feedback)
     * @param motivo  explicación corta para el usuario
     */
    record Puntuada(Map<String, Object> alternativa, double puntaje, Map<String, Double> valores, String motivo) {
    }

    /** Criterios que entiende el algoritmo: son las claves de los pesos de cada usuario. */
    Set<String> criterios();

    /** Devuelve las alternativas ordenadas de mejor a peor según los pesos del usuario. */
    List<Puntuada> ordenar(List<Map<String, Object>> alternativas, Map<String, Double> pesos);
}
