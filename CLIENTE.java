
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ThreadLocalRandom;

/*

 */
/**
 *
 * @author User
 */
public class CLIENTE {


    private static final String SERVER_IP = "127.0.0.1";
    int min = 1000;
    int max = 65500;
    int PORT = ThreadLocalRandom.current().nextInt(min, max + 1);


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
    
    // Cambia a 'true' para simular un cliente con poca capacidad de procesamiento
    private static final boolean SIMULAR_CLIENTE_LENTO = false; 

    public static void main(String[] args) {
        
        Socket socket = new Socket(SERVER_IP, PORT)
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in)))

            System.out.println("--- Conectado al servidor ---");
            System.out.println("1. Identifícate escribiendo: AUTH admin 1234");
            System.out.println("2. Luego suscríbete con: SUB FINANZAS");

            // ... (El hilo receiverThread se queda exactamente igual) ...

            // Bucle para enviar comandos desde la consola
            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                out.println(userInput);
                
                if (userInput.equals("UNSUB")) {
                    System.out.println("Suscripción finalizada.");
                }
            }
            
            // Hilo para recibir mensajes del servidor concurrentemente
            Thread receiverThread = new Thread(() -> {
                int expectedSequence = -1;
                try {
                    String serverResponse;
                    while ((serverResponse = in.readLine()) != null) {
                        
                        if (serverResponse.startsWith("DATA")) {
                            String[] parts = serverResponse.split(" ", 3);
                            int seq = Integer.parseInt(parts[1]);
                            String payload = parts[2];

                            // Detección de mensajes fuera de secuencia / perdidos
                            if (expectedSequence != -1 && seq != expectedSequence) {
                                System.err.println(">>> ALERTA: Salto de secuencia detectado. Esperado " 
                                        + expectedSequence + ", recibido " + seq + ". (Datos perdidos) <<<");
                            }
                            System.out.println("[STREAM] Seq:" + seq + " -> " + payload);
                            expectedSequence = seq + 1;

                            // Simulación de procesamiento costoso
                            if (SIMULAR_CLIENTE_LENTO) {
                                Thread.sleep(500); // Lee 1 msg cada 500ms, pero el server manda cada 100ms
                            }

                        } else if (serverResponse.startsWith("WARN")) {
                            System.err.println("[SERVIDOR AVISA]: " + serverResponse);
                        } else {
                            System.out.println("[RESPUESTA]: " + serverResponse);
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println("Conexión finalizada.");
                }
            });
            receiverThread.start();

            // Bucle para enviar comandos desde la consola
            String userInput;
            while ((userInput = stdIn.readLine()) != null) {
                out.println(userInput);
                if (userInput.equals("UNSUB")) {
                    System.out.println("Suscripción finalizada. Puedes cerrar o suscribirte de nuevo.");
                }
            }

        } catch (IOException e) {
            System.err.println("No se pudo conectar al servidor: " + e.getMessage());
        }
    }
}
}
