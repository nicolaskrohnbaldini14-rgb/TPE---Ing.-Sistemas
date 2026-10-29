package trotamundos.comun.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lector y escritor de JSON mínimo, para no depender de librerías externas. */
public final class Json {

    private Json() {
    }

    public static Object parsear(String texto) {
        Lector lector = new Lector(texto);
        lector.espacios();
        Object valor = lector.valor();
        lector.espacios();
        if (!lector.fin()) {
            throw lector.error("sobra texto después del valor");
        }
        return valor;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objeto(String texto) {
        if (texto == null || texto.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object valor = parsear(texto);
        if (!(valor instanceof Map)) {
            throw new IllegalArgumentException("Se esperaba un objeto JSON");
        }
        return (Map<String, Object>) valor;
    }

    public static String escribir(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(valor, sb);
        return sb.toString();
    }

    private static void escribir(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String s) {
            texto(s, sb);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Enum<?> e) {
            texto(e.name(), sb);
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                texto(String.valueOf(entrada.getKey()), sb);
                sb.append(':');
                escribir(entrada.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof Collection<?> lista) {
            sb.append('[');
            boolean primero = true;
            for (Object elemento : lista) {
                if (!primero) {
                    sb.append(',');
                }
                primero = false;
                escribir(elemento, sb);
            }
            sb.append(']');
        } else {
            texto(valor.toString(), sb);
        }
    }

    private static void texto(String s, StringBuilder sb) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Lector {
        private final String s;
        private int pos;

        Lector(String s) {
            this.s = s;
        }

        boolean fin() {
            return pos >= s.length();
        }

        void espacios() {
            while (!fin() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        char actual() {
            if (fin()) {
                throw error("se terminó el texto");
            }
            return s.charAt(pos);
        }

        void esperar(char c) {
            if (actual() != c) {
                throw error("se esperaba '" + c + "'");
            }
            pos++;
        }

        Object valor() {
            espacios();
            return switch (actual()) {
                case '{' -> objeto();
                case '[' -> lista();
                case '"' -> cadena();
                case 't' -> palabra("true", Boolean.TRUE);
                case 'f' -> palabra("false", Boolean.FALSE);
                case 'n' -> palabra("null", null);
                default -> numero();
            };
        }

        Map<String, Object> objeto() {
            Map<String, Object> mapa = new LinkedHashMap<>();
            esperar('{');
            espacios();
            if (actual() == '}') {
                pos++;
                return mapa;
            }
            while (true) {
                espacios();
                String clave = cadena();
                espacios();
                esperar(':');
                mapa.put(clave, valor());
                espacios();
                char c = actual();
                pos++;
                if (c == '}') {
                    return mapa;
                }
                if (c != ',') {
                    throw error("se esperaba ',' o '}'");
                }
            }
        }

        List<Object> lista() {
            List<Object> lista = new ArrayList<>();
            esperar('[');
            espacios();
            if (actual() == ']') {
                pos++;
                return lista;
            }
            while (true) {
                lista.add(valor());
                espacios();
                char c = actual();
                pos++;
                if (c == ']') {
                    return lista;
                }
                if (c != ',') {
                    throw error("se esperaba ',' o ']'");
                }
            }
        }

        String cadena() {
            esperar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = actual();
                pos++;
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char escape = actual();
                pos++;
                switch (escape) {
                    case '"', '\\', '/' -> sb.append(escape);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw error("escape \\u incompleto");
                        }
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw error("escape desconocido \\" + escape);
                }
            }
        }

        Object palabra(String palabra, Object valor) {
            if (!s.startsWith(palabra, pos)) {
                throw error("valor inesperado");
            }
            pos += palabra.length();
            return valor;
        }

        Object numero() {
            int inicio = pos;
            while (!fin() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            String numero = s.substring(inicio, pos);
            if (numero.isEmpty()) {
                throw error("valor inesperado");
            }
            try {
                if (numero.contains(".") || numero.contains("e") || numero.contains("E")) {
                    return Double.parseDouble(numero);
                }
                return Long.parseLong(numero);
            } catch (NumberFormatException e) {
                throw error("número inválido");
            }
        }

        IllegalArgumentException error(String mensaje) {
            return new IllegalArgumentException("JSON inválido en la posición " + pos + ": " + mensaje);
        }
    }
}
