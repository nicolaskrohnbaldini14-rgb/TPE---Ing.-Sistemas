package trotamundos.ingesta.partnerintegration.negocio;

import trotamundos.comun.modelo.Reserva;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static trotamundos.ingesta.partnerintegration.negocio.AdaptadorProveedor.campo;

/**
 * Hotel de ejemplo, con un formato distinto al de la aerolínea:
 * {"booking_ref", "guest", "hotel_name", "city", "check_in", "check_out", "status": OK|MODIFIED|CANCELLED}
 */
public final class AdaptadorHotelDemo implements AdaptadorProveedor {

    @Override
    public String proveedor() {
        return "hotel-demo";
    }

    @Override
    public Reserva traducir(Map<String, Object> mensaje) {
        Reserva.Estado estado = switch (String.valueOf(mensaje.getOrDefault("status", "OK")).toUpperCase()) {
            case "CANCELLED" -> Reserva.Estado.CANCELADA;
            case "MODIFIED" -> Reserva.Estado.CAMBIADA;
            default -> Reserva.Estado.CONFIRMADA;
        };
        return new Reserva(null, campo(mensaje, "guest"), "hotel", "hotel-demo.com",
                campo(mensaje, "booking_ref"), estado, campo(mensaje, "check_in"), campo(mensaje, "check_out"),
                null, campo(mensaje, "city"), Reserva.Fuente.PROVEEDOR,
                Map.of("hotel", campo(mensaje, "hotel_name")));
    }

    /** Catálogo de ejemplo: en el sistema real, esto es una búsqueda en la API del proveedor. */
    @Override
    public List<Map<String, Object>> alternativas(String tipo, String origen, String destino, String fecha) {
        if (!"hotel".equals(tipo)) {
            return List.of();
        }
        return List.of(
                hotel("Hotel Sol", destino, fecha + "T15:00", 3, 90),
                hotel("Gran Vía Palace", destino, fecha + "T15:00", 5, 240),
                hotel("Hostal Centro", destino, fecha + "T15:00", 2, 55));
    }

    private static Map<String, Object> hotel(String nombre, String ciudad, String entrada, int estrellas, int precio) {
        Map<String, Object> hotel = new LinkedHashMap<>();
        hotel.put("id", "hotel|" + nombre);
        hotel.put("tipo", "hotel");
        hotel.put("hotel", nombre);
        hotel.put("destino", ciudad);
        hotel.put("salida", entrada);
        hotel.put("estrellas", estrellas);
        hotel.put("precio", precio);
        return hotel;
    }
}
