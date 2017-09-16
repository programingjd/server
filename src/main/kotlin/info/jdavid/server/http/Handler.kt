package info.jdavid.server.http

import info.jdavid.server.SocketConnection
import info.jdavid.server.http.http11.Headers
import java.nio.ByteBuffer

interface Handler {

  /**
   * Sets up the handler.
   * @return this.
   */
  fun setup(): Handler

  /**
   * Returns whether this handler accepts the request by returning either null (the request is not accepted
   * and should be handled by another one further down the chain) or an array of parameters extracted from
   * the url. The array of parameters can be empty but not null for the request to be accepted.
   * @param method the request method.
   * @param uri the request uri.
   * @return null, or an array of parameters.
   */
  fun matches(method: String, uri: String): Array<String>?

  /**
   * Creates the response for an accepted request.
   * @param request the request object.
   * @param params the params returned by the accept method.
   * @return the response builder.
   */
  suspend fun handle(method: String, uri: String, headers: Headers,
                     socketConnection: SocketConnection,
                     deadline: Long,
                     buffer: ByteBuffer,
                     params: Array<String>): Response

  class Response constructor(val status: Int, val headers: Headers,
                             private val writeBody: (suspend (socketConnection: SocketConnection,
                                                              buffer: ByteBuffer, deadline: Long) -> Unit)?) {
    constructor(status: Int, headers: Headers, body: ByteArray):
      this(status, headers, { socketConnection: SocketConnection, buffer: ByteBuffer, deadline: Long ->
        socketConnection.write(deadline, body)
      })
    constructor(status: Int, headers: Headers):
      this(status, headers, null)
    internal operator fun component1() = status
    internal operator fun component2() = headers
    internal operator fun component3() = writeBody
  }

}