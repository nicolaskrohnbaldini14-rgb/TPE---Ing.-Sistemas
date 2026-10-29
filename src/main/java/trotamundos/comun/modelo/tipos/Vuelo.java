package trotamundos.comun.modelo.tipos;

import trotamundos.comun.modelo.Reserva;
import trotamundos.comun.modelo.TipoDeReserva;

import java.util.List;

public final class Vuelo implements TipoDeReserva {

    @Override
    public String codigo() {
        return "vuelo";
    }

    @Override
    public List<String> detallesObligatorios() {
        return List.of("numeroVuelo");
    }

    @Override
    public String describir(Reserva reserva) {
        return "vuelo " + reserva.detalles().get("numeroVuelo") + " (" + reserva.origen() + " → " + reserva.destino() + ")";
    }
}
