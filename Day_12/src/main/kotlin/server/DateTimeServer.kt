package server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.BufferedReader
import java.io.InputStreamReader

class DateTimeServer(val port: Int = 8080) {
    private var server: HttpServer? = null

    fun start() {
        try {
            server = HttpServer.create(InetSocketAddress(port), 0)
            server?.executor = Executors.newFixedThreadPool(10)
            server?.createContext("/datetime", DateTimeHandler())
            server?.createContext("/tools/list", ToolsListHandler()) // Список доступных инструментов
            server?.createContext("/tools/call", ToolsCallHandler()) // Выполнение инструментов
            // Обновляем сообщение о запуске сервера
            println("MCP-сервер запущен на порту $port")
            println("Доступно:")
            println("  - http://localhost:$port/datetime - текущее время")
            println("  - http://localhost:$port/tools/list - список инструментов")
            println("  - http://localhost:$port/tools/call - выполнение инструментов")
            server?.start()
            println("MCP-сервер запущен на порту $port")
            println("Доступно: http://localhost:$port/datetime")
            println("Метаданные инструментов доступны: http://localhost:$port/tools")
        } catch (e: IOException) {
            System.err.println("Ошибка при запуске сервера: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
        println("MCP-сервер остановлен")
    }
}

class ToolsListHandler : HttpHandler {
    private val objectMapper = jacksonObjectMapper()

    override fun handle(exchange: HttpExchange) {
        if (exchange.requestMethod == "GET") {
            val tools = listOf(
                mapOf(
                    "name" to "get_current_datetime",
                    "description" to "Получает текущую дату и время в указанной временной зоне. Если временная зона не указана, используется UTC.",
                    "inputSchema" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "timezone" to mapOf(
                                "type" to "string",
                                "description" to "Временная зона в формате IANA (например, \"Europe/Moscow\", \"America/New_York\"). По умолчанию используется UTC."
                            )
                        ),
                        "required" to emptyList<String>()
                    )
                )
            )
            sendResponse(exchange, objectMapper.writeValueAsString(tools))
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

class DateTimeHandler : HttpHandler {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    private val objectMapper = jacksonObjectMapper()

    override fun handle(exchange: HttpExchange) {
        if (exchange.requestMethod == "GET") {
            val currentDatetime = ZonedDateTime.now(ZoneOffset.UTC)
            val currentDatetimeStr = currentDatetime.format(formatter)
            val timestamp = currentDatetime.toInstant().toEpochMilli()

            val response = mapOf(
                "current_datetime" to currentDatetimeStr,
                "timezone" to "UTC",
                "timestamp" to timestamp
            )
            sendResponse(exchange, objectMapper.writeValueAsString(response))
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

fun main() {
    val server = DateTimeServer(8080)
    server.start()

    // Ожидание завершения (сервер работает в фоне)
    println("Нажмите Enter для остановки сервера...")
    readLine()
    server.stop()
}