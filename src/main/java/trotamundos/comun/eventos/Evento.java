package trotamundos.comun.eventos;

import java.util.Map;
import java.util.UUID;

/** Evento de dominio que viaja por el bus interno. El id permite descartar entregas repetidas. */
public record Evento(String id, String tipo, Map<String, Object> datos) {

    public static final String CORREO_DE_VIAJE_RECIBIDO = "CorreoDeViajeRecibido";
    public static final String RESERVA_DETECTADA = "ReservaDetectada";
    public static final String RESERVA_ACTUALIZADA = "ReservaActualizada";
    public static final String VIAJE_ARCHIVADO = "ViajeArchivado";
    public static final String PERMISO_CAMBIADO = "PermisoCambiado";

    public static Evento nuevo(String tipo, Map<String, Object> datos) {
        return new Evento(UUID.randomUUID().toString(), tipo, datos);
    }

    public String texto(String campo) {
        Object valor = datos.get(campo);
        return valor == null ? null : valor.toString();
    }

    public int entero(String campo) {
        Object valor = datos.get(campo);
        return valor instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(valor));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> mapa(String campo) {
        return (Map<String, Object>) datos.get(campo);
    }
}
