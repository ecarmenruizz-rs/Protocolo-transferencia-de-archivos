/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.mycompany.pr2_dar;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author inferken
 */
public interface servidorInterfaz extends Remote {
    
// ── Punto de entrada ────────────────────────────────────────────────────
    public void run() throws RemoteException;

    // ── Hilo lector ─────────────────────────────────────────────────────────
    public void hiloLector() throws RemoteException;

    // ── Hilo emisor ─────────────────────────────────────────────────────────
    public void enviarFlujo() throws RemoteException;
       

    // ── Esperar ACK con timeout ──────────────────────────────────────────────
    public boolean esperarACK() throws RemoteException;


    // ── Procesar señales de control del cliente ──────────────────────────────
    public boolean procesarControl(String msg) throws RemoteException;
        

    // ── Poll con timeout sin propagar InterruptedException en cada llamada ───
    public String poll(long ms) throws RemoteException;
}

