package trotamundos.recomendacion.negocio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puntaje por preferencias (ADR-0019): cada alternativa se puntúa en cada criterio de 0 a 1 y el
 * puntaje total es la suma ponderada por los pesos del usuario. Es simple y se puede explicar.
 */
public final class PuntajePorPreferencias implements Recomendador {

    private static final Map<String, String> MOTIVOS = Map.of(
            "precio", "es la opción más económica",
            "sinEscalas", "no tiene escalas",
            "horarioTemprano", "sale a la mañana",
            "estrellas", "tiene buena categoría");

    @Override
    public Set<String> criterios() {
        return MOTIVOS.keySet();
    }

    @Override
    public List<Puntuada> ordenar(List<Map<String, Object>> alternativas, Map<String, Double> pesos) {
        double minimo = alternativas.stream().mapToDouble(a -> numero(a, "precio")).min().orElse(0);
        double maximo = alternativas.stream().mapToDouble(a -> numero(a, "precio")).max().orElse(0);
        List<Puntuada> puntuadas = new ArrayList<>();
        for (Map<String, Object> alternativa : alternativas) {
            Map<String, Double> valores = new LinkedHashMap<>();
            valores.put("precio", maximo == minimo ? 1.0 : (maximo - numero(alternativa, "precio")) / (maximo - minimo));
            valores.put("sinEscalas", alternativa.containsKey("escalas") && numero(alternativa, "escalas") == 0 ? 1.0 : 0.0);
            valores.put("horarioTemprano", "vuelo".equals(alternativa.get("tipo")) && hora(alternativa) < 12 ? 1.0 : 0.0);
            valores.put("estrellas", alternativa.containsKey("estrellas") ? numero(alternativa, "estrellas") / 5.0 : 0.0);

            double total = 0;
            String mejorCriterio = null;
            double mejorAporte = 0;
            for (Map.Entry<String, Double> valor : valores.entrySet()) {
                double aporte = pesos.getOrDefault(valor.getKey(), 0.0) * valor.getValue();
                total += aporte;
                if (aporte > mejorAporte) {
                    mejorAporte = aporte;
                    mejorCriterio = valor.getKey();
                }
            }
            String motivo = mejorCriterio == null ? "es la mejor opción disponible" : MOTIVOS.get(mejorCriterio);
            puntuadas.add(new Puntuada(alternativa, total, valores, motivo));
        }
        puntuadas.sort(Comparator.comparingDouble(Puntuada::puntaje).reversed());
        return puntuadas;
    }

    private static double numero(Map<String, Object> alternativa, String campo) {
        Object valor = alternativa.get(campo);
        return valor instanceof Number n ? n.doubleValue() : 0;
    }

    private static int hora(Map<String, Object> alternativa) {
        String salida = String.valueOf(alternativa.get("salida"));
        return salida.length() >= 13 ? Integer.parseInt(salida.substring(11, 13)) : 0;
    }
}
