import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import org.http4k.websocket.Websocket
import java.time.Duration

sealed class UserActions
data class WakeUp(val ws: Websocket) : UserActions()
object Kill : UserActions()

private val <T> ActorRef<T>.name: String
    get() = path().name()

fun userActor(ws: Websocket): Behavior<UserActions> = Behaviors.withTimers { timer ->
    val deathTimer = "death"
    val setupDeathTimer = { timer.startSingleTimer(deathTimer, Kill, Duration.ofMinutes(5)) }
    setupDeathTimer()
    Behaviors.receive { ctx, msg ->
        when (msg) {
            is WakeUp -> {
                ctx.system.log().info("${ctx.self.name} received wakeup")
                timer.cancel(deathTimer)
                userActor(msg.ws)
            }
            is Kill -> {
                ctx.system.log().info("Killing ${ctx.self.name}")
                ws.close()
                Behaviors.stopped()
            }
        }
    }
}