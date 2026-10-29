package trotamundos.itinerario.negocio;

/** La reserva o el viaje no existe, o no pertenece al usuario que lo pide. */
public final class NoEncontrado extends RuntimeException {

    public NoEncontrado(String mensaje) {
        super(mensaje);
    }
}
