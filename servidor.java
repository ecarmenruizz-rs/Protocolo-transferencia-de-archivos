import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor de streaming continuo con control de flujo y gestión de clientes lentos.
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
    static final int    ACK_TIMEOUT_MS      = 4000;
    static final int    RETARDO_LENTO_MS    = 200;

    static final AtomicInteger contadorClientes = new AtomicInteger(0);

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
                Socket cs     = ss.accept();
                int    numCli = contadorClientes.incrementAndGet();
                System.out.println("[Servidor] Cliente " + numCli + " conectado: " + cs.getInetAddress());
                Thread t = new Thread(new ManejadorCliente(cs, numCli));
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

    private final Socket            socket;
    private final int               numCliente;
    private DataInputStream         in;
    private DataOutputStream        out;

    private final BlockingQueue<String> colaEntrada = new ArrayBlockingQueue<>(64);

    private volatile boolean conectado = true;
    private volatile boolean pausado   = false;
    private volatile boolean modoLento = false;

    ManejadorCliente(Socket socket, int numCliente) {
        this.socket     = socket;
        this.numCliente = numCliente;
    }

    // ── Punto de entrada ────────────────────────────────────────────────────
    @Override
    public void run() {
        try {
            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            String   raw    = in.readUTF();
            String[] partes = raw.split(" ", 3);
            boolean  altaOk = partes.length == 3
                           && "CTRL".equals(partes[0])
                           && "0".equals(partes[1])
                           && "Jefe".equals(partes[2]);

            if (altaOk) {
                System.out.println("[Servidor] ID recibida: " + partes[2]);
                out.writeUTF(servidor.CTRL_OK);
                System.out.println("[Servidor] Sesión aceptada. Iniciando stream.");

                Thread lector = new Thread(this::hiloLector, "lector-cliente");
                lector.setDaemon(true);
                lector.start();

                enviarFlujo();
            } else {
                System.out.println("[Servidor] Alta incorrecta: " + raw);
                out.writeUTF(servidor.CTRL_ID_KO);
            }

        } catch (IOException ex) {
            if (conectado) System.err.println("[Servidor] Error con cliente: " + ex.getMessage());
        } finally {
            conectado = false;
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[Servidor] Socket cerrado.");
        }
    }

    // ── Hilo lector ─────────────────────────────────────────────────────────
    private void hiloLector() {
        try {
            while (conectado) {
                String msg = in.readUTF();
                colaEntrada.put(msg);
            }
        } catch (IOException ex) {
            if (conectado) System.err.println("[Servidor] Hilo lector: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            try { colaEntrada.put(servidor.CTRL_CLOSE_CLI); } catch (InterruptedException ignored) {}
        }
    }

    // ── Hilo emisor ─────────────────────────────────────────────────────────
    private void enviarFlujo() throws IOException {
        String[] palabras  = servidor.TEXTO.split(" ");
        int      total     = palabras.length;
        int      seq       = 0;
        boolean  continuar = true;

        while (conectado && continuar) {

            for (int j = 0; j < servidor.PALABRAS_POR_BLOQUE && conectado && continuar; j++) {

                // Esperar mientras esté pausado
                while (pausado && conectado && continuar) {
                    String ctrl = poll(100);
                    if (ctrl != null) {
                        continuar = procesarControl(ctrl);
                    }
                }

                if (conectado && continuar) {
                    String msg = "Msg " + seq + " " + palabras[seq % total];
                    out.writeUTF(msg);
                    System.out.println("[Servidor] Enviando a Cliente " + numCliente + " -> " + msg);
                    seq++;

                    // Retardo modo lento: poll corto para atender controles durante la espera
                    if (modoLento) {
                        long fin = System.currentTimeMillis() + servidor.RETARDO_LENTO_MS;
                        while (conectado && continuar && !pausado
                                && System.currentTimeMillis() < fin) {
                            long resta = fin - System.currentTimeMillis();
                            String ctrl = poll(resta > 0 ? resta : 1);
                            if (ctrl != null) {
                                continuar = procesarControl(ctrl);
                            }
                        }
                    }
                }
            }

            if (conectado && continuar) {
                continuar = esperarACK();
            }
        }

        if (conectado) {
            out.writeUTF(servidor.CTRL_CLOSE_SRV);
            System.out.println("[Servidor] Fin de stream (CTRL 7) enviado.");
        }
    }

    // ── Esperar ACK con timeout ──────────────────────────────────────────────
    private boolean esperarACK() throws IOException {
        long    deadline  = System.currentTimeMillis() + servidor.ACK_TIMEOUT_MS;
        boolean ackOk     = false;
        boolean continuar = true;

        while (conectado && continuar && !ackOk) {
            long restante = deadline - System.currentTimeMillis();
            if (restante <= 0) {
                continuar = activarModoLentoDesdeServidor();
                ackOk     = continuar;
            } else {
                String msg = poll(restante);
                if (msg == null) {
                    continuar = activarModoLentoDesdeServidor();
                    ackOk     = continuar;
                } else if (servidor.CTRL_ACK.equals(msg)) {
                    System.out.println("[Servidor] ACK recibido.");
                    ackOk = true;
                } else {
                    continuar = procesarControl(msg);
                    if (pausado) {
                        deadline = System.currentTimeMillis() + servidor.ACK_TIMEOUT_MS;
                    }
                }
            }
        }

        return ackOk && continuar;
    }

    // ── Procesar señales de control del cliente ──────────────────────────────
    private boolean procesarControl(String msg) throws IOException {
        boolean continuar = true;

        if (servidor.CTRL_ACK.equals(msg)) {
            System.out.println("[Servidor] ACK (fuera de ventana) recibido.");

        } else if (servidor.CTRL_SLOW.equals(msg)) {
            continuar = toggleModoLento();

        } else if (servidor.CTRL_PAUSE.equals(msg)) {
            System.out.println("[Servidor] Pausa recibida.");
            pausado = true;

        } else if (servidor.CTRL_RESUME.equals(msg)) {
            System.out.println("[Servidor] Reanudación recibida.");
            pausado = false;

        } else if (servidor.CTRL_CLOSE_CLI.equals(msg)) {
            System.out.println("[Servidor] Cierre solicitado por cliente. Enviando CTRL 7.");
            out.writeUTF(servidor.CTRL_CLOSE_SRV);
            conectado = false;
            continuar = false;

        } else {
            System.out.println("[Servidor] Mensaje desconocido: " + msg);
        }

        return continuar;
    }

    // ── Modo lento activado por timeout ─────────────────────────────────────
    private boolean activarModoLentoDesdeServidor() throws IOException {
        System.out.println("[Servidor] Timeout de ACK. Enviando CTRL 5 al cliente.");
        out.writeUTF(servidor.CTRL_SLOW);

        long    deadline  = System.currentTimeMillis() + servidor.ACK_TIMEOUT_MS * 2L;
        boolean aceptado  = false;
        boolean continuar = true;

        while (conectado && continuar && !aceptado) {
            long restante = deadline - System.currentTimeMillis();
            if (restante <= 0) {
                System.out.println("[Servidor] Sin respuesta al CTRL 5. Cerrando.");
                conectado = false;
                continuar = false;
            } else {
                String resp = poll(restante);
                if (resp == null) {
                    System.out.println("[Servidor] Sin respuesta al CTRL 5. Cerrando.");
                    conectado = false;
                    continuar = false;
                } else if (servidor.CTRL_SLOW_ACCEPT.equals(resp)) {
                    modoLento = true;
                    System.out.println("[Servidor] Cliente acepta modo lento. Retardo: "
                            + servidor.RETARDO_LENTO_MS + " ms/palabra.");
                    out.writeUTF(servidor.CTRL_SLOW_ACCEPT);
                    aceptado = true;
                } else {
                    continuar = procesarControl(resp);
                }
            }
        }

        return aceptado && continuar;
    }

    // ── Toggle de modo lento por solicitud del cliente ───────────────────────
    private boolean toggleModoLento() throws IOException {
        modoLento = !modoLento;
        System.out.println("[Servidor] Modo lento por solicitud del cliente: "
                + (modoLento ? "ON (" + servidor.RETARDO_LENTO_MS + " ms/palabra)" : "OFF"));
        out.writeUTF(servidor.CTRL_SLOW_ACCEPT);
        return true;
    }

    // ── Poll con timeout sin propagar InterruptedException en cada llamada ───
    private String poll(long ms) {
        String result = null;
        try {
            result = colaEntrada.poll(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }
}
