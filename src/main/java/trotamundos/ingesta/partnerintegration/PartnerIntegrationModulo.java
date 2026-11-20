package trotamundos.ingesta.partnerintegration;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.http.Servidor;
import trotamundos.ingesta.partnerintegration.api.WebhookApi;
import trotamundos.ingesta.partnerintegration.negocio.AdaptadorAerolineaDemo;
import trotamundos.ingesta.partnerintegration.negocio.AdaptadorHotelDemo;
import trotamundos.ingesta.partnerintegration.negocio.AdaptadorProveedor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Módulo Partner Integration: un adaptador por proveedor; publica ReservaDetectada.
 * Su interfaz pública para otros módulos es {@link #buscarAlternativas}, que usa el recomendador.
 */
public final class PartnerIntegrationModulo {

    private final Map<String, AdaptadorProveedor> adaptadores;

    public PartnerIntegrationModulo(Servidor servidor, BusDeEventos bus) {
        // Sumar un proveedor = sumar un adaptador a esta lista.
        adaptadores = Stream.of(new AdaptadorAerolineaDemo(), new AdaptadorHotelDemo())
                .collect(Collectors.toMap(AdaptadorProveedor::proveedor, a -> a));
        new WebhookApi(adaptadores, bus).registrar(servidor);
    }

    /** Junta las alternativas que ofrecen todos los proveedores para ese tipo, lugar y fecha (yyyy-MM-dd). */
    public List<Map<String, Object>> buscarAlternativas(String tipo, String origen, String destino, String fecha) {
        List<Map<String, Object>> alternativas = new ArrayList<>();
        for (AdaptadorProveedor adaptador : adaptadores.values()) {
            alternativas.addAll(adaptador.alternativas(tipo, origen, destino, fecha));
        }
        return alternativas;
    }
}
