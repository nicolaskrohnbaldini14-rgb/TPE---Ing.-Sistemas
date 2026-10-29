package trotamundos.ingesta.partnerintegration.negocio;

import trotamundos.comun.modelo.Reserva;

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
}
