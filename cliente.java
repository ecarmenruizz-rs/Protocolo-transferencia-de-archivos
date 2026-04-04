
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
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
            
            String mensaje;
            String texto;
                
            String ID = "Jefe, 10";
            out.writeUTF(ID); // envia al servidor la ID
            
            do{
                mensaje = in.readUTF(); //queda a la espera hasta que recibe un mensaje
                texto = mensaje.substring(5); // elimino los 6 caracteres que son "Msg " + j + " "
                numeroSecuencia = mensaje.charAt(4); // el numero de secuencia del mensaje recibido está en la posicion 4
                contadorMsgs++;
//                if (numeroSecuencia == contadorMsgs){
//                    System.out.println(mensaje);
//                }else{
//                     
//                }
                System.out.println(mensaje);
                System.out.println("----numeroSec" + numeroSecuencia  + "----contadormsgs: " + contadorMsgs + "-----" + texto);

                
                if (contadorMsgs >= 20){ //envio de un ACK cada 20 msgs recibidos
                    System.out.println("Enviando ACK");
                    out.writeUTF("ACK");
                    contadorMsgs = 0;
                }
                
            }while (!"CLOSED".equals(mensaje));
            
            
            clienteSocket.close(); //cierra la conexion con el cliente
            System.out.println("Desconectado");
        } catch (IOException ex) {
            System.getLogger(cliente.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
            
            
       
    }
}
