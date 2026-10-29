package trotamundos.comun.modelo.tipos;

import trotamundos.comun.modelo.Reserva;
import trotamundos.comun.modelo.TipoDeReserva;

import java.util.List;

public final class Auto implements TipoDeReserva {

    @Override
    public String codigo() {
        return "auto";
    }

    @Override
    public List<String> detallesObligatorios() {
        return List.of("rentadora");
    }

    @Override
    public String describir(Reserva reserva) {
        return "auto de " + reserva.detalles().get("rentadora") + " en " + reserva.destino();
    }
}
