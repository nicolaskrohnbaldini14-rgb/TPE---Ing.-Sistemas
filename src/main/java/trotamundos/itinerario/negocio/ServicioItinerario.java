package trotamundos.itinerario.negocio;

import trotamundos.comun.eventos.Evento;
import trotamundos.comun.eventos.Outbox;
import trotamundos.comun.modelo.RegistroDeTipos;
import trotamundos.comun.modelo.Reserva;
import trotamundos.itinerario.datos.ItinerarioRepositorio;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reglas del itinerario: evitar duplicados, agrupar reservas en viajes y archivar los terminados.
 * Cada cambio se guarda junto con su evento en el outbox dentro del mismo bloque sincronizado,
 * que en el prototipo cumple el papel de la transacción de base de datos (ADR-0010).
 */
public final class ServicioItinerario {

    /** Dos reservas pertenecen al mismo viaje si están a menos de estos días. */
    private static final long DIAS_DE_MARGEN = 2;

    public record Guardado(Reserva reserva, String viajeId, int version) {
        public Map<String, Object> aMapa() {
            Map<String, Object> mapa = new LinkedHashMap<>();
            mapa.put("reserva", reserva.aMapa());
            mapa.put("viajeId", viajeId);
            mapa.put("version", version);
            return mapa;
        }
    }

    private final ItinerarioRepositorio repositorio;
    private final Outbox outbox;
    private final RegistroDeTipos tipos;

    public ServicioItinerario(ItinerarioRepositorio repositorio, Outbox outbox, RegistroDeTipos tipos) {
        this.repositorio = repositorio;
        this.outbox = outbox;
        this.tipos = tipos;
    }

    /** Registra una reserva que llega de cualquier fuente: correo, proveedor o carga manual. */
    public synchronized Guardado registrar(Reserva entrante) {
        tipos.validar(entrante);
        String clave = entrante.claveNatural();
        Optional<Reserva> existente = clave == null ? Optional.empty() : repositorio.porClave(clave);
        if (existente.isPresent()) {
            Reserva anterior = existente.get();
            Reserva actualizada = entrante.conId(anterior.id());
            Viaje viaje = repositorio.viaje(repositorio.viajeDe(anterior.id())).orElseThrow();
            if (actualizada.equals(anterior)) {
                // Llegó repetida (por ejemplo, el mismo webhook dos veces): no hay cambio que avisar.
                return new Guardado(anterior, viaje.id(), repositorio.version(anterior.id()));
            }
            String cambio = actualizada.estado() == Reserva.Estado.CANCELADA ? "CANCELADA" : "ACTUALIZADA";
            return guardar(actualizada, viaje, cambio);
        }
        Reserva nueva = entrante.conId("r-" + UUID.randomUUID().toString().substring(0, 8));
        return guardar(nueva, asignarViaje(nueva), "CREADA");
    }

    public synchronized Guardado crearManual(String usuario, Map<String, Object> datos) {
        Map<String, Object> campos = new LinkedHashMap<>(datos);
        campos.put("usuario", usuario);
        campos.put("fuente", Reserva.Fuente.MANUAL.name());
        campos.putIfAbsent("proveedor", "manual");
        campos.remove("id");
        return registrar(Reserva.desdeMapa(campos));
    }

    public synchronized Guardado modificarManual(String usuario, String id, Map<String, Object> datos) {
        Reserva anterior = reservaDe(usuario, id);
        Map<String, Object> campos = anterior.aMapa();
        campos.putAll(datos);
        campos.put("id", anterior.id());
        campos.put("usuario", usuario);
        campos.put("fuente", Reserva.Fuente.MANUAL.name());
        Reserva modificada = Reserva.desdeMapa(campos);
        tipos.validar(modificada);
        Viaje viaje = repositorio.viaje(repositorio.viajeDe(id)).orElseThrow();
        return guardar(modificada, viaje, "ACTUALIZADA");
    }

    public synchronized void eliminarManual(String usuario, String id) {
        Reserva reserva = reservaDe(usuario, id);
        Viaje viaje = repositorio.viaje(repositorio.viajeDe(id)).orElseThrow();
        viaje.quitar(id);
        int version = repositorio.eliminarReserva(id);
        outbox.guardar(eventoReserva(reserva, viaje, "ELIMINADA", version));
        if (viaje.reservaIds().isEmpty()) {
            repositorio.eliminarViaje(viaje.id());
        }
    }

    /** Devuelve el viaje solo si es del usuario que lo pide. */
    public synchronized Map<String, Object> viajeDe(String usuario, String viajeId) {
        Viaje viaje = repositorio.viaje(viajeId)
                .filter(v -> v.usuario().equals(usuario))
                .orElseThrow(() -> new NoEncontrado("No existe el viaje " + viajeId));
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", viaje.id());
        mapa.put("nombre", viaje.nombre());
        mapa.put("estado", viaje.estado().name());
        mapa.put("reservas", reservasDe(viaje).stream().map(Reserva::aMapa).toList());
        return mapa;
    }

    /** UC-6: archiva los viajes cuya última reserva terminó hace más de un día. */
    public synchronized int archivarTerminados(LocalDateTime ahora) {
        int archivados = 0;
        for (Viaje viaje : repositorio.todosLosViajes()) {
            if (viaje.estado() != Viaje.Estado.ACTIVO || viaje.reservaIds().isEmpty()) {
                continue;
            }
            LocalDateTime fin = reservasDe(viaje).stream().map(Reserva::finFecha).max(Comparator.naturalOrder()).orElseThrow();
            if (fin.plusDays(1).isBefore(ahora)) {
                viaje.archivar();
                Map<String, Object> datos = new LinkedHashMap<>();
                datos.put("usuario", viaje.usuario());
                datos.put("viajeId", viaje.id());
                outbox.guardar(Evento.nuevo(Evento.VIAJE_ARCHIVADO, datos));
                archivados++;
            }
        }
        return archivados;
    }

    private Reserva reservaDe(String usuario, String id) {
        return repositorio.reserva(id)
                .filter(r -> r.usuario().equals(usuario))
                .orElseThrow(() -> new NoEncontrado("No existe la reserva " + id));
    }

    private Guardado guardar(Reserva reserva, Viaje viaje, String cambio) {
        viaje.agregar(reserva.id());
        int version = repositorio.guardarReserva(reserva, viaje.id());
        outbox.guardar(eventoReserva(reserva, viaje, cambio, version));
        return new Guardado(reserva, viaje.id(), version);
    }

    private Viaje asignarViaje(Reserva reserva) {
        for (Viaje viaje : repositorio.viajesDe(reserva.usuario())) {
            if (viaje.estado() == Viaje.Estado.ACTIVO && seSolapa(viaje, reserva)) {
                return viaje;
            }
        }
        String lugar = reserva.destino() != null ? reserva.destino()
                : reserva.origen() != null ? reserva.origen() : reserva.tipo();
        Viaje nuevo = new Viaje("v-" + UUID.randomUUID().toString().substring(0, 8), reserva.usuario(), "Viaje a " + lugar);
        repositorio.guardarViaje(nuevo);
        return nuevo;
    }

    private boolean seSolapa(Viaje viaje, Reserva reserva) {
        List<Reserva> reservas = reservasDe(viaje);
        if (reservas.isEmpty()) {
            return false;
        }
        LocalDateTime inicio = reservas.stream().map(Reserva::inicioFecha).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime fin = reservas.stream().map(Reserva::finFecha).max(Comparator.naturalOrder()).orElseThrow();
        return !reserva.inicioFecha().isAfter(fin.plusDays(DIAS_DE_MARGEN))
                && !reserva.finFecha().isBefore(inicio.minusDays(DIAS_DE_MARGEN));
    }

    private List<Reserva> reservasDe(Viaje viaje) {
        return viaje.reservaIds().stream().map(repositorio::reserva).flatMap(Optional::stream).toList();
    }

    private static Evento eventoReserva(Reserva reserva, Viaje viaje, String cambio, int version) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("usuario", reserva.usuario());
        datos.put("viajeId", viaje.id());
        datos.put("viajeNombre", viaje.nombre());
        datos.put("cambio", cambio);
        datos.put("version", version);
        datos.put("reserva", reserva.aMapa());
        return Evento.nuevo(Evento.RESERVA_ACTUALIZADA, datos);
    }
}
