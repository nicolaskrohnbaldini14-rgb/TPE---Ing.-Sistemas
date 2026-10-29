package trotamundos.panel.api;

import trotamundos.comun.http.Servidor;
import trotamundos.comun.http.Servidor.ErrorHttp;
import trotamundos.comun.http.Servidor.Resultado;
import trotamundos.panel.negocio.ProyeccionPanel;

/** Endpoints de lectura del panel (UC-1, QA-2). */
public final class PanelApi {

    private final ProyeccionPanel proyeccion;

    public PanelApi(ProyeccionPanel proyeccion) {
        this.proyeccion = proyeccion;
    }

    public void registrar(Servidor servidor) {
        servidor.agregar("GET", "/v1/panel", p -> Resultado.ok(proyeccion.panelDe(p.requerirUsuario())));
        servidor.agregar("GET", "/v1/panel/viajes/{viajeId}", p -> {
            try {
                return proyeccion.viaje(p.requerirUsuario(), p.parametro("viajeId"))
                        .map(Resultado::ok)
                        .orElseThrow(() -> new ErrorHttp(404, "No existe el viaje " + p.parametro("viajeId")));
            } catch (ProyeccionPanel.SinAcceso e) {
                throw new ErrorHttp(403, e.getMessage());
            }
        });
    }
}
