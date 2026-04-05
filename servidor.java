
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author infer
 */
public class servidor { 
    public static void main(String[] args) throws InterruptedException{
        ServerSocket servidorSocket = null;
        Socket clienteSocket = null;
        DataInputStream in;
        DataOutputStream out;
        
        final int puertoEscucha = 5555;
        final int numeroPalabrasSinACK = 20; // numero de palabras que el servidor envia sin necesidad de recibir ACK
        
        String texto = "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor. Una olla de algo más vaca que carnero, salpicón las más noches, duelos y quebrantos los sábados, lantejas los viernes, algún palomino de añadidura los domingos, consumían las tres partes de su hacienda. El resto della concluían sayo de velarte, calzas de velludo para las fiestas, con sus pantuflos de lo mesmo, y los días de entresemana se honraba con su vellorí de lo más fino. Tenía en su casa una ama que pasaba de los cuarenta, y una sobrina que no llegaba a los veinte, y un mozo de campo y plaza, que así ensillaba el rocín como tomaba la podadera. Frisaba la edad de nuestro hidalgo con los cincuenta años; era de complexión recia, seco de carnes, enjuto de rostro, gran madrugador y amigo de la caza. Quieren decir que tenía el sobrenombre de Quijada, o Quesada, que en esto hay alguna diferencia en los autores que deste caso escriben; aunque por conjeturas verosímiles se deja entender que se llamaba Quijana. Pero esto importa poco a nuestro cuento: basta que en la narración dél no se salga un punto de la verdad. Es, pues, de saber que este sobredicho hidalgo, los ratos que estaba ocioso, que eran los más del año, se daba a leer libros de caballerías, con tanta afición y gusto, que olvidó casi de todo punto el ejercicio de la caza, y aun la administración de su hacienda; y llegó a tanto su curiosidad y desatino en esto, que vendió muchas hanegas de tierra de sembradura para comprar libros de caballerías en que leer, y así, llevó a su casa todos cuantos pudo haber dellos; y de todos, ningunos le parecían tan bien como los que compuso el famoso Feliciano de Silva; porque la claridad de su prosa y aquellas entricadas razones suyas le parecían de perlas, y más cuando llegaba a leer aquellos requiebros y cartas de desafíos, donde en muchas partes hallaba escrito: «La razón de la sinrazón que a mi razón se hace, de tal manera mi razón enflaquece, que con razón me quejo de la vuestra fermosura». Y también cuando leía: «... los altos cielos que de vuestra divinidad divinamente con las estrellas os fortifican, y os hacen merecedora ";
        String[] texto_div = texto.split(" "); // Vector de String en el que cada elemento es una palabra
        
        String ACKrecibido = "NACK"; //consideramos que desde el principio se ha recibido un ACK, si luego se recibe NACK cambiará
        
        try {
            servidorSocket = new ServerSocket(puertoEscucha);
            System.out.println("Servidor iniciado");

            while(true){
                clienteSocket = servidorSocket.accept(); // el servidor queda a la espera de conexion por parte de un cliente
                
                System.out.println("Cliente conectado, esperando ID");
                
                in = new DataInputStream(clienteSocket.getInputStream());
                out = new DataOutputStream(clienteSocket.getOutputStream());
                
                boolean IDComprobar;
                IDComprobar = ComprobarID(in, "Jefe");
                
                // Inicio de sesión
                if (IDComprobar){ // el cliente ha introducido la ID correcta
                    out.writeUTF("CTRL 2");
                    
                    System.out.println("Comienza el envio");
                    
                    
                    int i = 0; //iterador que define el grupo de palabras en el que estamos
                    
                    do{
                        
                        for (int j = 0; j < numeroPalabrasSinACK; j++){
                            //System.out.println("Enviando " + j);
                           
                            
                            if (i*numeroPalabrasSinACK+j < texto_div.length){ //reinicio para cuando se acaba el texto
                                out.writeUTF("Msg " + (j+i*numeroPalabrasSinACK) + " " + texto_div[j+i*numeroPalabrasSinACK]);
                                System.out.println("Msg " + (j+i*numeroPalabrasSinACK) + " " + texto_div[j+i*numeroPalabrasSinACK]);                                                
                                //System.out.println((i*numeroPalabrasSinACK+j) + " <= " + (texto_div.length ));
                                //Thread.sleep(10); //Espera 10 ms
                            }
                            
                            if (i*numeroPalabrasSinACK+j >= texto_div.length){
                               
                            }
                        }

                        ACKrecibido = in.readUTF(); // se espera al ACK correspondiente, hasta que no llega no continua
                        
                        i++; //pasa al siguiente grupo de palabras
                        
                        
                        
                        
                    }while("CTRL 4".equals(ACKrecibido));
                    
                    
                }else{
                    out.writeUTF("CTRL 3");
                }           
                
                
                
                clienteSocket.close(); //cierra la conexion con el cliente
                System.out.println("Cliente desconectado");
                
            }
           
            
            
        } catch (IOException ex) {
            System.getLogger(cliente.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        
    }
    
    // Funcion que comprueba que la ID que se introduce es igual a la permitida
    public static boolean ComprobarID (DataInputStream in, String ID_permitida) throws IOException{
        String ID = in.readUTF(); //queda a la espera hasta que recibe un mensaje tipo ID      
        System.out.println("ID recibida: " + ID);
        if (ID.equals(ID_permitida))
            return true;
        else
            return false;
    }
}