@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.server.http

import info.jdavid.server.Handler
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

open class SimpleHttpHandler: AbstractHttpHandler<Handler.Acceptance>() {

  override suspend fun acceptUri(method: Method, uri: String): Handler.Acceptance? {
    return when (method) {
      is Method.OPTIONS -> Ack(false, false, method, uri)
      is Method.HEAD -> Ack(false, false, method, uri)
      is Method.GET -> Ack(false, false, method, uri)
      is Method.POST -> Ack(true, true, method, uri)
      is Method.PUT -> Ack(true, true, method, uri)
      is Method.DELETE -> Ack(true, false, method, uri)
      is Method.PATCH -> Ack(true, true, method, uri)
      else -> Ack(true, false, method, uri)
    }
  }

  companion object {
    protected class Ack(override val bodyAllowed: Boolean, override val bodyRequired: Boolean,
                        val method: Method, val uri: String): Handler.Acceptance
  }

  override suspend fun handle(acceptance: Handler.Acceptance, headers: Headers, body: ByteBuffer,
                              socket: AsynchronousSocketChannel,
                              context: Any?) {
    if (acceptance is Ack) {
      val str = StringBuilder()
      str.append("${acceptance.method} ${acceptance.uri}\r\n\r\n")
      str.append(headers.lines.joinToString("\r\n"))
      str.append("\n\n")
      val contentType = headers.value(Headers.CONTENT_TYPE) ?: "text/plain"
      val isText =
        contentType.startsWith("text/") ||
          contentType.startsWith("application/") &&
            (contentType.startsWith(MediaType.JAVASCRIPT) ||
              contentType.startsWith(MediaType.JSON) ||
              contentType.startsWith(MediaType.XHTML) ||
              contentType.startsWith(MediaType.WEB_MANIFEST))

      val extra = if (isText) body.remaining() else Math.min(2048, body.remaining() * 2)
      val bytes = str.toString().toByteArray(Charsets.ISO_8859_1)
      socket.aWrite(ByteBuffer.wrap(
        "HTTP/1.1 200 OK\r\nContent-Type: plain/text\r\nContent-Length: ${bytes.size + extra}\r\nConnection: close\r\n\r\n".
          toByteArray(Charsets.US_ASCII)
      ))
      socket.aWrite(ByteBuffer.wrap(bytes))
      if (extra > 0) {
        if (contentType.startsWith("text/") ||
          contentType.startsWith("application/") &&
            (contentType.startsWith(MediaType.JAVASCRIPT) ||
              contentType.startsWith(MediaType.JSON) ||
              contentType.startsWith(MediaType.XHTML) ||
              contentType.startsWith(MediaType.WEB_MANIFEST))) {
          socket.aWrite(body)
        }
        else {
          if (body.remaining() > 1024) {
            val limit = body.limit()
            body.limit(body.position() + 511)
            socket.aWrite(ByteBuffer.wrap(Handler.hex(body).toByteArray(Charsets.US_ASCII)))
            socket.aWrite(ByteBuffer.wrap("....".toByteArray(Charsets.US_ASCII)))
            body.limit(limit)
            body.position(limit - 511)
            socket.aWrite(ByteBuffer.wrap(Handler.hex(body).toByteArray(Charsets.US_ASCII)))
          }
          else {
            socket.aWrite(ByteBuffer.wrap(Handler.hex(body).toByteArray(Charsets.US_ASCII)))
          }
        }
      }
    }
    else reject(socket, body.clear() as ByteBuffer, context)
  }

}