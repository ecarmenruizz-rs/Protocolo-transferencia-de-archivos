package com.mycompany.protocolo_practica2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
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

    
    static boolean pausado = false;
    static boolean terminado = false;

    public static void main(String[] args) throws InterruptedException{

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
                if (!verificado) System.out.println("Usuario incorrecto.");
            } while (verificado == false);
            
            System.out.println("Autenticación exitosa. Iniciando stream...");
            System.out.println("Comandos: [p] Pausa/Reanuda | [q] Salir");
            
            // --- INICIAR HILO DE TECLADO ---
            // para que el programa escuche teclas mientras recibe datos
            Thread hiloControl = new Thread(() -> leerTeclado());
            hiloControl.setDaemon(true); // Se cierra al cerrar el principal
            hiloControl.start();
            
            
            // RECIBIR FLUJO------------------------------------------------------------
            // queremos que cuando haya una excepcion pida la palabra anterior 
            int seq= 0;
            while (!terminado){
                if (!pausado) {
                    try {
                        // Llamada al método remoto con el número de secuencia
                        String palabra = stub.NextWord(seq);
                        System.out.println(palabra);
                        seq++;
                        
                        // Pequeña pausa para simular streaming
                        Thread.sleep(500); 
                    } catch (ConnectException e) {
                    // El servidor está caído o no responde → reintentar la misma seq
                    System.err.println("[RECONEXIÓN] Servidor no disponible (seq=" + seq
                            + "). Reintentando en 2 s... (" + e.getMessage() + ")");
                    try { Thread.sleep(2000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    // seq NO se incrementa → pedirá la misma palabra al reconectar

                } catch (ConnectIOException e) {
                    // Fallo de E/S en la conexión (red inestable) → reintentar
                    System.err.println("[RED] Error de E/S en conexión (seq=" + seq
                            + "). Reintentando en 1 s... (" + e.getMessage() + ")");
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    // seq NO se incrementa

                } catch (MarshalException e) {
                    // Error al serializar la llamada (parámetros incorrectos) → saltar palabra
                    System.err.println("[MARSHAL] Error serializando la petición seq=" + seq
                            + ". Saltando palabra. (" + e.getMessage() + ")");
                    seq++; // el problema es la petición, no la red; avanzamos

                } catch (UnmarshalException e) {
                    // Error al deserializar la respuesta → reintentar la misma seq
                    System.err.println("[UNMARSHAL] Respuesta corrupta para seq=" + seq
                            + ". Reintentando... (" + e.getMessage() + ")");
                    try { Thread.sleep(500); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    // seq NO se incrementa

                } catch (RemoteException e) {
                    // Cualquier otro error RMI genérico → reintentar con espera
                    System.err.println("[RMI] RemoteException en seq=" + seq
                            + ". Reintentando en 1 s... (" + e.getMessage() + ")");
                    try { Thread.sleep(1000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    // seq NO se incrementa
                }else {
                    // Si está pausado, esperamos un poco antes de volver a comprobar
                    Thread.sleep(200);
                }
            }
            System.out.println("Streaming finalizado por el usuario.");
 
        } catch (MalformedURLException e) {
            System.err.println("[FATAL] URL del registro RMI malformada: " + e.getMessage());
        } catch (NotBoundException e) {
            System.err.println("[FATAL] Nombre no registrado en el servidor RMI: " + e.getMessage());
        } catch (RemoteException e) {
            System.err.println("[FATAL] No se pudo contactar con el registro RMI: " + e.getMessage());
        } finally {
            lecturaTeclado.close();
        }

    }

    // ── Hilo de teclado ──────────────────────────────────────────────────────
    private static void leerTeclado() {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        try {
            while (!terminado) {
                String linea = teclado.readLine();
                if (linea != null) {
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
                System.out.println("Stream PAUSADO  (escribe 'p' para reanudar)");
                System.out.flush();
            } else {
                pausado = false;
                System.out.println("Stream REANUDADO");
                System.out.flush();
            }
        } else if ("q".equals(linea)) {
            terminado = true;
            System.out.println("Cerrando...");
            System.out.flush();
        } else {
            System.out.println("Comando desconocido. Usa 'p' o 'q'");
            System.out.flush();
        }
    }
}
