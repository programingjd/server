package info.jdavid.server.dev


sealed class Method {

  override fun toString(): String {
    return javaClass.simpleName
  }

  object OPTIONS: Method()
  object HEAD: Method()
  object GET: Method()
  object POST: Method()
  object PUT: Method()
  object DELETE: Method()
  object PATCH: Method()

  data class Custom(public val name: String): Method()

  companion object {
    internal fun from(m: String): Method {
      return when(m) {
        "OPTIONS" -> OPTIONS
        "HEAD" -> HEAD
        "GET" -> GET
        "POST" -> POST
        "PUT" -> PUT
        "DELETE" -> DELETE
        "PATCH" -> PATCH
        else -> throw RuntimeException("Unexpected HTTP method: ${m}")
      }
    }
  }

}