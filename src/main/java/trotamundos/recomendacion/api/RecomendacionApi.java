package trotamundos.recomendacion.api;

import trotamundos.comun.http.Servidor;
import trotamundos.comun.http.Servidor.ErrorHttp;
import trotamundos.comun.http.Servidor.Resultado;
import trotamundos.recomendacion.negocio.ServicioRecomendacion;

import java.util.Map;

/** Endpoints del recomendador: ver sugerencias (UC-9), responderlas (UC-10) y ver o borrar preferencias. */
public final class RecomendacionApi {

    private final ServicioRecomendacion servicio;

    public RecomendacionApi(ServicioRecomendacion servicio) {
        this.servicio = servicio;
    }

    public void registrar(Servidor servidor) {
        servidor.agregar("GET", "/v1/sugerencias", p ->
                Resultado.ok(Map.of("sugerencias", servicio.sugerenciasDe(p.requerirUsuario()))));
        servidor.agregar("POST", "/v1/sugerencias/{id}/respuesta", p -> {
            Object preferencia = p.cuerpo().get("preferencia");
            try {
                return Resultado.ok(servicio.responder(p.requerirUsuario(), p.parametro("id"), p.texto("accion"),
                        preferencia == null ? null : preferencia.toString()));
            } catch (ServicioRecomendacion.NoEncontrada e) {
                throw new ErrorHttp(404, e.getMessage());
            }
        });
        servidor.agregar("GET", "/v1/preferencias", p ->
                Resultado.ok(Map.of("pesos", servicio.preferenciasDe(p.requerirUsuario()))));
        servidor.agregar("DELETE", "/v1/preferencias", p -> {
            servicio.borrarPreferencias(p.requerirUsuario());
            return Resultado.sinContenido();
        });
    }
}
