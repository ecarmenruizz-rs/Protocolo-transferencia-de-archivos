
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

/*

 */
/**
 *
 * @author User
 */

public class SERVIDOR {

    

    public static  void main(String[] args) { // cuando encendemos el servidor 
        
    Socket socketServidor;
    int port = 12345;
        try {
            socketServidor = new ServerSocket(port);
            ClientConnection client = new ClientConnection(socketServidor);

        } catch (IOException e) {
            System.out.println("Error: no se pudo atender en el puerto " + port);
        }

    }
    
    // --- HILO GENERADOR DE FLUJO CONTINUO ---
    static class StreamGenerator implements Runnable {
        private int sequence = 0;

        @Override
        public void run() {
            while (true) {
                try {
                    sequence++;
                    String payload = "Valor en tiempo real: " + (Math.random() * 100);
                    String message = "DATA " + sequence + " " + payload;

                    for (ClientConnection client : subscribers) {
                        client.enqueueMessage(message);
                    }
                    Thread.sleep(100); // Genera 10 mensajes por segundo
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public static void conexion() { // para atender conexiones en el puerto 

        Socket socketConexion = null;

        try {
            socketConexion = socketConexion.accept();

        } catch (IOException e) {
            System.out.println("Error: no se pudo aceptar la conexion solicitada");
        }

    }

    // --- MANEJADOR DE CLIENTE Y CONTROL DE FLUJO ---
    static class ClientConnection implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        
        // BUFFER DE ENVÍO LÍMITADO (Aquí reside el control de flujo)
        private BlockingQueue<String> sendBuffer = new ArrayBlockingQueue<>(50);
        private boolean isSubscribed = false;
        
        private boolean isAuthenticated = false; // Estado de autenticación

        public ClientConnection(Socket socket) {
            this.socket = socket;
        }

        // Método expuesto al StreamGenerator
        public void enqueueMessage(String msg) {
            if (!isSubscribed) return;
            
            // Si el buffer está lleno, offer() devuelve false (Política de descarte)
            if (!sendBuffer.offer(msg)) {
                System.out.println("Cliente lento detectado (" + socket.getInetAddress() + "). Descartando mensaje.");
                // Intentamos notificar al cliente (si hay espacio)
                sendBuffer.offer("WARN DROP Perdiste datos por lentitud"); 
            }
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Hilo dedicado a vaciar el buffer hacia el socket (productor-consumidor)
                Thread writerThread = new Thread(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            String msg = sendBuffer.take();
                            out.println(msg);
                            if (out.checkError()) break; // Cliente desconectado abruptamente
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                writerThread.start();

                // Bucle principal de lectura de comandos del cliente
                String command;
                while ((command = in.readLine()) != null) {
                    String[] parts = command.split(" ", 2);
                    
                    // 1. FASE DE AUTENTICACIÓN
                    if (!isAuthenticated) {
                        if (parts[0].equals("AUTH") && parts.length == 3) {
                            String user = parts[1];
                            String pass = parts[2];
                            
                            // Validación (Aquí conectarías con una base de datos)
                            if (user.equals("admin") && pass.equals("1234")) {
                                isAuthenticated = true;
                                out.println("ACK AUTH OK");
                                System.out.println("Cliente autenticado con éxito: " + user);
                            } else {
                                out.println("ERR 401 Credenciales incorrectas");
                            }
                        } else {
                            out.println("ERR 403 Debes autenticarte primero (AUTH <usuario> <pass>)");
                        }
                        continue; // Salta a la siguiente iteración del bucle hasta que se autentique
                    }

                    // 2. FASE DE STREAMING (Solo llega aquí si isAuthenticated es true)

                    switch (parts[0]) {
                        case "SUB":
                            if (parts.length > 1 && parts[1].equals("FINANZAS")) {
                                isSubscribed = true;
                                subscribers.add(this);
                                out.println("ACK SUB OK");
                            } else {
                                out.println("ERR 404 Stream inexistente");
                            }
                            break;
                        case "UNSUB":
                            isSubscribed = false;
                            subscribers.remove(this);
                            out.println("ACK UNSUB OK");
                            break;
                        default:
                            out.println("ERR 400 Comando inválido");
                    }
                }
            } catch (IOException e) {
                System.out.println("Cliente desconectado: " + socket.getInetAddress());
            } finally {
                // Limpieza coherente ante desconexión
                isSubscribed = false;
                subscribers.remove(this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}
    public static void leer() { // leer los mensajes que llegan 
        int leidos = 0;
        char bufer[] = new char[255];

        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                        socketConexion.getInputStream()));
        String linea = in.readLine();
    }

    // envia mensajes 
    public static void enviar() {
        char bufer = new char[255];
        PrintWriter out = new PrintWriter(socketConexion.getOutputStream(), true);
        out.println(“mensaje”);
    }
    
    /*
    in.close ();
    out.close ();
    socketConexion.close ();
*/
}
