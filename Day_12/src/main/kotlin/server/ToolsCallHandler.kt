package server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class ToolsCallHandler : HttpHandler {
    private val objectMapper = jacksonObjectMapper()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    override fun handle(exchange: HttpExchange) {
        if (exchange.requestMethod == "POST") {
            try {
                // Читаем тело запроса
                val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
                val request = objectMapper.readTree(requestBody)
                
                val toolName = request["name"]?.asText()
                
                when (toolName) {
                    "get_current_datetime" -> {
                        val timezoneParam = request["arguments"]?.get("timezone")?.asText()
                        val zone = if (timezoneParam != null && timezoneParam.isNotEmpty()) {
                            java.time.ZoneId.of(timezoneParam)
                        } else {
                            ZoneOffset.UTC
                        }
                        
                        val currentDatetime = ZonedDateTime.now(zone)
                        val currentDatetimeStr = currentDatetime.format(formatter)
                        val timestamp = currentDatetime.toInstant().toEpochMilli()

                        val response = mapOf(
                            "name" to "get_current_datetime",
                            "result" to mapOf(
                                "current_datetime" to currentDatetimeStr,
                                "timezone" to zone.toString(),
                                "timestamp" to timestamp
                            )
                        )
                        
                        sendResponse(exchange, objectMapper.writeValueAsString(response))
                    }
                    else -> {
                        sendResponse(exchange, "{\"error\": \"Инструмент не найден\"}", 404)
                    }
                }
            } catch (e: Exception) {
                sendResponse(exchange, "{\"error\": \"Ошибка при выполнении инструмента: ${e.message}\"}", 500)
            }
        } else {
            sendResponse(exchange, "{\"error\": \"Метод не поддерживается\"}", 405)
        }
    }
    
    private fun sendResponse(exchange: HttpExchange, response: String, statusCode: Int = 200) {
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        val bytes = response.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        val os: OutputStream = exchange.responseBody
        os.write(bytes)
        os.close()
    }
}