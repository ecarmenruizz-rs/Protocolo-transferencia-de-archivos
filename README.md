Protocolo de Streaming continuo con control de flujo (caso 12)
Este proyecto implementa un protocolo de comunicación a nivel de aplicación diseñado para la distribución de flujos de datos en forma de texto en tiempo real. El sistema garantiza la integridad de la entrega mediante mecanismos explícitos de control de flujo y gestión dinámica de clientes lentos.

1. Descripción del protocolo
El protocolo ópera sobre TCP y utiliza una arquitectura de mensajes estructurados para gestionar el ciclo de vida de la conexión y el ritmo de transmisión.
•	CTRL 0 : Petición de autenticación enviada por el cliente al iniciar la conexión
•	CTRL 1: Aceptación o confirmación del modo lento.
•	CTRL 2 : Confirmación de autenticación exitosa (servidor al cliente)
•	CTRL 3: Confirmación de autenticación fallida (servidor al cliente)
•	CTRL 4: Mensaje ACK enviado por el cliente cada 20 palabras recibidas.
•	CTRL 5: Aviso de modo lento. Se puede originar por el servidor (si detecta un timeout de ACK) o solicitado de forma manual por el cliente.
•	CTRL 6: Petición de cierre que origina el cliente.
•	CTRL 7: Señal de fin de la transmisión y cierre originada por el servidor ( tanto por desconexión como por haber enviado todo el texto).
•	CTRL 8: Petición de parar el flujo de datos ( del Cliente al Servidor)
•	CTRL 9: Petición de reanudar el flujo de datos (Cliente al Servidor)
Mecanismos de control de flujo
el sistema utiliza una ventana de parada y espera por bloques:
-	el servidor envía una ráfaga de 20 palabras.
-	La transmisión se detiene hasta que el cliente devuelve un ACK (CTRL 4).
-	Si el cliente no responde en cuatro segundos el servidor no interpreta como modo lento (latencia de 200ms entre palabras), para evitar el colapso del sistema.

2.  Arquitectura del sistema general:
El sistema sigue un modelo multi hilo con desacoplamiento de entrada y salida.
-	El servidor escucha en el puerto 5555 y delega cada conexión a un ManejadorCliente independiente.
-	ManejadorCliente: 
Hilo Lector: Monitorea constantemente el socket para capturar comandos de control (pausa, modo lento) y los deposita en una ArrayBlockingQueue.
Hilo Emisor: Consume la cola de control y gestiona el envío del texto proactivo, respetando los estados de pausa y los retardos del modo lento.
-	Cliente: mantiene un hilo dedicado para la entrada de teclado, permitiendo al usuario interactuar con el flujo sin detener la recepción de datos.

3. Requisitos de ejecución:
- Java Runtime Enviroment (JRE): versión 8  superior.
- Conectividad: Acceso al puerto 5555 ( se puede configurar en el código).

4. Instrucciones de lanzamiento 
Compilación, desde la raíz del proyecto o la carpeta de fuentes:
Javac servidor,java
Javac cliente.java
ejecución del servidor
java servidor
el servidor muestra logs en tiempo real que indican la conexión de clientes y el estado de los ACKs recibidos.
Ejecución del cliente; en un terminal distinto (puede ser otra máquina que tiene que configurar la IP correcta).
Java cliente

5.Ejemplos de uso (interfaz cliente)
Una vez que se inició la sesión con el ID “Jefe”, el cliente puede interactuar con los siguientes comandos por teclado:
-	p (pausa): detiene la recepción de datos. El servidor entra en un estado de espera hasta que se envíe una p nuevamente para reanudar el envío de datos.
-	s (slow mode): Activa un retardo de 200 milisegundos entre palabras. Útil para entornos con baja capacidad de procesamiento.
-	Q (quit) :Finaliza la conexión de forma segura, informando al servidor para que libere los recursos del hilo.
