package trotamundos.ingesta.emailparser.negocio;

import trotamundos.comun.modelo.Reserva;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Extrae la reserva de un correo con líneas "Campo: valor" (táctica Tailor Interface).
 * Reconoce dos plantillas:
 * <pre>
 * Vuelo:   Código, Vuelo, Desde, Hacia, Salida, Llegada
 * Hotel:   Código, Hotel, Ciudad, Entrada, Salida
 * </pre>
 */
public final class ParserDePlantillas {

    /** Devuelve vacío si el correo no coincide con ninguna plantilla o trae datos inválidos. */
    public Optional<Reserva> parsear(String usuario, String remitente, String cuerpo) {
        try {
            return intentar(usuario, remitente, cuerpo);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private Optional<Reserva> intentar(String usuario, String remitente, String cuerpo) {
        Map<String, String> campos = campos(cuerpo);
        String proveedor = remitente.contains("@") ? remitente.substring(remitente.indexOf('@') + 1) : remitente;
        if (campos.containsKey("vuelo")) {
            return Optional.of(new Reserva(null, usuario, "vuelo", proveedor, campos.get("codigo"),
                    Reserva.Estado.CONFIRMADA, campos.get("salida"), campos.get("llegada"),
                    campos.get("desde"), campos.get("hacia"), Reserva.Fuente.CORREO,
                    Map.of("numeroVuelo", campos.get("vuelo"))));
        }
        if (campos.containsKey("hotel")) {
            return Optional.of(new Reserva(null, usuario, "hotel", proveedor, campos.get("codigo"),
                    Reserva.Estado.CONFIRMADA, campos.get("entrada"), campos.get("salida"),
                    null, campos.get("ciudad"), Reserva.Fuente.CORREO,
                    Map.of("hotel", campos.get("hotel"))));
        }
        return Optional.empty();
    }

    private static Map<String, String> campos(String cuerpo) {
        Map<String, String> campos = new HashMap<>();
        for (String linea : cuerpo.split("\\R")) {
            int dosPuntos = linea.indexOf(':');
            if (dosPuntos <= 0) {
                continue;
            }
            String clave = sinAcentos(linea.substring(0, dosPuntos).trim().toLowerCase());
            campos.put(clave, linea.substring(dosPuntos + 1).trim());
        }
        return new LinkedHashMap<>(campos);
    }

    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
