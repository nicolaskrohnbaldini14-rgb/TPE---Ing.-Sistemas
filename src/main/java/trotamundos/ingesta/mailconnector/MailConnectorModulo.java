package trotamundos.ingesta.mailconnector;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.http.Servidor;
import trotamundos.ingesta.mailconnector.api.MailConnectorApi;
import trotamundos.ingesta.mailconnector.datos.WhitelistRepositorio;
import trotamundos.ingesta.mailconnector.negocio.FiltroDeCorreos;

/** Módulo Mail Connector: recibe los avisos de correo, filtra y publica CorreoDeViajeRecibido. */
public final class MailConnectorModulo {

    public MailConnectorModulo(Servidor servidor, BusDeEventos bus) {
        WhitelistRepositorio whitelist = new WhitelistRepositorio();
        new MailConnectorApi(new FiltroDeCorreos(whitelist), whitelist, bus).registrar(servidor);
    }
}
