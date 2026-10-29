package trotamundos.ingesta.mailconnector.api;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.comun.http.Servidor;
import trotamundos.comun.http.Servidor.Resultado;
import trotamundos.ingesta.mailconnector.datos.WhitelistRepositorio;
import trotamundos.ingesta.mailconnector.negocio.FiltroDeCorreos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Endpoints de Mail Connector: aviso de correo nuevo (UC-2) y whitelist (UC-3). */
public final class MailConnectorApi {

    private final FiltroDeCorreos filtro;
    private final WhitelistRepositorio whitelist;
    private final BusDeEventos bus;

    public MailConnectorApi(FiltroDeCorreos filtro, WhitelistRepositorio whitelist, BusDeEventos bus) {
        this.filtro = filtro;
        this.whitelist = whitelist;
        this.bus = bus;
    }

    public void registrar(Servidor servidor) {
        // Simula el aviso push del proveedor de correo (Gmail / Outlook) cuando llega un correo nuevo.
        servidor.agregar("POST", "/v1/correos/aviso", p -> {
            String buzon = p.texto("buzon").trim().toLowerCase();
            String remitente = p.texto("remitente");
            Map<String, Object> respuesta = new LinkedHashMap<>();
            if (!filtro.esDeViaje(buzon, remitente)) {
                respuesta.put("procesado", false);
                respuesta.put("motivo", "El remitente no está en la whitelist ni en el catálogo de viajes");
                return Resultado.aceptado(respuesta);
            }
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("usuario", buzon);
            datos.put("remitente", remitente.trim().toLowerCase());
            datos.put("asunto", p.cuerpo().getOrDefault("asunto", ""));
            datos.put("cuerpo", p.texto("cuerpo"));
            bus.publicar(Evento.nuevo(Evento.CORREO_DE_VIAJE_RECIBIDO, datos));
            respuesta.put("procesado", true);
            return Resultado.aceptado(respuesta);
        });
        servidor.agregar("GET", "/v1/correos/whitelist", p -> Resultado.ok(vista(p.requerirUsuario())));
        servidor.agregar("PUT", "/v1/correos/whitelist", p -> {
            String usuario = p.requerirUsuario();
            Object lista = p.cuerpo().get("remitentes");
            if (!(lista instanceof List<?> remitentes)) {
                throw new IllegalArgumentException("Falta la lista 'remitentes'");
            }
            whitelist.reemplazar(usuario, remitentes.stream()
                    .map(r -> r.toString().trim().toLowerCase()).collect(Collectors.toSet()));
            return Resultado.ok(vista(usuario));
        });
    }

    private Map<String, Object> vista(String usuario) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("remitentes", new TreeSet<>(whitelist.remitentesDe(usuario)));
        mapa.put("catalogoGlobal", new TreeSet<>(FiltroDeCorreos.CATALOGO_GLOBAL));
        return mapa;
    }
}
