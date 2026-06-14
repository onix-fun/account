package profile

import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.EngineConnectorBuilder
import io.ktor.server.netty.Netty
import profile.infrastructure.config.EnvConfig
import profile.infrastructure.db.DatabaseFactory
import profile.infrastructure.di.appConfigFrom

fun main(args: Array<String>) {
    when (args.firstOrNull() ?: "serve") {
        "config" -> {
            require(args.getOrNull(1) == "validate") { "usage: config validate" }
            EnvConfig.load()
            println("configuration is valid")
        }
        "migrate" -> DatabaseFactory.migrate(appConfigFrom(EnvConfig.load()).postgres)
        "serve" -> {
            val role = args.firstOrNull { it.startsWith("--role=") }?.substringAfter("=")
            val config = EnvConfig.load(roleOverride = role)
            val connector = EngineConnectorBuilder().apply {
                host = config.property("ktor.deployment.host").getString()
                port = config.property("ktor.deployment.port").getString().toInt()
            }
            embeddedServer(
                factory = Netty,
                environment = applicationEnvironment { this.config = config },
                configure = { connectors.add(connector) },
                module = { module() }
            ).start(wait = true)
        }
        else -> error("usage: serve [--role=api|worker|all] | migrate | config validate")
    }
}
