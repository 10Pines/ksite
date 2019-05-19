import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import org.http4k.websocket.Websocket
import java.time.Duration


data class User(val name: String, val ws: Websocket)

private val <T> ActorRef<T>.name: String
    get() = path().name()

interface UserFinder
data class FoundUser(val user: ActorRef<UserActions>) : UserFinder
object UserNotFound : UserFinder

fun userRemover(userName: String): Behavior<NotUsed> = Behaviors.setup<Any> { ctx ->
    val userServiceKey = specificUserServiceKey(userName)
    val receptionistAdapter = findUserAdapter(ctx, userServiceKey)
    ctx.system.receptionist().tell(Receptionist.find(userServiceKey, receptionistAdapter))
    Behaviors.receiveMessage { msg ->
        when (msg) {
            is FoundUser -> msg.user.tell(Kill)
            is UserNotFound -> ctx.log.warning("Tried to remove an already stopped User[$userName]")
        }
        Behaviors.stopped()
    }
}.narrow()

fun userConnector(user: User, system: ActorRef<SystemMessage>): Behavior<NotUsed> = Behaviors.setup<Any> { ctx ->
    val userServiceKey = specificUserServiceKey(user.name)
    val receptionistAdapter = findUserAdapter(ctx, userServiceKey)
    ctx.system.receptionist().tell(Receptionist.find(userServiceKey, receptionistAdapter))
    Behaviors.receiveMessage { msg ->
        when (msg) {
            is FoundUser -> msg.user.tell(WakeUp(user.ws))
            is UserNotFound -> system.tell(SpawnUser(user))
        }
        Behaviors.stopped()
    }
}.narrow()

private fun findUserAdapter(
    ctx: ActorContext<in UserFinder>,
    userServiceKey: ServiceKey<UserActions>
): ActorRef<Receptionist.Listing>? =
    ctx.messageAdapter(Receptionist.Listing::class.java) { answer ->
        val foundUserActor = answer.getServiceInstances(userServiceKey).toList().firstOrNull()
        foundUserActor?.let { FoundUser(foundUserActor) } ?: UserNotFound
    }


sealed class UserActions
data class WakeUp(val ws: Websocket) : UserActions()
object Kill : UserActions()

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