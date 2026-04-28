package com.mycompany.protocolo_practica2;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

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

    static final String CTRL_ID = "CTRL 0";
    static final String CTRL_OK = "CTRL 2";
    static final String CTRL_ID_KO = "CTRL 3";
    static final String CTRL_ACK = "CTRL 4";
    static final String CTRL_SLOW = "CTRL 5";
    static final String CTRL_CLOSE_CLI = "CTRL 6";
    static final String CTRL_CLOSE_SRV = "CTRL 7";
    static final String CTRL_SLOW_ACCEPT = "CTRL 1";
    static final String CTRL_PAUSE = "CTRL 8";
    static final String CTRL_RESUME = "CTRL 9";

    private static volatile boolean pausado = false;
    private static volatile boolean terminado = false;

    private static volatile DataOutputStream outGlobal = null;

    public static void main(String[] args) {
        try (Socket socket = new Socket(IP_SERVER, PUERTO)) {

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            outGlobal = out;

            out.writeUTF(CTRL_ID + " " + "Jefe");

            String confirmacion = in.readUTF();

            if (CTRL_OK.equals(confirmacion)) {
                System.out.println("Sesión iniciada. Comandos: 'p'=pausa  's'=modo lento  'q'=salir");
                System.out.flush();

                Thread hiloTeclado = new Thread(cliente::leerTeclado, "teclado");
                hiloTeclado.setDaemon(true);
                hiloTeclado.start();

                recibirFlujo(in, out);

            } else if (CTRL_ID_KO.equals(confirmacion)) {
                System.out.println("ID rechazada por el servidor.\n");
                System.out.flush();
            } else {
                System.out.println(" Respuesta desconocida: \n[" + confirmacion + "]");
                System.out.flush();
            }

        } catch (IOException ex) {
            if (!terminado) {
                System.out.println("Error de red: " + ex.getMessage());
                System.out.flush();
            }
        }
        System.out.println("\n Desconectado");
        System.out.flush();
    }

    // ── Bucle de recepción ───────────────────────────────────────────────────
    private static void recibirFlujo(DataInputStream in, DataOutputStream out) throws IOException {
        int contadorBloque = 0;

        while (!terminado) {
            String mensaje;
            try {
                mensaje = in.readUTF();
            } catch (IOException ex) {
                if (!terminado) {
                    throw ex;
                }
                mensaje = null;
            }

            if (mensaje != null) {
                if (mensaje.startsWith("CTRL")) {
                    boolean continuar = procesarControlServidor(mensaje, in, out);
                    terminado = terminado || !continuar;

                } else if (mensaje.startsWith("Msg")) {
                    String[] partes = mensaje.split(" ", 3);
                    if (partes.length < 3) {
                        System.out.println(" \n Malformado: " + mensaje);
                        System.out.flush();
                    } else {
                        System.out.println("[Cliente] #" + partes[1] + " -> " + partes[2]);
                        contadorBloque++;
                        if (contadorBloque >= PALABRAS_POR_BLOQUE) {
                            out.writeUTF(CTRL_ACK);
                            contadorBloque = 0;
                        }
                    }
                } else {
                    System.out.println("\n[" + mensaje + "]");
                    System.out.flush();
                }
            }
        }
    }

 // ── Procesar señales de control del servidor ─────────────────────────────
    private static boolean procesarControlServidor(String msg, DataInputStream in, DataOutputStream out)
            throws IOException {
        boolean continuar = true;

        if (CTRL_CLOSE_SRV.equals(msg)) {
            System.out.println("\n Fin de stream");
        System.out.flush();
            terminado = true;
            continuar = false;

        } else if (CTRL_SLOW.equals(msg)) {
            out.writeUTF(CTRL_SLOW_ACCEPT);
            String confirm = in.readUTF();
            if (CTRL_SLOW_ACCEPT.equals(confirm)) {
                System.out.println("\n Modo lento activado por el servidor");
        System.out.flush();
            } else {
                System.out.println("Respuesta inesperada tras aviso lento: " + confirm);
        System.out.flush();
            }

        } else if (CTRL_SLOW_ACCEPT.equals(msg)) {
            System.out.println("Modo lento confirmado por el servidor");
        System.out.flush();

        } else {
            System.out.println("\n Control desconocido del servidor: " + msg);
        System.out.flush();
        }

        return continuar;
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


