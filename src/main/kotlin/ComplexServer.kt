import org.http4k.format.Gson.auto
import org.http4k.routing.bind
import org.http4k.routing.websockets
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import kotlin.concurrent.fixedRateTimer


data class Person(val name: String, val age: Int)


fun main() {
    val personLens = WsMessage.auto<Person>().toLens()
    val app = websockets(
        "/ageMe" bind { ws: Websocket ->
            val timer = fixedRateTimer("send ping", period = 2000) {
                ping(ws)
            }

            ws.onMessage {
                val person = personLens(it)
                ws.send(personLens.create(person.copy(age = person.age + 10)))
            }

            ws.onClose {
                timer.cancel()
            }
        }
    )
    app.asServer(Jetty(9000)).start()
}

fun ping(ws: Websocket) {
    ws.send(WsMessage(""" {"type": "ping"} """))
}
