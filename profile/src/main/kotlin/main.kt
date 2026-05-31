package profile

import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    val effectiveArgs = if (args.any { it == "-config" || it.startsWith("-config=") }) {
        args
    } else {
        args + "-config=application.yaml"
    }
    EngineMain.main(effectiveArgs)
}
