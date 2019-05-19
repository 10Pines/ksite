import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import java.time.Duration
import java.util.function.BiFunction as JBiFunction
import java.util.function.Function as JFunction

interface SystemMessage
data class ConnectUser(val user: User) : SystemMessage
data class RemoveUser(val userName: String) : SystemMessage
data class SpawnUser(val user: User) : SystemMessage

fun specificUserServiceKey(name: String): ServiceKey<UserActions> =
    ServiceKey.create(UserActions::class.java, "users-$name")

val systemMain: Behavior<SystemMessage> =
    Behaviors.supervise(
        Behaviors.receive<SystemMessage> { context, msg ->
            when (msg) {
                is ConnectUser -> context.spawnAnonymous(userConnector(msg.user, context.self))
                is RemoveUser -> context.spawnAnonymous(userRemover(msg.userName))
                is SpawnUser -> {
                    val newUser = context.spawn(userActor(msg.user.ws), msg.user.name)
                    val userServiceKey = specificUserServiceKey(msg.user.name)
                    context.system.receptionist().tell(Receptionist.register(userServiceKey, newUser))
                    context.log.info("Spawning User[${msg.user.name}]")
                }
            }
            Behavior.same()
        }
    ).onFailure(SupervisorStrategy.restart())

@Suppress("unused")
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
