
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;


/**
 *
 * @author infer
 * 
 */

public class cliente {
    public static void main(String[] args){
        
        final String IP_SERVER = "127.0.0.1";
        final int PUERTO = 5555;
        DataInputStream in;
        DataOutputStream out;
        int contadorMsgs = 0;
        int numeroSecuencia;
        try {
            
            Socket clienteSocket = new Socket(IP_SERVER, PUERTO);
            
            in = new DataInputStream(clienteSocket.getInputStream());
            out = new DataOutputStream(clienteSocket.getOutputStream());
            
            
           // Envio de ID al servidor                
            String ID = "Jefe";
            out.writeUTF(ID); // envia al servidor la ID
            
            String confirmacion = in.readUTF();
            switch(confirmacion){
                case "CTRL Accepted ID" -> {
                    // Recepción del flujo
                    String texto;
                    String[] mensaje_div;
                    String[] buffer;

                    do{
                        String mensaje = in.readUTF(); //queda a la espera hasta que recibe un mensaje, que es de la forma "Msg" + nº secuencia + palabra correspondiente
                        mensaje_div = mensaje.split(" ");      // Divido el string por espacios para obtener los distintos campos
                        texto = mensaje_div[2];                         // vease el ABNF de este tipo de mensajes: "Msg" SP numero_secuencia SP [signo_puntuacion] palabra [signo_puntuacion] CRLF
                        numeroSecuencia = Integer.parseInt(mensaje_div[1]);     //Convierto los caracteres correspondientes al numero a un entero

                        System.out.print(mensaje_div[2] + " ");

                        //System.out.println("----numeroSec " + numeroSecuencia  + "----contadormsgs: " + contadorMsgs + "-----" + texto);
                        contadorMsgs++;
                        
                        
                        if (contadorMsgs >= 20){ //envio de un ACK cada 20 msgs recibidos
                            //System.out.println("Enviando ACK");
                            out.writeUTF("ACK");
                            contadorMsgs = 0;
                        }

                    }while (!"CTRL 6".equals(mensaje_div[0] + mensaje_div[1]));
                }
                case "CTRL Denied ID" -> {
                    // error,

                }
                default -> {
                    // error, mal implementado
                }
                
            }
                
            
            clienteSocket.close(); //cierra la conexion con el cliente
            System.out.println("Desconectado");
        } catch (IOException ex) {
            System.getLogger(cliente.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
            
            
       
    }
}
