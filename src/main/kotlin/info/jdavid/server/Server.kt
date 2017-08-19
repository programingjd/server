package info.jdavid.server

import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.InterruptedByTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class Server internal constructor(address: InetSocketAddress,
                                  readTimeoutMillis: Long, writeTimeoutMillis: Long,
                                  maxHeaderSize: Int, maxRequestSize: Int,
                                  requestHandler: RequestHandler,
                                  cores: Int, cert: () -> ByteArray?) {
  @Suppress("ObjectLiteralToLambda")
  private val looper = Executors.newSingleThreadExecutor(object: ThreadFactory {
    override fun newThread(r: Runnable): Thread {
      val t = Thread(null, r, "Socket ${address.hostName}:${address.port}")
      t.priority = Thread.MAX_PRIORITY
      return t
    }
  })
  private val dispatcher = looper.asCoroutineDispatcher()
  private val serverChannel = openChannel(address)
  private val job = launch(looper.asCoroutineDispatcher()) {
    val ssl = SSL.createSSLContext(cert())
    val pool = ForkJoinPool(cores)
    println("Started listening on ${address.hostName}:${address.port}")
    val nodes = LockFreeLinkedListHead()
    while (true) {
      try {
        val clientChannel = serverChannel.accept().get()
        val clientAddress = clientChannel.remoteAddress as InetSocketAddress
        if (requestHandler.reject(clientAddress)) continue
        launch(pool.asCoroutineDispatcher()) {
          val channel = if (ssl == null) {
            InsecureChannel(clientChannel, nodes, maxRequestSize)
          }
          else {
            SecureChannel(clientChannel, SSL.createSSLEngine(ssl), nodes, maxRequestSize)
          }
          try {
            val start = System.nanoTime()
            channel.start(start + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis),
                          start + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis))
            while (true) {
              try {
                val now = System.nanoTime()
                if (!requestHandler.handle(channel, clientAddress,
                                           now + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis),
                                           now + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis),
                                           maxHeaderSize, channel.buffer())) {
                  break
                }
              }
              finally {
                channel.next()
              }
            }
            val stop = System.nanoTime()
            channel.stop(stop + TimeUnit.MILLISECONDS.toNanos(readTimeoutMillis),
                         stop + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMillis))
          }
          catch (ignore: IOException) {
            ignore.printStackTrace()
            println("Connection closed prematurely")
          }
          catch (ignored: InterruptedByTimeoutException) {
            println("Timeout")
          }
          finally {
            channel.recycle()
            launch(dispatcher) {
              try {
                clientChannel.close()
              }
              catch (ignore: IOException) {}
            }
          }
        }
      }
      catch (e: InterruptedException) {
        pool.shutdownNow()
        while (!pool.awaitTermination(1000, TimeUnit.MILLISECONDS)) {}
        break
      }
    }
    println("Stopped listening on ${address.hostName}:${address.port}")
  }

  companion object {
    fun openChannel(address: InetSocketAddress): AsynchronousServerSocketChannel {
      val serverChannel = AsynchronousServerSocketChannel.open()
      serverChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true)
      serverChannel.bind(address)
      return serverChannel
    }
//    val counter = AtomicInteger(0)
  }

  fun stop() {
    job.cancel()
    looper.shutdownNow()
    while (!looper.awaitTermination(1000, TimeUnit.MILLISECONDS)) {}
  }

}

fun main(args: Array<String>) {
  val server = Config().
    readTimeoutMillis(TimeUnit.SECONDS.toMillis(300)).
    writeTimeoutMillis(TimeUnit.SECONDS.toMillis(300)).
    certificate(java.io.File("localhost.p12")).port(8181).
    startServer()
  //Thread.sleep(15000L)
  //server.stop()
}
