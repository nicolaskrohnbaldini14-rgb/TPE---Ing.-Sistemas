package trotamundos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Hace cumplir las fronteras entre módulos del ADR-0001: cada módulo solo puede usar el módulo Común
 * y, de los demás, solo los que figuran en la vista de módulos de la Wiki, y siempre a través de su
 * fachada pública (la clase ...Modulo). Falla si alguien saltea una frontera.
 */
public final class PruebaFronteras {

    private static final Path FUENTES = Path.of("src", "main", "java", "trotamundos");

    /** Dependencias permitidas entre módulos, tal como están en la vista de módulos. */
    private static final Map<String, Set<String>> PERMITIDAS = Map.of(
            "panel", Set.of("compartir"),
            "recomendacion", Set.of("ingesta.partnerintegration"));

    private static final List<String> MODULOS = List.of(
            "ingesta.mailconnector", "ingesta.emailparser", "ingesta.partnerintegration",
            "itinerario", "panel", "compartir", "notificaciones", "recomendacion");

    private static final Pattern IMPORT = Pattern.compile("^import (?:static )?trotamundos\\.([\\w.]+);", Pattern.MULTILINE);

    public static void main(String[] args) throws IOException {
        List<String> violaciones = new ArrayList<>();
        for (String modulo : MODULOS) {
            Path carpeta = FUENTES.resolve(modulo.replace('.', '/'));
            try (Stream<Path> archivos = Files.walk(carpeta)) {
                for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                    revisar(modulo, archivo, violaciones);
                }
            }
        }
        if (!violaciones.isEmpty()) {
            System.out.println("FALLÓ: hay módulos que saltean las fronteras de la vista de módulos:");
            violaciones.forEach(v -> System.out.println("  - " + v));
            System.exit(1);
        }
        System.out.println("OK  los " + MODULOS.size() + " módulos solo usan Común y las fachadas permitidas por la vista de módulos");
        System.out.println("\nPrueba de fronteras OK.");
    }

    private static void revisar(String modulo, Path archivo, List<String> violaciones) throws IOException {
        Matcher importacion = IMPORT.matcher(Files.readString(archivo, StandardCharsets.UTF_8));
        while (importacion.find()) {
            String destino = importacion.group(1);
            if (destino.startsWith("comun.") || destino.startsWith(modulo + ".")) {
                continue;
            }
            String otro = MODULOS.stream().filter(m -> destino.startsWith(m + ".")).findFirst().orElse(null);
            if (otro == null) {
                continue;
            }
            String clase = destino.substring(otro.length() + 1);
            boolean permitido = PERMITIDAS.getOrDefault(modulo, Set.of()).contains(otro);
            boolean esFachada = !clase.contains(".") && clase.endsWith("Modulo");
            if (!permitido) {
                violaciones.add(modulo + " depende de " + otro + " (" + archivo.getFileName() + "), y eso no está en la vista de módulos");
            } else if (!esFachada) {
                violaciones.add(modulo + " usa " + destino + " (" + archivo.getFileName() + "): solo se puede usar la fachada de " + otro);
            }
        }
    }
}
