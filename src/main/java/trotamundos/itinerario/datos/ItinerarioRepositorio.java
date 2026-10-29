package trotamundos.itinerario.datos;

import trotamundos.comun.modelo.Reserva;
import trotamundos.itinerario.negocio.Viaje;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tablas propias del módulo Itinerary: viajes y reservas (en memoria en el prototipo).
 * Ningún otro módulo las lee: se enteran de los cambios por eventos.
 * No es thread-safe: el servicio lo usa siempre dentro de un bloque sincronizado.
 */
public final class ItinerarioRepositorio {

    private final Map<String, Viaje> viajes = new LinkedHashMap<>();
    private final Map<String, Reserva> reservas = new HashMap<>();
    private final Map<String, String> reservaPorClave = new HashMap<>();
    private final Map<String, String> viajeDeReserva = new HashMap<>();
    private final Map<String, Integer> versiones = new HashMap<>();

    public void guardarViaje(Viaje viaje) {
        viajes.put(viaje.id(), viaje);
    }

    public Optional<Viaje> viaje(String id) {
        return Optional.ofNullable(viajes.get(id));
    }

    public List<Viaje> viajesDe(String usuario) {
        return viajes.values().stream().filter(v -> v.usuario().equals(usuario)).toList();
    }

    public List<Viaje> todosLosViajes() {
        return List.copyOf(viajes.values());
    }

    public void eliminarViaje(String id) {
        viajes.remove(id);
    }

    /** Guarda la reserva en el viaje indicado y devuelve su nueva versión. */
    public int guardarReserva(Reserva reserva, String viajeId) {
        reservas.put(reserva.id(), reserva);
        viajeDeReserva.put(reserva.id(), viajeId);
        if (reserva.claveNatural() != null) {
            reservaPorClave.put(reserva.claveNatural(), reserva.id());
        }
        return versiones.merge(reserva.id(), 1, Integer::sum);
    }

    /** Borra la reserva y devuelve la versión del borrado. */
    public int eliminarReserva(String id) {
        Reserva reserva = reservas.remove(id);
        viajeDeReserva.remove(id);
        if (reserva != null && reserva.claveNatural() != null) {
            reservaPorClave.remove(reserva.claveNatural());
        }
        return versiones.merge(id, 1, Integer::sum);
    }

    public Optional<Reserva> reserva(String id) {
        return Optional.ofNullable(reservas.get(id));
    }

    public Optional<Reserva> porClave(String clave) {
        return Optional.ofNullable(reservaPorClave.get(clave)).map(reservas::get);
    }

    public int version(String reservaId) {
        return versiones.getOrDefault(reservaId, 0);
    }

    public String viajeDe(String reservaId) {
        return viajeDeReserva.get(reservaId);
    }
}
