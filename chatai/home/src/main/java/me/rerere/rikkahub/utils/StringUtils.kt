package me.rerere.rikkahub.utils

import org.apache.commons.text.StringEscapeUtils
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun String.urlEncode(): String {
  return URLEncoder.encode(this, "UTF-8")
}

fun String.urlDecode(): String {
  return URLDecoder.decode(this, "UTF-8")
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Encode(): String {
  return Base64.encode(this.toByteArray())
}

@OptIn(ExperimentalEncodingApi::class)
fun String.base64Decode(): String {
  return String(Base64.decode(this))
}

fun String.escapeHtml(): String {
  return StringEscapeUtils.escapeHtml4(this)
}

fun String.unescapeHtml(): String {
  return StringEscapeUtils.unescapeHtml4(this)
}

fun Number.toFixed(digits: Int = 0) = "%.${digits}f".format(this)

fun String.applyPlaceholders(
  vararg placeholders: Pair<String, String>,
): String {
  var result = this
  for ((placeholder, replacement) in placeholders) {
    result = result.replace("{$placeholder}", replacement)
  }
  return result
}

fun Long.fileSizeToString(): String {
  return when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "${this / 1024} KB"
    this < 1024 * 1024 * 1024 -> "${this / (1024 * 1024)} MB"
    else -> "${this / (1024 * 1024 * 1024)} GB"
  }
}