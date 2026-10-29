package trotamundos.ingesta.mailconnector.negocio;

import trotamundos.ingesta.mailconnector.datos.WhitelistRepositorio;

import java.util.Set;

/**
 * Filtro previo por remitente (ADR-0003, táctica Limit Event Response): solo pasan los correos de
 * remitentes de la whitelist del usuario o del catálogo de remitentes de viaje conocidos.
 */
public final class FiltroDeCorreos {

    /** Dominios de empresas de viaje conocidas. En el diseño este catálogo se mantiene aparte. */
    public static final Set<String> CATALOGO_GLOBAL = Set.of("aerolinea-demo.com", "hotel-demo.com");

    private final WhitelistRepositorio whitelist;

    public FiltroDeCorreos(WhitelistRepositorio whitelist) {
        this.whitelist = whitelist;
    }

    public boolean esDeViaje(String usuario, String remitente) {
        String direccion = remitente.trim().toLowerCase();
        String dominio = direccion.contains("@") ? direccion.substring(direccion.indexOf('@') + 1) : direccion;
        return whitelist.remitentesDe(usuario).contains(direccion) || CATALOGO_GLOBAL.contains(dominio);
    }
}
