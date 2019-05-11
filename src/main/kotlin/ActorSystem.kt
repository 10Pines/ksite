import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import org.http4k.websocket.Websocket
import java.time.Duration
import java.util.function.BiFunction as JBiFunction
import java.util.function.Function as JFunction

sealed class SystemMessage
data class SpawnUser(val name: String, val ws: Websocket) : SystemMessage()
data class ConnectUser(val name: String, val ws: Websocket) : SystemMessage()
data class WakeUpUser(val user: ActorRef<UserActions>, val ws: Websocket) : SystemMessage()
data class RemoveUser(val name: String) : SystemMessage()
data class StopUser(val user: ActorRef<UserActions>) : SystemMessage()
data class UserNotFound(val name: String) : SystemMessage()

fun usersServiceKey(name: String): ServiceKey<UserActions> = ServiceKey.create(UserActions::class.java, "users-$name")

val systemMain: Behavior<SystemMessage> =
    Behaviors.supervise(
        Behaviors.receive<SystemMessage> { context, msg ->
            when (msg) {
                is ConnectUser -> {
                    val userServiceKey = usersServiceKey(msg.name)
                    context.ask(
                        Receptionist.Listing::class.java,
                        context.system.receptionist(),
                        Duration.ofSeconds(2),
                        { Receptionist.find(userServiceKey, it) },
                        { listing, _ ->
                            val users = listing!!.getServiceInstances(userServiceKey)
                            users.firstOrNull()
                                ?.let { WakeUpUser(it, msg.ws) } ?: SpawnUser(msg.name, msg.ws)
                        }
                    )
                }
                is SpawnUser -> {
                    context.log.info("Spawning User[${msg.name}]")
                    val newUser = context.spawn(userActor(msg.ws), msg.name)
                    context.system.receptionist().tell(Receptionist.register(usersServiceKey(msg.name), newUser))
                }
                is WakeUpUser -> msg.user.tell(WakeUp(msg.ws))
                is RemoveUser -> {
                    val userServiceKey = usersServiceKey(msg.name)
                    context.ask(
                        Receptionist.Listing::class.java,
                        context.system.receptionist(),
                        Duration.ofSeconds(2),
                        { Receptionist.find(userServiceKey, it) },
                        { listing, _ ->
                            val users = listing!!.getServiceInstances(userServiceKey)
                            users.firstOrNull()?.let { StopUser(it) } ?: UserNotFound(msg.name)
                        }
                    )
                }
                is StopUser -> {
                    context.stop(msg.user)
                }
                is UserNotFound -> context.log.warning("Tried to remove an already stopped User[${msg.name}]")
            }
            Behavior.same()
        }
    ).onFailure(SupervisorStrategy.restart())

private fun <Self, Res, Req> ActorContext<Self>.ask(
    clazz: Class<Res>,
    recipient: ActorRef<Req>,
    timeout: Duration,
    prepareMessage: (ActorRef<Res>) -> Req,
    adaptResponse: (Res?, Throwable?) -> Self
) {
    ask(
        clazz,
        recipient,
        timeout,
        JFunction { ref -> prepareMessage(ref) },
        JBiFunction { t, u -> adaptResponse(t, u) }
    )
}
