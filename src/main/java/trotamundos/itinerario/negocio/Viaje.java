package trotamundos.itinerario.negocio;

import java.util.ArrayList;
import java.util.List;

/** Un viaje agrupa las reservas de un usuario que están cerca en el tiempo. */
public final class Viaje {

    public enum Estado { ACTIVO, ARCHIVADO }

    private final String id;
    private final String usuario;
    private final String nombre;
    private Estado estado = Estado.ACTIVO;
    private final List<String> reservaIds = new ArrayList<>();

    public Viaje(String id, String usuario, String nombre) {
        this.id = id;
        this.usuario = usuario;
        this.nombre = nombre;
    }

    public String id() {
        return id;
    }

    public String usuario() {
        return usuario;
    }

    public String nombre() {
        return nombre;
    }

    public Estado estado() {
        return estado;
    }

    public List<String> reservaIds() {
        return List.copyOf(reservaIds);
    }

    void agregar(String reservaId) {
        if (!reservaIds.contains(reservaId)) {
            reservaIds.add(reservaId);
        }
    }

    void quitar(String reservaId) {
        reservaIds.remove(reservaId);
    }

    void archivar() {
        estado = Estado.ARCHIVADO;
    }
}
