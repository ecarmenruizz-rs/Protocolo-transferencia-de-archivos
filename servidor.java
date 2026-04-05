import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Servidor de streaming continuo con control de flujo y gestión de clientes lentos.
 *
 * === PROTOCOLO ===
 *  Alta              : cliente -> servidor  : ID
 *  Confirmación OK   : servidor -> cliente  : "CTRL 2"
 *  Confirmación KO   : servidor -> cliente  : "CTRL 3"
 *  Mensaje flujo     : servidor -> cliente  : "Msg" SP seq SP palabra
 *  ACK               : cliente -> servidor  : "CTRL 4"  (cada PALABRAS_POR_BLOQUE palabras)
 *  Slow aviso        : servidor -> cliente  : "CTRL 5"  (timeout detectado por servidor)
 *  Slow solicitud    : cliente -> servidor  : "CTRL 5"  (el cliente pide modo lento)
 *  Slow accept       : cliente -> servidor  : "CTRL 1"
 *  Slow confirmación : servidor -> cliente  : "CTRL 1"
 *  Pausa             : cliente -> servidor  : "CTRL 8"
 *  Reanudación       : cliente -> servidor  : "CTRL 9"
 *  Cierre cliente    : cliente -> servidor  : "CTRL 6"
 *  Cierre servidor   : servidor -> cliente  : "CTRL 7"
 *
 * ARQUITECTURA:
 *   - Hilo lector  : lee TODOS los mensajes del cliente y los mete en una cola.
 *   - Hilo emisor  : consume la cola para reaccionar a controles y envía el flujo.
 *   Así se evita la desincronización ACK/PAUSE que bloqueaba la versión anterior.
 */
public class servidor {

    static final String CTRL_OK          = "CTRL 2";
    static final String CTRL_ID_KO       = "CTRL 3";
    static final String CTRL_ACK         = "CTRL 4";
    static final String CTRL_SLOW        = "CTRL 5";
    static final String CTRL_CLOSE_CLI   = "CTRL 6";
    static final String CTRL_CLOSE_SRV   = "CTRL 7";
    static final String CTRL_SLOW_ACCEPT = "CTRL 1";
    static final String CTRL_PAUSE       = "CTRL 8";
    static final String CTRL_RESUME      = "CTRL 9";

    static final int    PUERTO              = 5555;
    static final int    PALABRAS_POR_BLOQUE = 20;
    static final int    ACK_TIMEOUT_MS      = 4000;  // espera máxima de ACK
    static final int    RETARDO_LENTO_MS    = 200;   // ms entre palabras en modo lento

    static final String TEXTO =
        "En un lugar de la Mancha de cuyo nombre no quiero acordarme no ha mucho tiempo que vivía " +
        "un hidalgo de los de lanza en astillero adarga antigua rocín flaco y galgo corredor " +
        "Una olla de algo más vaca que carnero salpicón las más noches duelos y quebrantos los sábados " +
        "lantejas los viernes algún palomino de añadidura los domingos consumían las tres partes de su hacienda " +
        "El resto della concluían sayo de velarte calzas de velludo para las fiestas con sus pantuflos de lo mesmo " +
        "y los días de entresemana se honraba con su vellorí de lo más fino " +
        "Tenía en su casa una ama que pasaba de los cuarenta y una sobrina que no llegaba a los veinte " +
        "y un mozo de campo y plaza que así ensillaba el rocín como tomaba la podadera " +
        "Frisaba la edad de nuestro hidalgo con los cincuenta años era de complexión recia seco de carnes " +
        "enjuto de rostro gran madrugador y amigo de la caza";

    public static void main(String[] args) {
        try (ServerSocket ss = new ServerSocket(PUERTO)) {
            System.out.println("[Servidor] Escuchando en puerto " + PUERTO);
            while (true) {
                Socket cs = ss.accept();
                System.out.println("[Servidor] Cliente conectado: " + cs.getInetAddress());
                Thread t = new Thread(new ManejadorCliente(cs));
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException ex) {
            System.err.println("[Servidor] Error fatal: " + ex.getMessage());
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
class ManejadorCliente implements Runnable {

    private final Socket             socket;
    private DataInputStream          in;
    private DataOutputStream         out;

    // Cola de mensajes del cliente → el hilo lector produce, el emisor consume
    private final BlockingQueue<String> colaEntrada = new ArrayBlockingQueue<>(64);

    private volatile boolean conectado = true;
    private volatile boolean pausado   = false;
    private volatile boolean modoLento = false;

    ManejadorCliente(Socket socket) { this.socket = socket; }

    // ── Punto de entrada ────────────────────────────────────────────────────
    @Override
    public void run() {
        try {
            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Alta: sin timeout para la identificación inicial
            String id = in.readUTF();
            System.out.println("[Servidor] ID recibida: " + id);
            if (!"Jefe".equals(id)) {
                out.writeUTF(servidor.CTRL_ID_KO);
                return;
            }
            out.writeUTF(servidor.CTRL_OK);
            System.out.println("[Servidor] Sesión aceptada. Iniciando stream.");

            // Lanzar hilo lector dedicado
            Thread lector = new Thread(this::hiloLector, "lector-cliente");
            lector.setDaemon(true);
            lector.start();

            // El hilo principal hace de emisor
            enviarFlujo();

        } catch (IOException ex) {
            if (conectado) System.err.println("[Servidor] Error con cliente: " + ex.getMessage());
        } finally {
            conectado = false;
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[Servidor] Socket cerrado.");
        }
    }

    // ── Hilo lector: lee continuamente del socket y encola mensajes ─────────
    private void hiloLector() {
        try {
            while (conectado) {
                String msg = in.readUTF();
                colaEntrada.put(msg); // bloquea solo si la cola está llena (no ocurrirá)
            }
        } catch (IOException ex) {
            if (conectado) System.err.println("[Servidor] Hilo lector: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            // Meter señal de cierre para desbloquear al emisor si está esperando
            try { colaEntrada.put(servidor.CTRL_CLOSE_CLI); } catch (InterruptedException ignored) {}
        }
    }

    // ── Hilo emisor: envía el flujo y gestiona controles ────────────────────
    private void enviarFlujo() throws IOException {
        String[] palabras = servidor.TEXTO.split(" ");
        int total = palabras.length;
        int seq   = 0;

        while (conectado) {
            // Enviar un bloque de PALABRAS_POR_BLOQUE palabras
            for (int j = 0; j < servidor.PALABRAS_POR_BLOQUE && conectado; j++) {

                // Esperar si está pausado (comprueba la cola para reanudar/cerrar)
                while (pausado && conectado) {
                    String ctrl = null;
                    try { ctrl = colaEntrada.poll(100, TimeUnit.MILLISECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    if (ctrl != null) {
                        boolean continuar = procesarControl(ctrl);
                        if (!continuar) return;
                    }
                }
                if (!conectado) break;

                String msg = "Msg " + seq + " " + palabras[seq % total];
                out.writeUTF(msg);
                System.out.println("[Servidor] Enviado -> " + msg);
                seq++;

                // Retardo en modo lento: usar poll para seguir atendiendo controles
                // (pausa, cierre…) durante la espera en lugar de un sleep ciego.
                if (modoLento) {
                    long fin = System.currentTimeMillis() + servidor.RETARDO_LENTO_MS;
                    while (conectado) {
                        long resta = fin - System.currentTimeMillis();
                        if (resta <= 0) break;
                        String ctrl = null;
                        try { ctrl = colaEntrada.poll(resta, TimeUnit.MILLISECONDS); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                        if (ctrl != null) {
                            boolean continuar = procesarControl(ctrl);
                            if (!continuar) return;
                            // Si ahora está pausado, salir del retardo y dejar que
                            // el bucle exterior gestione la pausa
                            if (pausado) break;
                        }
                    }
                }
            }

            if (!conectado) break;

            // Esperar ACK (con timeout) o cualquier señal de control
            if (!esperarACK()) break;
        }

        if (conectado) {
            out.writeUTF(servidor.CTRL_CLOSE_SRV);
            System.out.println("[Servidor] Fin de stream (CTRL 7) enviado.");
        }
    }

    // ── Esperar ACK con timeout ──────────────────────────────────────────────
    private boolean esperarACK() throws IOException {
        long deadline = System.currentTimeMillis() + servidor.ACK_TIMEOUT_MS;

        while (conectado) {
            long restante = deadline - System.currentTimeMillis();
            if (restante <= 0) {
                // Timeout: activar modo lento
                return activarModoLentoDesdeServidor();
            }
            String msg = null;
            try { msg = colaEntrada.poll(restante, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }

            if (msg == null) {
                // Timeout expirado
                return activarModoLentoDesdeServidor();
            }

            // Recibido algo
            if (servidor.CTRL_ACK.equals(msg)) {
                System.out.println("[Servidor] ACK recibido.");
                return true;
            }
            // Otro control: procesarlo y seguir esperando el ACK
            boolean continuar = procesarControl(msg);
            if (!continuar) return false;
            // Si se procesó una pausa, el deadline se reinicia para no penalizar al cliente
            if (!pausado) deadline = System.currentTimeMillis() + servidor.ACK_TIMEOUT_MS;
        }
        return false;
    }

    // ── Procesar señales de control del cliente ──────────────────────────────
    private boolean procesarControl(String msg) throws IOException {
        switch (msg) {

            case servidor.CTRL_ACK:
                // ACK llegado fuera de esperarACK (raro pero posible): ignorar
                System.out.println("[Servidor] ACK (fuera de ventana) recibido.");
                return true;

            case servidor.CTRL_SLOW:
                return toggleModoLento();

            case servidor.CTRL_PAUSE:
                System.out.println("[Servidor] Pausa recibida.");
                pausado = true;
                return true; // el bucle de envío se encargará de esperar

            case servidor.CTRL_RESUME:
                System.out.println("[Servidor] Reanudación recibida.");
                pausado = false;
                return true;

            case servidor.CTRL_CLOSE_CLI:
                System.out.println("[Servidor] Cierre solicitado por cliente. Enviando CTRL 7.");
                out.writeUTF(servidor.CTRL_CLOSE_SRV);
                conectado = false;
                return false;

            default:
                System.out.println("[Servidor] Mensaje desconocido: " + msg);
                return true;
        }
    }

    // ── Modo lento activado por timeout del servidor ─────────────────────────
    // Flujo: servidor envía CTRL 5 → espera CTRL 1 del cliente → confirma CTRL 1
    private boolean activarModoLentoDesdeServidor() throws IOException {
        System.out.println("[Servidor] Timeout de ACK. Enviando CTRL 5 al cliente.");
        out.writeUTF(servidor.CTRL_SLOW);

        // Esperar CTRL 1 del cliente con timeout ampliado
        long deadline = System.currentTimeMillis() + servidor.ACK_TIMEOUT_MS * 2L;
        while (conectado) {
            long restante = deadline - System.currentTimeMillis();
            if (restante <= 0) {
                System.out.println("[Servidor] Sin respuesta al CTRL 5. Cerrando.");
                conectado = false;
                return false;
            }
            String resp = null;
            try { resp = colaEntrada.poll(restante, TimeUnit.MILLISECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }

            if (resp == null) {
                System.out.println("[Servidor] Sin respuesta al CTRL 5. Cerrando.");
                conectado = false;
                return false;
            }
            if (servidor.CTRL_SLOW_ACCEPT.equals(resp)) {
                modoLento = true;
                System.out.println("[Servidor] Cliente acepta modo lento. Retardo: "
                        + servidor.RETARDO_LENTO_MS + " ms/palabra.");
                out.writeUTF(servidor.CTRL_SLOW_ACCEPT); // confirmar con CTRL 1
                return true;
            }
            // Otro control mientras esperamos: procesarlo
            boolean continuar = procesarControl(resp);
            if (!continuar) return false;
        }
        return false;
    }

    // ── Toggle de modo lento solicitado por el cliente (CTRL 5 teclado) ──────
    // Flujo: cliente envía CTRL 5 → servidor hace toggle → confirma con CTRL 1
    private boolean toggleModoLento() throws IOException {
        modoLento = !modoLento;
        System.out.println("[Servidor] Modo lento por solicitud del cliente: "
                + (modoLento ? "ON (" + servidor.RETARDO_LENTO_MS + " ms/palabra)" : "OFF"));
        out.writeUTF(servidor.CTRL_SLOW_ACCEPT); // CTRL 1 como confirmación
        return true;
    }
}