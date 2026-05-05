package com.mycompany.protocolo_practica2;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Scanner;

/**
 * Cliente de streaming continuo.
 *
 * Comandos de teclado (escribir y pulsar Enter): p -> pausa / reanuda el stream
 * s -> solicitar al servidor que active/desactive modo lento q -> cierre
 * ordenado
 *
 */
public class cliente {

    static final String IP_SERVER = "127.0.0.1";
    static final int PUERTO = 5555;
    static final int PALABRAS_POR_BLOQUE = 20;

    private static volatile boolean pausado = false;
    private static volatile boolean terminado = false;

    private static volatile DataOutputStream outGlobal = null;

    public static void main(String[] args) throws Object {

        @SuppressWarnings("CallToPrintStackTrace")
        Scanner lecturaTeclado = new Scanner(System.in);

        try {
            String registroURL = "rmi://localhost:1099/server";
            //utiliza Naming.lookup para obtener una referencia al objeto remoto registrado previamente en el servidor
            // la referencia es un stub.
            servidorInterfaz stub = (servidorInterfaz) Naming.lookup(registroURL);
            //invocar ahora los métodos remotos

            // AUTENTICACION------------------------------------------------
            String auth;
            boolean verificado;
            do {
                System.out.println("Introduzca su usuario: ");
                auth = lecturaTeclado.nextLine();
                verificado = stub.authe(auth);
            } while (verificado == false);
            
            // RECIBIR FLUJO------------------------------------------------------------
           int seq= 0;
            do {
                System.out.println(stub.NextWord(seq));
                seq++;
                
            } while (////excepciones); 
 
            
         
        } catch (RemoteException | MalformedURLException | NotBoundException e) {
            e.printStackTrace();
        }

    }

    // ── Hilo de teclado ──────────────────────────────────────────────────────
    private static void leerTeclado() {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (!terminado) {
                String linea = teclado.readLine();
                if (linea != null && outGlobal != null) {
                    procesarComando(linea.trim().toLowerCase());
                }
            }
        } catch (IOException ex) {
            if (!terminado) {
                System.out.println("Error teclado: " + ex.getMessage());
                System.out.flush();
            }
        }
    }

    // ── Procesar comando de teclado ──────────────────────────────────────────
    private static void procesarComando(String linea) throws IOException {
        if ("p".equals(linea)) {
            if (!pausado) {
                pausado = true;
                outGlobal.writeUTF(CTRL_PAUSE);
                System.out.println("Stream PAUSADO  (escribe 'p' para reanudar)");
                System.out.flush();
            } else {
                pausado = false;
                outGlobal.writeUTF(CTRL_RESUME);
                System.out.println("Stream REANUDADO");
                System.out.flush();
            }
        } else if ("s".equals(linea)) {
            outGlobal.writeUTF(CTRL_SLOW);
            System.out.println("Solicitud modo lento enviada");
            System.out.flush();
        } else if ("q".equals(linea)) {
            terminado = true;
            outGlobal.writeUTF(CTRL_CLOSE_CLI);
            System.out.println("Cerrando...");
            System.out.flush();
        } else {
            System.out.println("Comando desconocido. Usa 'p', 's' o 'q'");
            System.out.flush();
        }
    }
}
