package trotamundos.itinerario.api;

import trotamundos.comun.http.Servidor;
import trotamundos.comun.http.Servidor.ErrorHttp;
import trotamundos.comun.http.Servidor.Resultado;
import trotamundos.itinerario.negocio.NoEncontrado;
import trotamundos.itinerario.negocio.ServicioItinerario;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

/** Endpoints de Itinerary: gestión manual de reservas (UC-5), detalle de un viaje y archivado (UC-6). */
public final class ItinerarioApi {

    private final ServicioItinerario servicio;

    public ItinerarioApi(ServicioItinerario servicio) {
        this.servicio = servicio;
    }

    public void registrar(Servidor servidor) {
        servidor.agregar("POST", "/v1/reservas", p ->
                Resultado.creado(servicio.crearManual(p.requerirUsuario(), p.cuerpo()).aMapa()));
        servidor.agregar("PUT", "/v1/reservas/{id}", p -> buscar(() ->
                Resultado.ok(servicio.modificarManual(p.requerirUsuario(), p.parametro("id"), p.cuerpo()).aMapa())));
        servidor.agregar("DELETE", "/v1/reservas/{id}", p -> buscar(() -> {
            servicio.eliminarManual(p.requerirUsuario(), p.parametro("id"));
            return Resultado.sinContenido();
        }));
        servidor.agregar("GET", "/v1/viajes/{viajeId}", p -> buscar(() ->
                Resultado.ok(servicio.viajeDe(p.requerirUsuario(), p.parametro("viajeId")))));
        // Dispara el archivado a mano; "ahora" permite simular una fecha futura en la demo.
        servidor.agregar("POST", "/v1/admin/archivar", p -> {
            String ahora = p.consulta().get("ahora");
            int archivados = servicio.archivarTerminados(ahora == null ? LocalDateTime.now() : LocalDateTime.parse(ahora));
            return Resultado.ok(Map.of("archivados", archivados));
        });
    }

    private static Resultado buscar(Supplier<Resultado> accion) {
        try {
            return accion.get();
        } catch (NoEncontrado e) {
            throw new ErrorHttp(404, e.getMessage());
        }
    }
}
