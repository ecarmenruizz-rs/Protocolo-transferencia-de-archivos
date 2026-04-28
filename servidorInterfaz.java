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
    
    public void run()throws IOException ; 
    public void hiloLector() throws IOException; 
    public void enviarFlujo() throws IOException;
    public boolean esperarACK() throws IOException ; 
    public boolean procesarControl(String msg) throws IOException ;
    public String poll(long ms)throws IOException ;
    
}
