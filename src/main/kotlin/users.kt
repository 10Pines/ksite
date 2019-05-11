import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import java.time.Duration

interface UserActions
object Greet : UserActions
object WakeUp : UserActions
object Kill : UserActions

private val <T> ActorRef<T>.name: String
    get() = path().name()

val userActor: Behavior<UserActions> = Behaviors.withTimers { timer ->
    val deathTimer = "death"
    val setupDeathTimer = { timer.startSingleTimer(deathTimer, Kill, Duration.ofMinutes(15)) }
    setupDeathTimer()
    Behaviors.receive { ctx, msg ->
        when (msg) {
            is Greet -> ctx.log.debug("Greet!")
            is WakeUp -> {
                println("${ctx.self.name} received wakeup")
                timer.cancel(deathTimer)
                setupDeathTimer()
            }
            is Kill -> {
                println("killing ${ctx.self.name}")
                return@receive Behaviors.stopped()
            }
        }
        Behavior.same()
    }
}