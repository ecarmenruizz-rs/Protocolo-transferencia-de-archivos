Protocolo de Streaming continuo con control de flujo (caso 12)
Este proyecto implementa un protocolo de comunicación a nivel de aplicación diseñado para la distribución de flujos de datos en forma de texto en tiempo real. Esta implementación hace uso del concepto de stubs y de programación de protocolos a alto nivel.

1. Descripción del protocolo
El protocolo opera mediante el uso de stubs y de invocaciones remotas de métodos por parte del cliente. Estos métodos remotos se encuentran en el servidor, definidos en el archivo servidorImplements.java.

2.  Arquitectura del sistema general:
El sistema sigue un modelo multi hilo con desacoplamiento de entrada y salida.
-	El servidor escucha con RMI en el puerto RMI (1099) y delega cada conexión a un ManejadorCliente independiente.
-	Cliente: mantiene un hilo dedicado para la entrada de teclado, permitiendo al usuario interactuar con el flujo sin detener la recepción de datos.

3. Requisitos de ejecución:
- Java Runtime Enviroment (JRE): versión 8  superior.
- Conectividad: Acceso al puerto 1099 ( se puede configurar en el código).

4. Instrucciones de lanzamiento
Es necesario tener 
Compilación, desde la raíz del proyecto o la carpeta de fuentes:
Javac servidor,java
Javac cliente.java
ejecución del servidor
java servidor
Ejecución del cliente; en un terminal distinto (puede ser otra máquina que tiene que configurar la IP correcta).
Java cliente

5.Ejemplos de uso (interfaz cliente)
Una vez que se inició la sesión con el ID “jefe”, el cliente puede interactuar con los siguientes comandos por teclado:
-	p (pausa): detiene la recepción de datos. El servidor entra en un estado de espera hasta que se envíe una p nuevamente para reanudar el envío de datos.
-	q (quit) :Finaliza la conexión de forma segura, informando al servidor para que libere los recursos del hilo.
