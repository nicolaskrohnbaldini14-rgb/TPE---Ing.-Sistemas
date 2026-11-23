package trotamundos.recomendacion.negocio;

import trotamundos.comun.eventos.Evento;
import trotamundos.comun.eventos.Outbox;
import trotamundos.ingesta.partnerintegration.PartnerIntegrationModulo;
import trotamundos.recomendacion.datos.RecomendacionRepositorio;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sugerencias proactivas con human-in-the-loop (ADR-0018, ADR-0020):
 * cuando se cancela una reserva se sugiere una alternativa, y cada respuesta del usuario
 * (aceptar, rechazar o ajustar) cambia sus pesos y, si corresponde, la sugerencia.
 */
public final class ServicioRecomendacion {

    /** Cuánto se mueven los pesos con cada aceptación o rechazo. */
    private static final double APRENDIZAJE = 0.1;
    /** Cuánto sube un criterio cuando el usuario lo pide explícitamente ("prefiero sin escalas"). */
    private static final double AJUSTE = 1.0;

    public static final class NoEncontrada extends RuntimeException {
        public NoEncontrada(String mensaje) {
            super(mensaje);
        }
    }

    private final RecomendacionRepositorio repositorio;
    private final Recomendador recomendador;
    private final PartnerIntegrationModulo proveedores;
    private final Outbox outbox;

    public ServicioRecomendacion(RecomendacionRepositorio repositorio, Recomendador recomendador,
                                 PartnerIntegrationModulo proveedores, Outbox outbox) {
        this.repositorio = repositorio;
        this.recomendador = recomendador;
        this.proveedores = proveedores;
        this.outbox = outbox;
    }

    /** UC-9: ante una cancelación, busca alternativas y sugiere la mejor según los pesos del usuario. */
    public synchronized void alActualizarReserva(Evento evento) {
        if (!"CANCELADA".equals(evento.texto("cambio"))) {
            return;
        }
        Map<String, Object> reserva = evento.mapa("reserva");
        String tipo = texto(reserva, "tipo");
        String inicio = texto(reserva, "inicio");
        List<Map<String, Object>> candidatas = proveedores.buscarAlternativas(
                tipo, texto(reserva, "origen"), texto(reserva, "destino"), inicio.substring(0, 10));
        String cancelada = idDeLaCancelada(tipo, reserva);
        candidatas = candidatas.stream().filter(a -> !a.get("id").equals(cancelada)).toList();
        if (candidatas.isEmpty()) {
            System.out.println("[recomendacion] No hay alternativas para " + tipo + " a " + texto(reserva, "destino"));
            return;
        }
        proponer(evento.texto("usuario"), evento.texto("viajeId"), tipo, candidatas, Set.of(), null);
    }

    /** UC-10: el usuario acepta, rechaza o ajusta la sugerencia; eso cambia sus pesos. */
    public synchronized Map<String, Object> responder(String usuario, String sugerenciaId, String accion, String preferencia) {
        Sugerencia sugerencia = repositorio.sugerencia(sugerenciaId)
                .filter(s -> s.usuario().equals(usuario) && s.estado() == Sugerencia.Estado.PENDIENTE)
                .orElseThrow(() -> new NoEncontrada("No hay una sugerencia pendiente con id " + sugerenciaId));
        Map<String, Double> pesos = repositorio.pesosDe(usuario);
        Map<String, Double> valores = valoresDe(sugerencia, pesos);
        Set<String> descartadas = new HashSet<>(sugerencia.descartadas());
        boolean recalcular;

        switch (accion == null ? "" : accion.toUpperCase()) {
            case "ACEPTAR" -> {
                // Lo que tenía la alternativa aceptada pasa a pesar un poco más.
                valores.forEach((criterio, valor) -> pesos.merge(criterio, APRENDIZAJE * valor, Double::sum));
                sugerencia.responder(Sugerencia.Estado.ACEPTADA);
                recalcular = false;
            }
            case "RECHAZAR" -> {
                // Lo que tenía la alternativa rechazada pasa a pesar un poco menos, y no se vuelve a sugerir.
                valores.forEach((criterio, valor) -> pesos.put(criterio, Math.max(0, pesos.getOrDefault(criterio, 0.0) - APRENDIZAJE * valor)));
                descartadas.add(String.valueOf(sugerencia.alternativa().get("id")));
                sugerencia.responder(Sugerencia.Estado.RECHAZADA);
                recalcular = true;
            }
            case "AJUSTAR" -> {
                if (preferencia == null || !recomendador.criterios().contains(preferencia)) {
                    throw new IllegalArgumentException("Para ajustar hay que indicar una preferencia: " + recomendador.criterios());
                }
                pesos.merge(preferencia, AJUSTE, Double::sum);
                sugerencia.responder(Sugerencia.Estado.AJUSTADA);
                recalcular = true;
            }
            default -> throw new IllegalArgumentException("Acción desconocida: usar ACEPTAR, RECHAZAR o AJUSTAR");
        }

        repositorio.guardarPesos(usuario, pesos);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("sugerenciaId", sugerenciaId);
        respuesta.put("usuario", usuario);
        respuesta.put("accion", sugerencia.estado().name());
        respuesta.put("preferencia", preferencia);
        repositorio.guardarRespuesta(respuesta);
        // Primero sale la respuesta y después la sugerencia nueva, así el panel quita la vieja y muestra la nueva.
        outbox.guardar(Evento.nuevo(Evento.SUGERENCIA_RESPONDIDA, respuesta));

        Sugerencia nueva = recalcular
                ? proponer(usuario, sugerencia.viajeId(), sugerencia.tipo(), sugerencia.candidatas(), descartadas, sugerenciaId)
                : null;
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("sugerencia", sugerencia.aMapa());
        resultado.put("nueva", nueva == null ? null : nueva.aMapa());
        resultado.put("pesos", redondear(pesos));
        return resultado;
    }

        /**
     * Cuando un viaje se archiva, sus sugerencias pendientes dejan de tener sentido: se descartan para que
     * no se puedan ver ni responder. No se tocan los pesos, porque el usuario no respondió nada.
     */
    public synchronized void alArchivarViaje(Evento evento) {
        for (Sugerencia sugerencia : repositorio.pendientesDeViaje(evento.texto("viajeId"))) {
            sugerencia.responder(Sugerencia.Estado.DESCARTADA);
        }
    }


    public synchronized List<Map<String, Object>> sugerenciasDe(String usuario) {
        return repositorio.sugerenciasDe(usuario).stream()
                .filter(s -> s.estado() == Sugerencia.Estado.PENDIENTE)
                .map(Sugerencia::aMapa)
                .toList();
    }

    public synchronized Map<String, Double> preferenciasDe(String usuario) {
        return redondear(repositorio.pesosDe(usuario));
    }

    /** Privacidad: el usuario puede borrar lo que el sistema aprendió de él. */
    public synchronized void borrarPreferencias(String usuario) {
        repositorio.borrarPesos(usuario);
    }

    private Sugerencia proponer(String usuario, String viajeId, String tipo, List<Map<String, Object>> candidatas,
                                Set<String> descartadas, String reemplazaA) {
        List<Map<String, Object>> disponibles = candidatas.stream()
                .filter(a -> !descartadas.contains(String.valueOf(a.get("id"))))
                .toList();
        if (disponibles.isEmpty()) {
            return null;
        }
        Recomendador.Puntuada mejor = recomendador.ordenar(disponibles, repositorio.pesosDe(usuario)).get(0);
        Sugerencia sugerencia = new Sugerencia("s-" + UUID.randomUUID().toString().substring(0, 8), usuario, viajeId,
                tipo, mejor.alternativa(), mejor.puntaje(), mejor.motivo(), candidatas, descartadas);
        repositorio.guardar(sugerencia);

        Map<String, Object> datos = new LinkedHashMap<>(sugerencia.aMapa());
        datos.put("sugerenciaId", sugerencia.id());
        datos.put("usuario", usuario);
        datos.put("reemplazaA", reemplazaA);
        outbox.guardar(Evento.nuevo(Evento.SUGERENCIA_GENERADA, datos));
        return sugerencia;
    }

    private Map<String, Double> valoresDe(Sugerencia sugerencia, Map<String, Double> pesos) {
        return recomendador.ordenar(sugerencia.candidatas(), pesos).stream()
                .filter(p -> p.alternativa().get("id").equals(sugerencia.alternativa().get("id")))
                .findFirst()
                .map(p -> new LinkedHashMap<>(p.valores()))
                .orElseGet(LinkedHashMap::new);
    }

    private static String idDeLaCancelada(String tipo, Map<String, Object> reserva) {
        Object detalles = reserva.get("detalles");
        if (!(detalles instanceof Map<?, ?> mapa)) {
            return "";
        }
        return "vuelo".equals(tipo) ? "vuelo|" + mapa.get("numeroVuelo") : tipo + "|" + mapa.get("hotel");
    }

    private static String texto(Map<String, Object> mapa, String campo) {
        Object valor = mapa.get(campo);
        return valor == null ? null : valor.toString();
    }

    private static Map<String, Double> redondear(Map<String, Double> pesos) {
        Map<String, Double> redondeados = new LinkedHashMap<>();
        pesos.forEach((criterio, peso) -> redondeados.put(criterio, Math.round(peso * 100) / 100.0));
        return redondeados;
    }
}
