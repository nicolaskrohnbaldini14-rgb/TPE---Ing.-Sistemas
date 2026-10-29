package trotamundos;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Outbox;
import trotamundos.comun.http.Servidor;
import trotamundos.comun.modelo.RegistroDeTipos;
import trotamundos.comun.modelo.tipos.Auto;
import trotamundos.comun.modelo.tipos.Hotel;
import trotamundos.comun.modelo.tipos.Vuelo;
import trotamundos.compartir.CompartirModulo;
import trotamundos.ingesta.emailparser.EmailParserModulo;
import trotamundos.ingesta.mailconnector.MailConnectorModulo;
import trotamundos.ingesta.partnerintegration.PartnerIntegrationModulo;
import trotamundos.itinerario.ItinerarioModulo;
import trotamundos.notificaciones.NotificacionesModulo;
import trotamundos.panel.PanelModulo;

import java.io.IOException;
import java.nio.file.Path;

/** El monolito modular: un solo programa que arma todos los módulos y los conecta por el bus. */
public final class Trotamundos {

    private final Servidor servidor;
    private final Outbox outbox;
    private final ItinerarioModulo itinerario;
    private final int puerto;

    private Trotamundos(int puertoPedido, Path carpetaWeb) throws IOException {
        BusDeEventos bus = new BusDeEventos();
        outbox = new Outbox(bus);
        // Plugins de tipos de reserva: sumar trenes = registrar una clase más (QA-10).
        RegistroDeTipos tipos = new RegistroDeTipos()
                .registrar(new Vuelo())
                .registrar(new Hotel())
                .registrar(new Auto());
        servidor = new Servidor(carpetaWeb);

        CompartirModulo compartir = new CompartirModulo(servidor, bus, outbox);
        itinerario = new ItinerarioModulo(servidor, bus, outbox, tipos);
        new PanelModulo(servidor, bus, compartir);
        new NotificacionesModulo(servidor, bus, tipos);
        new EmailParserModulo(bus);
        new MailConnectorModulo(servidor, bus);
        new PartnerIntegrationModulo(servidor, bus);

        puerto = servidor.iniciar(puertoPedido);
    }

    public static Trotamundos iniciar(int puerto, Path carpetaWeb) throws IOException {
        return new Trotamundos(puerto, carpetaWeb);
    }

    public int puerto() {
        return puerto;
    }

    public void detener() {
        servidor.detener();
        itinerario.detener();
        outbox.detener();
    }
}
