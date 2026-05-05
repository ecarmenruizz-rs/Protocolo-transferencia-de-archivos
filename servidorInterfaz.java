/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.mycompany.protocolo_practica2;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 *
 * @author User
 */
public interface servidorInterfaz extends Remote {

    public String NextWord() throws RemoteException;

    public boolean authe(String auth) throws RemoteException;

}
