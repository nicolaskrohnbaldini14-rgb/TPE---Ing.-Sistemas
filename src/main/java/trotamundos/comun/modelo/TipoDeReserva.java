package trotamundos.comun.modelo;

import java.util.List;

/**
 * Plugin de un medio de transporte o alojamiento (ADR-0004, QA-10).
 * Agregar trenes o autobuses es crear una clase que implemente esta interfaz y registrarla.
 */
public interface TipoDeReserva {

    String codigo();

    /** Campos de detalles que tiene que traer toda reserva de este tipo. */
    List<String> detallesObligatorios();

    /** Texto corto para avisos, por ejemplo "vuelo AR1132". */
    String describir(Reserva reserva);
}
