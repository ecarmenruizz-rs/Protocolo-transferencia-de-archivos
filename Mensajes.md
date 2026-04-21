# Mensajes
## Tipo 0 Errores:
- 0: mal formato ABNF
	- envie 0 
- 1: usuario y contraseña mal
	- envie 0
- 2: no existe la opción
	- envie 6
- 3: vas lento
	- enviar 5
- 4: comando no permitido
	- por ejemplo que el cliente envie datos
		- *ignorar*
- 5: cliente vuelve a intentar autentificarse
	- *ignorar*
## Tipo 1 Control
### Formato: CTRL [codigo]
- 0: enviar ID
	- CTRL 0 Quijote
- 1: 
- 2: Accepted ID
	- CTRL 1
- 3: Denied ID
	- CTRL 2
- 4: ACK (cada 20 msgs por ejemplo, configurable)
	- CTRL 4
- 5: detecto cliente lento (su buffer está lleno)
	- CTRL 5
- 6: Solicito CLOSED
	- CTRL 6
- 7: Closed accepted
	- CTRL 7
# Tipo 2 Mensajes
## Formato: Msg [numero secuencia] [palabra]
- 0: Mensaje
	- Msg 
