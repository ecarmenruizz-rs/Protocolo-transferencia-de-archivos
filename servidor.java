
package com.mycompany.protocolo_practica2;


import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 *
 * @author User
 */
public class servidor {
    
   @SuppressWarnings("CallToPrintStackTrace")
   
    public static void main(String[] args) {
        try{
            Registry registry = LocateRegistry.createRegistry(1099);
            System.out.println("Registro RMI creado en el puerto 1099");
        } catch (RemoteException ex){
            System.out.println("Registro RMI no creado");
        }
        
        try{
            // se registra el objeto como stub
            servidorImplements objExportado = (servidorImplements) new servidorImplements();
            // se crea el stub dinamicamente y se asocia al puerto 0
            servidorInterfaz stub = (servidorInterfaz) UnicastRemoteObject.exportObject(objExportado, 0);
            // se registra stub en el servidor RMI
            Registry registry = LocateRegistry.getRegistry(); // Puerto por defecto 1099
            // para registralo o sustituirlo
            registry.rebind("server", stub);
            System.out.println("Servidor RMI listo.");
        }catch (RemoteException e){
            e.printStackTrace();
            System.exit(0);
        }
        
        System.out.println("Servidor llamado *servidor_flujo* preparado.");
    }
}
