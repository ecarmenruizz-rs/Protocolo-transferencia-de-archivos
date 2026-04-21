/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.pr2_dar;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author inferken
 */
public class servidorImplements implements servidorInterfaz{
    
    static final String CTRL_ENVIAR_ID = "CTRL 0";
    static final String CTRL_OK = "CTRL 2"; //acepted ID 
    static final String CTRL_ID_MAL = "CTRL 3"; 
    static final String CTRL_ACK = "CTRL 4"; 
    static final String CTRL_CLOSE_CLI = "CTRL 6"; 
    static final String CTRL_CLOSE_SRV = "CTRL 7"; // CLOSE ACEPTADOR POR EL SERVIDOR 
    static final String CTRL_PAUSE = "CTRL 8"; 
    static final String CTRL_RESUME = "CTRL 9";
    
    private volatile boolean conectado = true;
    private volatile boolean pausado   = false;

    
    private final Socket            socket;
    private final int               numCliente;
    private DataInputStream         in;
    private DataOutputStream        out;
    
    public servidorImplements() throws RemoteException {}
    // ── Punto de entrada ────────────────────────────────────────────────────
    @Override
    public void run() throws RemoteException {
        try {
            in  = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            String   raw    = in.readUTF();
            String[] partes = raw.split(" ", 3);
            boolean  altaOk = partes.length == 3
                           && "CTRL".equals(partes[0])
                           && "0".equals(partes[1])
                           && "Jefe".equals(partes[2]);

            if (altaOk) {
                System.out.println("[Servidor] ID recibida: " + partes[2]);
                out.writeUTF(servidorImplements.CTRL_OK);
                System.out.println("[Servidor] Sesión aceptada. Iniciando stream.");

                Thread lector = new Thread(this::hiloLector, "lector-cliente");
                lector.setDaemon(true);
                lector.start();

                enviarFlujo();
            } else {
                System.out.println("[Servidor] Alta incorrecta: " + raw);
                out.writeUTF(servidorImplements.CTRL_ID_KO);
            }

        } catch (IOException ex) {
            if (conectado) System.err.println("[Servidor] Error con cliente: " + ex.getMessage());
        } finally {
            conectado = false;
            try { socket.close(); } catch (IOException ignored) {}
            System.out.println("[Servidor] Socket cerrado.");
        }
    }

    // ── Hilo lector ─────────────────────────────────────────────────────────
    private void hiloLector() {
        try {
            while (conectado) {
                String msg = in.readUTF();
                colaEntrada.put(msg);
            }
        } catch (IOException ex) {
            if (conectado) System.err.println("[Servidor] Hilo lector: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            try { colaEntrada.put(servidor.CTRL_CLOSE_CLI); } catch (InterruptedException ignored) {}
        }
    }

    // ── Hilo emisor ─────────────────────────────────────────────────────────
    private void enviarFlujo() throws IOException {
        String[] palabras  = servidor.TEXTO.split(" ");
        int      total     = palabras.length;
        int      seq       = 0;
        boolean  continuar = true;

        while (conectado && continuar) {

            for (int j = 0; j < servidor.PALABRAS_POR_BLOQUE && conectado && continuar; j++) {

                // Esperar mientras esté pausado
                while (pausado && conectado && continuar) {
                    String ctrl = poll(100);
                    if (ctrl != null) {
                        continuar = procesarControl(ctrl);
                    }
                }

                if (conectado && continuar) {
                    String msg = "Msg " + seq + " " + palabras[seq % total];
                    out.writeUTF(msg);
                    System.out.println("[Servidor] Enviando a Cliente " + numCliente + " -> " + msg);
                    seq++;

                    // Retardo modo lento: poll corto para atender controles durante la espera
                    if (modoLento) {
                        long fin = System.currentTimeMillis() + servidor.RETARDO_LENTO_MS;
                        while (conectado && continuar && !pausado
                                && System.currentTimeMillis() < fin) {
                            long resta = fin - System.currentTimeMillis();
                            String ctrl = poll(resta > 0 ? resta : 1);
                            if (ctrl != null) {
                                continuar = procesarControl(ctrl);
                            }
                        }
                    }
                }
            }

            if (conectado && continuar) {
                continuar = esperarACK();
            }
        }

        if (conectado) {
            out.writeUTF(servidor.CTRL_CLOSE_SRV);
            System.out.println("[Servidor] Fin de stream (CTRL 7) enviado.");
        }
    }

    // ── Esperar ACK con timeout ──────────────────────────────────────────────
    private boolean esperarACK() throws IOException {
        long    deadline  = System.currentTimeMillis() + servidor.ACK_TIMEOUT_MS;
        boolean ackOk     = false;
        boolean continuar = true;

        while (conectado && continuar && !ackOk) {
            long restante = deadline - System.currentTimeMillis();
            if (restante <= 0) {
                continuar = activarModoLentoDesdeServidor();
                ackOk     = continuar;
            } else {
                String msg = poll(restante);
                if (msg == null) {
                    continuar = activarModoLentoDesdeServidor();
                    ackOk     = continuar;
                } else if (servidor.CTRL_ACK.equals(msg)) {
                    System.out.println("[Servidor] ACK recibido.");
                    ackOk = true;
                } else {
                    continuar = procesarControl(msg);
                    if (pausado) {
                        deadline = System.currentTimeMillis() + servidor.ACK_TIMEOUT_MS;
                    }
                }
            }
        }

        return ackOk && continuar;
    }

    // ── Procesar señales de control del cliente ──────────────────────────────
    private boolean procesarControl(String msg) throws IOException {
        boolean continuar = true;

        if (servidor.CTRL_ACK.equals(msg)) {
            System.out.println("[Servidor] ACK (fuera de ventana) recibido.");

        } else if (servidor.CTRL_SLOW.equals(msg)) {
            continuar = toggleModoLento();

        } else if (servidor.CTRL_PAUSE.equals(msg)) {
            System.out.println("[Servidor] Pausa recibida.");
            pausado = true;

        } else if (servidor.CTRL_RESUME.equals(msg)) {
            System.out.println("[Servidor] Reanudación recibida.");
            pausado = false;

        } else if (servidor.CTRL_CLOSE_CLI.equals(msg)) {
            System.out.println("[Servidor] Cierre solicitado por cliente. Enviando CTRL 7.");
            out.writeUTF(servidor.CTRL_CLOSE_SRV);
            conectado = false;
            continuar = false;

        } else {
            System.out.println("[Servidor] Mensaje desconocido: " + msg);
        }

        return continuar;
    }

    // ── Poll con timeout sin propagar InterruptedException en cada llamada ───
    private String poll(long ms) {
        String result = null;
        try {
            result = colaEntrada.poll(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result;
    }
}
