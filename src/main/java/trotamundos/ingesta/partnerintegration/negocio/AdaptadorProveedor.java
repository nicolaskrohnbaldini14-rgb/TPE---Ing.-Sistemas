package trotamundos.ingesta.partnerintegration.negocio;

import trotamundos.comun.modelo.Reserva;

import java.util.Map;

/**
 * Un adaptador por proveedor (ADR-0004, tácticas Use an Intermediary + Tailor Interface):
 * traduce el mensaje en el formato del proveedor al modelo común de reserva.
 */
public interface AdaptadorProveedor {

    String proveedor();

    Reserva traducir(Map<String, Object> mensaje);

    static String campo(Map<String, Object> mensaje, String nombre) {
        Object valor = mensaje.get(nombre);
        if (valor == null || valor.toString().isBlank()) {
            throw new IllegalArgumentException("El mensaje del proveedor no trae '" + nombre + "'");
        }
        return valor.toString();
    }
}
