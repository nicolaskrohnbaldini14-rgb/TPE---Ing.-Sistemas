package trotamundos.comun.modelo;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modelo común de reserva (ADR-0004): todos los adaptadores traducen a esta forma, así el núcleo
 * conoce un solo formato. Los datos propios de cada tipo (número de vuelo, hotel…) van en detalles.
 * Las fechas tienen el formato yyyy-MM-ddTHH:mm.
 */
public record Reserva(String id, String usuario, String tipo, String proveedor, String codigoConfirmacion,
                      Estado estado, String inicio, String fin, String origen, String destino,
                      Fuente fuente, Map<String, Object> detalles) {

    public enum Estado { CONFIRMADA, CAMBIADA, CANCELADA }

    public enum Fuente { CORREO, PROVEEDOR, MANUAL }

    public Reserva {
        if (usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("La reserva no tiene usuario");
        }
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("La reserva no tiene tipo");
        }
        if (inicio == null || inicio.isBlank()) {
            throw new IllegalArgumentException("La reserva no tiene fecha de inicio");
        }
        usuario = usuario.trim().toLowerCase();
        tipo = tipo.trim().toLowerCase();
        estado = estado == null ? Estado.CONFIRMADA : estado;
        fuente = fuente == null ? Fuente.MANUAL : fuente;
        detalles = detalles == null ? Map.of() : Map.copyOf(detalles);
        validarFecha(inicio);
        if (fin != null && !fin.isBlank()) {
            validarFecha(fin);
        }
    }

    public LocalDateTime inicioFecha() {
        return LocalDateTime.parse(inicio);
    }

    public LocalDateTime finFecha() {
        return fin == null || fin.isBlank() ? inicioFecha() : LocalDateTime.parse(fin);
    }

    /** Identifica la misma reserva aunque llegue por correo, por el proveedor y a mano. */
    public String claveNatural() {
        if (codigoConfirmacion == null || codigoConfirmacion.isBlank()) {
            return null;
        }
        return (tipo + "|" + proveedor + "|" + codigoConfirmacion).toLowerCase();
    }

    public Reserva conId(String nuevoId) {
        return new Reserva(nuevoId, usuario, tipo, proveedor, codigoConfirmacion, estado, inicio, fin,
                origen, destino, fuente, detalles);
    }

    public Map<String, Object> aMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", id);
        mapa.put("usuario", usuario);
        mapa.put("tipo", tipo);
        mapa.put("proveedor", proveedor);
        mapa.put("codigoConfirmacion", codigoConfirmacion);
        mapa.put("estado", estado.name());
        mapa.put("inicio", inicio);
        mapa.put("fin", fin);
        mapa.put("origen", origen);
        mapa.put("destino", destino);
        mapa.put("fuente", fuente.name());
        mapa.put("detalles", new LinkedHashMap<>(detalles));
        return mapa;
    }

    @SuppressWarnings("unchecked")
    public static Reserva desdeMapa(Map<String, Object> mapa) {
        return new Reserva(
                texto(mapa, "id"),
                texto(mapa, "usuario"),
                texto(mapa, "tipo"),
                texto(mapa, "proveedor"),
                texto(mapa, "codigoConfirmacion"),
                mapa.get("estado") == null ? null : Estado.valueOf(texto(mapa, "estado").toUpperCase()),
                texto(mapa, "inicio"),
                texto(mapa, "fin"),
                texto(mapa, "origen"),
                texto(mapa, "destino"),
                mapa.get("fuente") == null ? null : Fuente.valueOf(texto(mapa, "fuente").toUpperCase()),
                (Map<String, Object>) mapa.get("detalles"));
    }

    private static String texto(Map<String, Object> mapa, String campo) {
        Object valor = mapa.get(campo);
        return valor == null ? null : valor.toString();
    }

    private static void validarFecha(String fecha) {
        try {
            LocalDateTime.parse(fecha);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha inválida '" + fecha + "': usar el formato 2026-12-01T10:00");
        }
    }
}
