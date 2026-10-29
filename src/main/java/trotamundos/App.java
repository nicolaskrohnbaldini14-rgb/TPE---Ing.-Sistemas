package trotamundos;

import java.io.IOException;
import java.nio.file.Path;

public final class App {

    public static void main(String[] args) throws IOException {
        int puerto = Integer.parseInt(System.getenv().getOrDefault("PUERTO", "8080"));
        Trotamundos app = Trotamundos.iniciar(puerto, Path.of("web"));
        System.out.println("Trotamundos corriendo en http://localhost:" + app.puerto());
        System.out.println("Cortar con Ctrl+C");
    }
}
