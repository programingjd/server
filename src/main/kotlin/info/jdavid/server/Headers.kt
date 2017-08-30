package info.jdavid.server


class Headers(internal val lines: MutableList<String> = ArrayList(16)) {

  internal fun add(line: String) = lines.add(line)

  fun add(name: String, value: String) = lines.add("${name}: ${value}")

  fun value(name: String): String? {
    val lower = name.toLowerCase()
    return lines.findLast { matches(it, lower) }?.substring(name.length + 1)?.trim()
  }

  fun values(name: String): List<String> {
    val lower = name.toLowerCase()
    return lines.filter { matches(it, lower) }.map { it.substring(name.length + 1).trim() }
  }

  fun keys(): List<String> {
    val set = HashSet<String>(lines.size)
    val list = ArrayList<String>(lines.size)
    lines.forEach {
      val key = it.substring(0, it.indexOf(':'))
      if (set.add(key.toLowerCase())) list.add(key)
    }
    return list
  }

  fun has(name: String): Boolean {
    val lower = name.toLowerCase()
    return lines.find { matches(it, lower) } != null
  }

  companion object {
    val CONTENT_LENGTH = "Content-Length"
    val TRANSFER_ENCODING = "Transfer-Encoding"
    val CONTENT_TYPE = "Content-Type"
    val EXPECT = "Expect"
    val CONNECTION = "Connection"
    val UPGRADE = "Upgrade"
    val HTTP2_SETTINGS = "HTTP2-Settings"

    private fun matches(line: String, lowercaseName: String): Boolean {
      return line.length > lowercaseName.length + 1 &&
        line.substring(0, lowercaseName.length).toLowerCase() == lowercaseName &&
        line[lowercaseName.length] == ':'
    }
  }

}
