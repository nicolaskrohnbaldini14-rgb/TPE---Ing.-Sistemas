package trotamundos.ingesta.partnerintegration.negocio;

import trotamundos.comun.modelo.Reserva;

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
}
