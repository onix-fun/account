package profile.infrastructure.events

import profile.infrastructure.config.SmtpConfig
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets

class SmtpEmailSender(private val config: SmtpConfig) {
    fun sendVerificationCode(email: String, code: String) {
        send(
            to = email,
            subject = "Sparrow email verification",
            body = """
                Your Sparrow verification code is:

                $code

                The code expires in 1 hour.
            """.trimIndent()
        )
    }

    fun sendPasswordReset(email: String, code: String) {
        send(
            to = email,
            subject = "Sparrow password reset",
            body = """
                Use this reset code to set a new Sparrow password:

                $code

                The code expires in 1 hour.
            """.trimIndent()
        )
    }

    private fun send(to: String, subject: String, body: String) {
        Socket(config.host, config.port).use { socket ->
            socket.soTimeout = 5_000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))

            readResponse(reader, "220")
            sendCommand(writer, reader, "EHLO sparrow.local", "250")
            sendCommand(writer, reader, "MAIL FROM:<${config.from}>", "250")
            sendCommand(writer, reader, "RCPT TO:<$to>", "250", "251")
            sendCommand(writer, reader, "DATA", "354")
            writer.write(renderMessage(to, subject, body))
            writer.write("\r\n.\r\n")
            writer.flush()
            readResponse(reader, "250")
            sendCommand(writer, reader, "QUIT", "221")
        }
    }

    private fun renderMessage(to: String, subject: String, body: String): String {
        val escapedBody = body
            .lineSequence()
            .joinToString("\r\n") { line -> if (line.startsWith(".")) ".$line" else line }

        return """
            From: ${config.from}
            To: $to
            Subject: $subject
            MIME-Version: 1.0
            Content-Type: text/plain; charset=UTF-8

            $escapedBody
        """.trimIndent().replace("\n", "\r\n")
    }

    private fun sendCommand(
        writer: BufferedWriter,
        reader: BufferedReader,
        command: String,
        vararg expectedPrefixes: String
    ) {
        writer.write(command)
        writer.write("\r\n")
        writer.flush()
        readResponse(reader, *expectedPrefixes)
    }

    private fun readResponse(reader: BufferedReader, vararg expectedPrefixes: String): String {
        var line = reader.readLine() ?: throw IllegalStateException("SMTP server closed the connection")
        val response = StringBuilder(line)
        val code = line.take(3)

        while (line.length > 3 && line[3] == '-') {
            line = reader.readLine() ?: break
            response.append('\n').append(line)
            if (!line.startsWith("$code-")) break
        }

        val text = response.toString()
        if (expectedPrefixes.none { text.startsWith(it) }) {
            throw IllegalStateException("Unexpected SMTP response: $text")
        }
        return text
    }
}
