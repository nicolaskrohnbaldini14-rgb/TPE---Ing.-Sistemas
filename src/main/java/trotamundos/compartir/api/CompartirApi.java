package trotamundos.compartir.api;

import trotamundos.comun.http.Servidor;
import trotamundos.comun.http.Servidor.ErrorHttp;
import trotamundos.comun.http.Servidor.Resultado;
import trotamundos.compartir.negocio.ServicioCompartir;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Endpoints para compartir un viaje con personas designadas (UC: compartir itinerario, QA-9). */
public final class CompartirApi {

    private final ServicioCompartir servicio;

    public CompartirApi(ServicioCompartir servicio) {
        this.servicio = servicio;
    }

    public void registrar(Servidor servidor) {
        servidor.agregar("POST", "/v1/viajes/{viajeId}/compartir", p -> conPermiso(() -> {
            String viajeId = p.parametro("viajeId");
            String email = p.texto("email");
            servicio.compartir(p.requerirUsuario(), viajeId, email);
            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("viajeId", viajeId);
            respuesta.put("email", email.trim().toLowerCase());
            // El link se puede publicar en redes: sin login de una persona designada no muestra nada.
            respuesta.put("link", "/?viaje=" + viajeId);
            return Resultado.creado(respuesta);
        }));
        servidor.agregar("DELETE", "/v1/viajes/{viajeId}/compartir/{email}", p -> conPermiso(() -> {
            servicio.revocar(p.requerirUsuario(), p.parametro("viajeId"), p.parametro("email"));
            return Resultado.sinContenido();
        }));
        servidor.agregar("GET", "/v1/viajes/{viajeId}/compartir", p -> conPermiso(() ->
                Resultado.ok(Map.of("viajeId", p.parametro("viajeId"),
                        "invitados", servicio.invitados(p.requerirUsuario(), p.parametro("viajeId"))))));
    }

    private static Resultado conPermiso(Supplier<Resultado> accion) {
        try {
            return accion.get();
        } catch (ServicioCompartir.SinPermiso e) {
            throw new ErrorHttp(403, e.getMessage());
        }
    }
}
