package trotamundos.ingesta.partnerintegration.negocio;

import trotamundos.comun.modelo.Reserva;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static trotamundos.ingesta.partnerintegration.negocio.AdaptadorProveedor.campo;

/**
 * Aerolínea de ejemplo. Su formato:
 * {"pnr", "pasajero_email", "vuelo", "desde", "hacia", "salida", "llegada", "estado": CONFIRMED|CHANGED|CANCELLED}
 */
public final class AdaptadorAerolineaDemo implements AdaptadorProveedor {

    @Override
    public String proveedor() {
        return "aerolinea-demo";
    }

    @Override
    public Reserva traducir(Map<String, Object> mensaje) {
        Reserva.Estado estado = switch (String.valueOf(mensaje.getOrDefault("estado", "CONFIRMED")).toUpperCase()) {
            case "CANCELLED" -> Reserva.Estado.CANCELADA;
            case "CHANGED" -> Reserva.Estado.CAMBIADA;
            default -> Reserva.Estado.CONFIRMADA;
        };
        return new Reserva(null, campo(mensaje, "pasajero_email"), "vuelo", "aerolinea-demo.com",
                campo(mensaje, "pnr"), estado, campo(mensaje, "salida"), campo(mensaje, "llegada"),
                campo(mensaje, "desde"), campo(mensaje, "hacia"), Reserva.Fuente.PROVEEDOR,
                Map.of("numeroVuelo", campo(mensaje, "vuelo")));
    }

    /** Catálogo de ejemplo: en el sistema real, esto es una búsqueda en la API del proveedor. */
    @Override
    public List<Map<String, Object>> alternativas(String tipo, String origen, String destino, String fecha) {
        if (!"vuelo".equals(tipo)) {
            return List.of();
        }
        return List.of(
                vuelo("AR1134", origen, destino, fecha + "T14:00", 0, 1250),
                vuelo("IB6844", origen, destino, fecha + "T22:30", 0, 1100),
                vuelo("UX42", origen, destino, fecha + "T09:15", 1, 780),
                vuelo("LA8070", origen, destino, fecha + "T18:00", 1, 820));
    }

    private static Map<String, Object> vuelo(String numero, String origen, String destino, String salida, int escalas, int precio) {
        Map<String, Object> vuelo = new LinkedHashMap<>();
        vuelo.put("id", "vuelo|" + numero);
        vuelo.put("tipo", "vuelo");
        vuelo.put("numeroVuelo", numero);
        vuelo.put("origen", origen);
        vuelo.put("destino", destino);
        vuelo.put("salida", salida);
        vuelo.put("escalas", escalas);
        vuelo.put("precio", precio);
        return vuelo;
    }
}
