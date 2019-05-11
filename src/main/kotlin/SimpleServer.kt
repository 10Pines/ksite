import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage

fun main() {
    { ws: Websocket -> ws.send(WsMessage("hello")) }.asServer(Jetty(9000)).start()
}