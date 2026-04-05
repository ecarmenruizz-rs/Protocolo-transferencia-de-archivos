import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * Cliente de streaming continuo.
 *
 * === PROTOCOLO ===
 *  Alta              : cliente -> servidor  : ID
 *  Confirmación OK   : servidor -> cliente  : "CTRL 2"
 *  Confirmación KO   : servidor -> cliente  : "CTRL 3"
 *  Mensaje flujo     : servidor -> cliente  : "Msg" SP seq SP palabra
 *  ACK               : cliente -> servidor  : "CTRL 4"  (cada PALABRAS_POR_BLOQUE palabras)
 *  Slow aviso        : servidor -> cliente  : "CTRL 5"
 *  Slow solicitud    : cliente -> servidor  : "CTRL 5"  (toggle vía teclado)
 *  Slow accept       : cliente -> servidor  : "CTRL 1"
 *  Slow confirmación : servidor -> cliente  : "CTRL 1"
 *  Pausa             : cliente -> servidor  : "CTRL 8"
 *  Reanudación       : cliente -> servidor  : "CTRL 9"
 *  Cierre cliente    : cliente -> servidor  : "CTRL 6"
 *  Cierre servidor   : servidor -> cliente  : "CTRL 7"
 *
 * Comandos de teclado (escribir y pulsar Enter):
 *   p  -> pausa / reanuda el stream
 *   s  -> solicitar al servidor que active/desactive modo lento
 *   q  -> cierre ordenado
 */
public class cliente {

    static final String IP_SERVER           = "127.0.0.1";
    static final int    PUERTO              = 5555;
    static final int    PALABRAS_POR_BLOQUE = 20;

    static final String CTRL_OK          = "CTRL 2";
    static final String CTRL_ID_KO       = "CTRL 3";
    static final String CTRL_ACK         = "CTRL 4";
    static final String CTRL_SLOW        = "CTRL 5";
    static final String CTRL_CLOSE_CLI   = "CTRL 6";
    static final String CTRL_CLOSE_SRV   = "CTRL 7";
    static final String CTRL_SLOW_ACCEPT = "CTRL 1";
    static final String CTRL_PAUSE       = "CTRL 8";
    static final String CTRL_RESUME      = "CTRL 9";

    private static volatile boolean pausado   = false;
    private static volatile boolean terminado = false;

    private static volatile DataOutputStream outGlobal = null;

    public static void main(String[] args) {
        try (Socket socket = new Socket(IP_SERVER, PUERTO)) {

            DataInputStream  in  = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            outGlobal = out;

            out.writeUTF("Jefe");

            String confirmacion = in.readUTF();

            switch (confirmacion) {
                case CTRL_OK -> {
                    controlMsg("Sesión iniciada. Comandos: 'p'=pausa  's'=modo lento  'q'=salir");

                    Thread hiloTeclado = new Thread(cliente::leerTeclado, "teclado");
                    hiloTeclado.setDaemon(true);
                    hiloTeclado.start();

                    recibirFlujo(in, out);
                }
                case CTRL_ID_KO -> controlMsg("ID rechazada por el servidor.");
                default         -> controlMsg("Respuesta desconocida: " + confirmacion);
            }

        } catch (IOException ex) {
            if (!terminado) controlMsg("Error de red: " + ex.getMessage());
        }
        controlMsg("Desconectado.");
    }

    // ── Utilidades de salida ─────────────────────────────────────────────────

    /** Imprime una palabra del stream en la misma línea, separada por espacio. */
    private static void palabra(String w) {
        System.out.print(w + " ");
        System.out.flush();
    }

    /** Imprime un mensaje de control en su propia línea (con salto antes y después). */
    private static void controlMsg(String msg) {
        System.out.println("\n[" + msg + "]");
        System.out.flush();
    }

    // ── Bucle de recepción ───────────────────────────────────────────────────
    private static void recibirFlujo(DataInputStream in, DataOutputStream out) throws IOException {
        int contadorBloque = 0;

        while (!terminado) {
            String mensaje;
            try {
                mensaje = in.readUTF();
            } catch (IOException ex) {
                if (!terminado) throw ex;
                break;
            }

            if (mensaje.startsWith("CTRL")) {
                boolean continuar = procesarControlServidor(mensaje, in, out);
                if (!continuar) break;
                continue;
            }

            if (!mensaje.startsWith("Msg")) {
                controlMsg("Desconocido: " + mensaje);
                continue;
            }

            String[] partes = mensaje.split(" ", 3);
            if (partes.length < 3) { controlMsg("Malformado: " + mensaje); continue; }

            palabra(partes[2]);

            contadorBloque++;
            if (contadorBloque >= PALABRAS_POR_BLOQUE) {
                out.writeUTF(CTRL_ACK);
                contadorBloque = 0;
            }
        }
    }

    // ── Procesar señales de control del servidor ─────────────────────────────
    private static boolean procesarControlServidor(String msg, DataInputStream in, DataOutputStream out)
            throws IOException {
        switch (msg) {

            case CTRL_CLOSE_SRV:
                controlMsg("Fin de stream.");
                terminado = true;
                return false;

            case CTRL_SLOW:
                // El servidor detectó que vamos lentos: aceptamos modo lento.
                out.writeUTF(CTRL_SLOW_ACCEPT);
                String confirm = in.readUTF();
                if (CTRL_SLOW_ACCEPT.equals(confirm)) {
                    controlMsg("Modo lento activado por el servidor");
                } else {
                    controlMsg("Respuesta inesperada tras aviso lento: " + confirm);
                }
                return true;

            case CTRL_SLOW_ACCEPT:
                // Confirmación del servidor tras nuestra solicitud de toggle (tecla 's')
                controlMsg("Modo lento confirmado por el servidor");
                return true;

            default:
                controlMsg("Control desconocido del servidor: " + msg);
                return true;
        }
    }

    // ── Hilo de teclado ──────────────────────────────────────────────────────
    private static void leerTeclado() {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (!terminado) {
                String linea = teclado.readLine();
                if (linea == null) break;
                linea = linea.trim().toLowerCase();
                if (outGlobal == null) continue;

                switch (linea) {
                    case "p":
                        if (!pausado) {
                            pausado = true;
                            outGlobal.writeUTF(CTRL_PAUSE);
                            controlMsg("Stream PAUSADO  (escribe 'p' para reanudar)");
                        } else {
                            pausado = false;
                            outGlobal.writeUTF(CTRL_RESUME);
                            controlMsg("Stream REANUDADO");
                        }
                        break;

                    case "s":
                        outGlobal.writeUTF(CTRL_SLOW);
                        controlMsg("Solicitud modo lento enviada");
                        break;

                    case "q":
                        terminado = true;
                        outGlobal.writeUTF(CTRL_CLOSE_CLI);
                        controlMsg("Cerrando...");
                        break;

                    default:
                        controlMsg("Comando desconocido. Usa 'p', 's' o 'q'");
                }
            }
        } catch (IOException ex) {
            if (!terminado) controlMsg("Error teclado: " + ex.getMessage());
        }
    }
}