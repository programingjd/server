package info.jdavid.server

import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress


class Config {

  private var port = 8080  // 80
  private var hostname: String? = null
  private var cert: () -> ByteArray? = { null }
  private var readTimeoutMillis: Long = 30000L
  private var writeTimeoutMillis: Long = 30000L
  private var maxHeaderSize: Int = 8192
  private var maxRequestSize: Int = 65536
  private var requestHandler: RequestHandler = RequestHandler.DEFAULT

  fun port(port: Int): Config {
    if (port < 0) throw IllegalArgumentException("Invalid port number: $port")
    this.port = port
    return this
  }

  fun hostname(hostname: String): Config {
    this.hostname = hostname
    return this
  }

  fun certificate(bytes: ByteArray): Config {
    this.cert = { bytes }
    return this
  }

  fun certificate(file: File): Config {
    this.cert = { file.readBytes() }
    return this
  }

  fun readTimeoutMillis(millis: Long): Config {
    this.readTimeoutMillis = millis
    return this
  }

  fun writeTimeoutMillis(millis: Long): Config {
    this.writeTimeoutMillis = millis
    return this
  }

  fun maxHeaderSize(bytes: Int): Config {
    this.maxRequestSize = bytes
    return this
  }

  fun maxRequestSize(bytes: Int): Config {
    this.maxRequestSize = bytes
    return this
  }

  fun requestHandler(handler: RequestHandler): Config {
    this.requestHandler = handler
    return this
  }

  fun startServer(): Server {
    if (maxRequestSize < Math.max(8192, maxRequestSize)) {
      throw RuntimeException("The maximum request size is too small.")
    }
    val address = InetSocketAddress(hostname?.with { InetAddress.getByName(it) }, port)
    return Server(
      address,
      readTimeoutMillis, writeTimeoutMillis,
      maxHeaderSize, maxRequestSize,
      requestHandler,
      Runtime.getRuntime().availableProcessors() - 1,
      cert
    )
  }

  private inline fun <T, U> T?.with(t: (T) -> U): U? = if (this == null) null else t(this)

}
