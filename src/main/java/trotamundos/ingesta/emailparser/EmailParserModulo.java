package trotamundos.ingesta.emailparser;

import trotamundos.comun.eventos.BusDeEventos;
import trotamundos.comun.eventos.Evento;
import trotamundos.ingesta.emailparser.negocio.ParserDePlantillas;

import java.util.Map;

/**
 * Módulo Email Parser: consume CorreoDeViajeRecibido y publica ReservaDetectada.
 * No tiene capa api (solo trabaja con eventos) ni tablas (las plantillas están en el código).
 */
public final class EmailParserModulo {

    public EmailParserModulo(BusDeEventos bus) {
        ParserDePlantillas parser = new ParserDePlantillas();
        bus.suscribir(Evento.CORREO_DE_VIAJE_RECIBIDO, e ->
                parser.parsear(e.texto("usuario"), e.texto("remitente"), e.texto("cuerpo")).ifPresentOrElse(
                        reserva -> bus.publicar(Evento.nuevo(Evento.RESERVA_DETECTADA, Map.of("reserva", reserva.aMapa()))),
                        () -> System.out.println("[email-parser] Correo de " + e.texto("remitente")
                                + " sin plantilla conocida: queda para revisión")));
    }
}
