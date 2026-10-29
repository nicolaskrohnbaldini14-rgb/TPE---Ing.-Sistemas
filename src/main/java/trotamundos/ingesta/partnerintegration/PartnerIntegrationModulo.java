package trotamundos.ingesta.partnerintegration;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.http.Servidor;
import trotamundos.ingesta.partnerintegration.api.WebhookApi;
import trotamundos.ingesta.partnerintegration.negocio.AdaptadorAerolineaDemo;
import trotamundos.ingesta.partnerintegration.negocio.AdaptadorHotelDemo;
import trotamundos.ingesta.partnerintegration.negocio.AdaptadorProveedor;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Módulo Partner Integration: un adaptador por proveedor; publica ReservaDetectada. */
public final class PartnerIntegrationModulo {

    public PartnerIntegrationModulo(Servidor servidor, BusDeEventos bus) {
        // Sumar un proveedor = sumar un adaptador a esta lista.
        Map<String, AdaptadorProveedor> adaptadores = Stream.of(new AdaptadorAerolineaDemo(), new AdaptadorHotelDemo())
                .collect(Collectors.toMap(AdaptadorProveedor::proveedor, a -> a));
        new WebhookApi(adaptadores, bus).registrar(servidor);
    }
}
