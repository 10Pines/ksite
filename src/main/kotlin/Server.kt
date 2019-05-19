import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory
import org.http4k.core.Method
import org.http4k.routing.ResourceLoader.Companion.Classpath
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.routing.websockets
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.PolyHandler

fun main() {
    val actorSystem: ActorSystem<SystemMessage> = ActorSystem.create(systemMain, "ksite", ConfigFactory.load())

    val websocketLayer = websockets(
        "/{uuid}" bind connectUser(actorSystem)
    )
    val httpLayer = routes(
        "/{uuid}" bind Method.DELETE to removeUser(actorSystem),
        "/static" bind static(Classpath("/static"))
    )

    val port = System.getenv("PORT")?.toIntOrNull() ?: 9000
    PolyHandler(httpLayer, websocketLayer).asServer(Jetty(port)).start()
}
