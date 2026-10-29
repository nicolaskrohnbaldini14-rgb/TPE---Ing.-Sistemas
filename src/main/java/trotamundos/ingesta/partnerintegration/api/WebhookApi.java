package trotamundos.ingesta.partnerintegration.api;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.comun.http.Servidor;
import trotamundos.comun.http.Servidor.ErrorHttp;
import trotamundos.comun.http.Servidor.Resultado;
import trotamundos.comun.modelo.Reserva;
import trotamundos.ingesta.partnerintegration.negocio.AdaptadorProveedor;

import java.util.Map;

/** Webhooks de los proveedores (UC-4): cada uno entra por su adaptador. */
public final class WebhookApi {

    private final Map<String, AdaptadorProveedor> adaptadores;
    private final BusDeEventos bus;

    public WebhookApi(Map<String, AdaptadorProveedor> adaptadores, BusDeEventos bus) {
        this.adaptadores = adaptadores;
        this.bus = bus;
    }

    public void registrar(Servidor servidor) {
        servidor.agregar("POST", "/v1/webhooks/{proveedor}", p -> {
            AdaptadorProveedor adaptador = adaptadores.get(p.parametro("proveedor"));
            if (adaptador == null) {
                throw new ErrorHttp(404, "Proveedor desconocido: " + p.parametro("proveedor")
                        + ". Disponibles: " + adaptadores.keySet());
            }
            Reserva reserva;
            try {
                reserva = adaptador.traducir(p.cuerpo());
            } catch (IllegalArgumentException e) {
                // Mensaje no interpretable: se rechaza sin frenar a los demás (QA-6).
                throw new ErrorHttp(422, e.getMessage());
            }
            bus.publicar(Evento.nuevo(Evento.RESERVA_DETECTADA, Map.of("reserva", reserva.aMapa())));
            return Resultado.aceptado(Map.of("recibido", true, "codigoConfirmacion", reserva.codigoConfirmacion()));
        });
    }
}
