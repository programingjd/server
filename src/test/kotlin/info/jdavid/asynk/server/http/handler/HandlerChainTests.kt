package info.jdavid.asynk.server.http.handler

import info.jdavid.asynk.http.Headers
import info.jdavid.asynk.http.MediaType
import info.jdavid.asynk.http.Method
import info.jdavid.asynk.http.Uri
import info.jdavid.asynk.server.Server
import info.jdavid.asynk.server.http.route.FixedRoute
import info.jdavid.asynk.server.http.route.NoParams
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.net.URI

class HandlerChainTests {

  @Test fun testSimple() {
    val range = (1..5)
    val handlers = range.map {
      HttpHandler.of(
        NumberedRoute(it)
        ) { acceptance , _, _, _ ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        }
    }
    Server.http(
      handlers
    ).use { _ ->
      range.forEach { n ->
        val request = HttpGet().apply {
          uri = URI("http://localhost:8080/${n}")
          setHeader(Headers.CACHE_CONTROL, "no-cache")
          setHeader(Headers.PRAGMA, "no-cache")
          setHeader(Headers.CONNECTION, "close")
        }
        HttpClientBuilder.create().build().use { client ->
          client.execute(request).use {
            assertEquals(200, it.statusLine.statusCode)
            val bytes = it.entity.content.readBytes()
            assertEquals(it.getLastHeader(
              Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
            assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(MediaType.TEXT))
            assertEquals(n.toString(), String(bytes))
          }
        }
      }
    }
  }

  @Test fun testBuilder() {
    Server.http(
      HttpHandler.Builder().
        route(NumberedRoute(0)).to { acceptance , _, _, _ ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        }.
        route(NumberedRoute(2)).to { acceptance , _, _, _ ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        }.
        route(NumberedRoute(3)).to { acceptance , _, _, _ ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        }.
        build(),
      HttpHandler.of(
        NumberedRoute(5)
      ) { ->
          HttpHandler.StringResponse(acceptance.routeParams.toString(), MediaType.TEXT)
        }
    ).use {
      listOf(0, 2, 3, 5).forEach { n ->
        val request = HttpGet().apply {
          uri = URI("http://localhost:8080/${n}")
          setHeader(Headers.CACHE_CONTROL, "no-cache")
          setHeader(Headers.PRAGMA, "no-cache")
          setHeader(Headers.CONNECTION, "close")
        }
        HttpClientBuilder.create().build().use { client ->
          client.execute(request).use {
            assertEquals(200, it.statusLine.statusCode)
            val bytes = it.entity.content.readBytes()
            assertEquals(it.getLastHeader(Headers.CONTENT_LENGTH).value.toInt(), bytes.size)
            assertTrue(it.getLastHeader(Headers.CONTENT_TYPE).value.startsWith(MediaType.TEXT))
            assertEquals(n.toString(), String(bytes))
          }
        }
      }
      listOf("1", "4", "6", "test", "2/3").forEach { path ->
        val request = HttpGet().apply {
          uri = URI("http://localhost:8080/${path}")
          setHeader(Headers.CACHE_CONTROL, "no-cache")
          setHeader(Headers.PRAGMA, "no-cache")
          setHeader(Headers.CONNECTION, "close")
        }
        HttpClientBuilder.create().build().use { client ->
          client.execute(request).use {
            assertEquals(404, it.statusLine.statusCode, path)
          }
        }
      }
    }
  }

  @Test fun testMaxRequestSize() {
    Server.http(
      HttpHandler.Builder().
        route(FixedRoute("/r1", 1024, listOf(Method.POST))).to { _, _, _, _ ->
          HttpHandler.EmptyResponse()
        }.
        route(FixedRoute("/r2", 8192, listOf(Method.POST))).to { _, _, _, _ ->
          HttpHandler.EmptyResponse()
        }.
        build()
    ).use {
      val bytes = ByteArray(6000) { it.toByte() }
      val request1 = HttpPost().apply {
        uri = URI("http://localhost:8080/r1")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.CONNECTION, "close")
        entity = ByteArrayEntity(bytes)
      }
      val request2 = HttpPost().apply {
        uri = URI("http://localhost:8080/r2")
        setHeader(Headers.CACHE_CONTROL, "no-cache")
        setHeader(Headers.PRAGMA, "no-cache")
        setHeader(Headers.CONNECTION, "close")
        entity = ByteArrayEntity(bytes)
      }
      HttpClientBuilder.create().build().use { client ->
        client.execute(request1).use {
          assertEquals(413, it.statusLine.statusCode, "/r1")
        }
        client.execute(request2).use {
          assertEquals(204, it.statusLine.statusCode, "/r2")
        }
      }
    }
  }

  private class NumberedRoute(val number: Int): HttpHandler.Route<Int> {
    override val maxRequestSize = 4096
    override fun match(method: Method, uri: String): Int? {
      return if (Uri.path(uri) == "/${number}") number else null
    }
  }

}
