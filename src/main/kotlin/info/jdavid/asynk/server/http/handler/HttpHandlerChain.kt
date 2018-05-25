@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.server.Handler
import info.jdavid.asynk.server.http.Headers
import info.jdavid.asynk.server.http.Method
import info.jdavid.asynk.server.http.base.AbstractHttpHandler
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

internal class HttpHandlerChain(
  private val chain: List<AbstractHttpHandler<out Handler.Acceptance, out Context>>
): AbstractHttpHandler<HttpHandlerChain.HandlerAcceptance<out Handler.Acceptance,
  out AbstractHttpHandler.Context>,
  HttpHandlerChain.ChainContext>() {

  override suspend fun context(others: Collection<*>?): ChainContext {
    val map = HashMap<AbstractHttpHandler<out Handler.Acceptance, out Context>, Context>(chain.size)
    chain.forEach {
      map[it] = it.context(map.values)
    }
   return ChainContext(others, map)
  }

  override suspend fun acceptUri(method: Method, uri: String): HandlerAcceptance<out Handler.Acceptance,
    out Context>? {
    for (handler in chain) {
      val acceptance = handler.acceptUri(method, uri)
      if (acceptance != null) return HandlerAcceptance(
        handler, acceptance)
    }
    return null
  }

  override suspend fun handle(acceptance: HandlerAcceptance<out Handler.Acceptance, out Context>,
                              headers: Headers,
                              body: ByteBuffer,
                              socket: AsynchronousSocketChannel,
                              context: ChainContext) {
    acceptance.handle(headers, body, socket, context)
  }

  internal class HandlerAcceptance<ACCEPTANCE: Handler.Acceptance, CONTEXT: Context>(
    private val handler: AbstractHttpHandler<ACCEPTANCE, CONTEXT>,
    private val acceptance: Handler.Acceptance): Handler.Acceptance(acceptance.bodyAllowed,
                                                                    acceptance.bodyRequired
  ) {
    suspend fun handle(headers: Headers,
                       body: ByteBuffer,
                       socket: AsynchronousSocketChannel,
                       context: ChainContext) {
      @Suppress("UNCHECKED_CAST")
      handler.handle(acceptance as ACCEPTANCE, headers, body, socket, context.contexts[handler] as CONTEXT)
    }
  }

  internal class ChainContext(
    others: Collection<*>?,
    val contexts: Map<AbstractHttpHandler<out Handler.Acceptance, out Context>, Context>
  ): Context(others)

}