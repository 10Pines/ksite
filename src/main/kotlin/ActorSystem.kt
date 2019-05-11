import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import java.time.Duration
import java.util.function.BiFunction as JBiFunction
import java.util.function.Function as JFunction

fun usersServiceKey(name: String): ServiceKey<UserActions> = ServiceKey.create(UserActions::class.java, "users-$name")

val systemMain: Behavior<SystemMessage> =
    Behaviors.receive<SystemMessage> { context, msg ->
        when (msg) {
            is SpawnUser -> {
                val newUser = context.spawn(userActor, msg.name)
                context.system.receptionist().tell(Receptionist.register(usersServiceKey(msg.name), newUser))
            }
            is ReconnectUser -> {
                val userServiceKey = usersServiceKey(msg.name)
                context.ask(
                    Receptionist.Listing::class.java,
                    context.system.receptionist(),
                    Duration.ofSeconds(2),
                    { Receptionist.find(userServiceKey, it) },
                    { listing, _ ->
                        val users = listing!!.getServiceInstances(userServiceKey)
                        WakeUpUser(users.first())
                    }
                )
            }
            is WakeUpUser -> msg.user.tell(WakeUp)
            is RemoveUser -> {
                val userServiceKey = usersServiceKey(msg.name)
                context.ask(
                    Receptionist.Listing::class.java,
                    context.system.receptionist(),
                    Duration.ofSeconds(2),
                    { Receptionist.find(userServiceKey, it) },
                    { listing, _ ->
                        val users = listing!!.getServiceInstances(userServiceKey)
                        StopUser(users.first())
                    }
                )
            }
            is StopUser -> {
                context.stop(msg.user)
            }
        }
        Behavior.same()
    }

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
