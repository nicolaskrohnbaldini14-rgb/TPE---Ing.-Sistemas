package trotamundos.comun.modelo.tipos;

import trotamundos.comun.modelo.Reserva;
import trotamundos.comun.modelo.TipoDeReserva;

import java.util.List;

public final class Hotel implements TipoDeReserva {

    @Override
    public String codigo() {
        return "hotel";
    }

    @Override
    public List<String> detallesObligatorios() {
        return List.of("hotel");
    }

    @Override
    public String describir(Reserva reserva) {
        return "hotel " + reserva.detalles().get("hotel") + " en " + reserva.destino();
    }
}
