package trotamundos.comun.modelo;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Catálogo de tipos de reserva disponibles (táctica Defer Binding: se arma al arrancar). */
public final class RegistroDeTipos {

    private final Map<String, TipoDeReserva> tipos = new ConcurrentHashMap<>();

    public RegistroDeTipos registrar(TipoDeReserva tipo) {
        tipos.put(tipo.codigo(), tipo);
        return this;
    }

    public TipoDeReserva obtener(String codigo) {
        TipoDeReserva tipo = tipos.get(codigo);
        if (tipo == null) {
            throw new IllegalArgumentException("Tipo de reserva desconocido '" + codigo + "'. Disponibles: " + codigos());
        }
        return tipo;
    }

    public Set<String> codigos() {
        return new TreeSet<>(tipos.keySet());
    }

    /** Verifica que la reserva sea de un tipo registrado y traiga sus detalles obligatorios. */
    public void validar(Reserva reserva) {
        TipoDeReserva tipo = obtener(reserva.tipo());
        for (String campo : tipo.detallesObligatorios()) {
            Object valor = reserva.detalles().get(campo);
            if (valor == null || valor.toString().isBlank()) {
                throw new IllegalArgumentException("Una reserva de tipo " + tipo.codigo() + " necesita el detalle '" + campo + "'");
            }
        }
    }
}
