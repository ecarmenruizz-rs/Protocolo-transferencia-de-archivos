Protocolo de Streaming continuo con control de flujo (caso 12)
Este proyecto implementa un protocolo de comunicación a nivel de aplicación diseñado para la distribución de flujos de datos en forma de texto en tiempo real. El sistema garantiza la integridad de la entrega mediante mecanismos explícitos de control de flujo y gestión dinámica de clientes lentos.

1. Descripción del protocolo
El protocolo ópera sobre TCP y utiliza una arquitectura de mensajes estructurados para gestionar el ciclo de vida de la conexión y el ritmo de transmisión.

2.  Arquitectura del sistema general:
El sistema sigue un modelo multi hilo con desacoplamiento de entrada y salida.
-	El servidor escucha con RMI en el puerto RMI (1099) y delega cada conexión a un ManejadorCliente independiente.
-	ManejadorCliente: 
Hilo Lector: Monitorea constantemente el socket para capturar comandos de control (pausa, modo lento) y los deposita en una ArrayBlockingQueue.
Hilo Emisor: Consume la cola de control y gestiona el envío del texto proactivo, respetando los estados de pausa y los retardos del modo lento.
-	Cliente: mantiene un hilo dedicado para la entrada de teclado, permitiendo al usuario interactuar con el flujo sin detener la recepción de datos.

3. Requisitos de ejecución:
- Java Runtime Enviroment (JRE): versión 8  superior.
- Conectividad: Acceso al puerto 1099 ( se puede configurar en el código).

4. Instrucciones de lanzamiento 
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
