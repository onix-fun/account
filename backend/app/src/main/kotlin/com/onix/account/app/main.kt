package com.onix.account.app

import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import com.onix.account.infrastructure.config.EnvConfig
import com.onix.account.infrastructure.db.DatabaseFactory
import com.onix.account.infrastructure.di.appConfigFrom

fun main(args: Array<String>) {
    when (args.firstOrNull() ?: "serve") {
        "config" -> {
            require(args.getOrNull(1) == "validate") { "usage: config validate" }
            EnvConfig.load()
            println("configuration is valid")
        }
        "migrate" -> DatabaseFactory.migrate(appConfigFrom(EnvConfig.load()).postgres)
        "serve" -> {
            val config = EnvConfig.load()
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
        else -> error("usage: serve | migrate | config validate")
    }
}
