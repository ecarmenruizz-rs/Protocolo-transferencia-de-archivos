/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.pr2_dar;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 *
 * @author inferken
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
            servidorImplements stub = (servidorImplements) UnicastRemoteObject.exportObject(objExportado, 0);
            // se registra stub en el servidor RMI
            Registry registry = LocateRegistry.getRegistry(); // Puerto por defecto 1099
            // para registralo o sustituirlo
            registry.rebind("server", stub);
        }catch (RemoteException e){
            e.printStackTrace();
            System.exit(0);
        }
        
        System.out.println("Servidor llamado *servidor_flujo* preparado.");
    }
}
