import akka.actor.typed.ActorSystem
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Path
import org.http4k.websocket.Websocket

fun connectUser(actorSystem: ActorSystem<SystemMessage>) = { ws: Websocket ->
    val uuidPath = Path.of("uuid")
    val visitorId = uuidPath(ws.upgradeRequest)
    val user = User(visitorId, ws)
    actorSystem.tell(ConnectUser(user))
}

fun removeUser(actorSystem: ActorSystem<SystemMessage>) = { request: Request ->
    val uuidPath = Path.of("uuid")
    val uuid = uuidPath(request)
    actorSystem.tell(RemoveUser(uuid))
    Response(Status.OK)
}
