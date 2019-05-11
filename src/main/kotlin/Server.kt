import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.lens.Path
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.websockets
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.PolyHandler
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.fixedRateTimer

interface SystemMessage
data class SpawnUser(val name: String) : SystemMessage
data class ReconnectUser(val name: String) : SystemMessage
data class WakeUpUser(val user: ActorRef<UserActions>) : SystemMessage
data class RemoveUser(val name: String) : SystemMessage
data class StopUser(val user: ActorRef<UserActions>) : SystemMessage

fun main() {
    val websockets = ConcurrentHashMap<String, Websocket>()
    val uuidPath = Path.of("uuid")

    val actorSystem = ActorSystem.create(systemMain, "ksite")

    val ws = websockets(
        "/{uuid}" bind { ws: Websocket ->
            val visitorId = uuidPath(ws.upgradeRequest)
            if (websockets.containsKey(visitorId)) {
                println("Existing visitor reconnected")
                actorSystem.tell(ReconnectUser(visitorId))
            } else {
                println("New connection with id $visitorId")
                actorSystem.tell(SpawnUser(visitorId))
            }
            websockets[visitorId] = ws
        })

    val http: HttpHandler = routes(
        "/{uuid}" bind Method.DELETE to {
            val uuid = uuidPath(it)
            actorSystem.tell(RemoveUser(uuid))
            val userWs = websockets[uuid]
            userWs?.apply {
                send(WsMessage("""{"type": "CLOSING"}"""))
                close()
            }
            websockets.remove(uuid)
            Response(OK)
        })

    fixedRateTimer(period = 10000) {
        println(actorSystem.printTree())
        println("")
        println("")
        println("")
    }

    PolyHandler(http, ws).asServer(Jetty(9000)).start()
}


