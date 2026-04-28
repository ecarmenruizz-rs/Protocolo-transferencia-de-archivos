/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.mycompany.protocolo_practica2;

import java.io.IOException;
import java.rmi.Remote;

/**
 *
 * @author User
 */
public interface servidorInterfaz extends Remote{
    
    public void enviarFlujo() throws RemoteException;
    public boolean esperarACK() throws RemoteException ; 
    public boolean procesarControl(String msg) throws RemoteException ;
    public String poll(long ms)throws RemoteException ;
    public boolean auth(Stream auth) throws RemoteException;
    
}
