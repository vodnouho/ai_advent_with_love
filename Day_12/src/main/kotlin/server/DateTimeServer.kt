package server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.util.concurrent.Executors

class DateTimeServer(val port: Int = 8080) {
    private var server: HttpServer? = null

    fun start() {
        try {
            server = HttpServer.create(InetSocketAddress(port), 0)
            server?.createContext("/datetime", DateTimeHandler())
            server?.createContext("/tools", ToolsHandler()) // Добавляем обработчик для /tools
            server?.executor = Executors.newFixedThreadPool(10)
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

class ToolsHandler : HttpHandler {
    private val toolDescription = "{\n" +
            "  \"name\": \"datetime_server\",\n" +
            "  \"description\": \"Получает текущую дату и время в указанной временной зоне. Если временная зона не указана, используется UTC.\",\n" +
            "  \"parameters\": {\n" +
            "    \"type\": \"object\",\n" +
            "    \"properties\": {\n" +
            "      \"timezone\": {\n" +
            "        \"type\": \"string\",\n" +
            "        \"description\": \"Временная зона (например, \"\\\"Europe/Moscow\\\"\"), по умолчанию — UTC\"\n" +
            "      }\n" +
            "    },\n" +
            "    \"required\": []\n" +
            "  }\n" +
            "}"

    override fun handle(exchange: HttpExchange) {
        if (exchange.requestMethod == "GET") {
            val jsonResponse = "{\n" +
                    "  \"tools\": [\n" +
                    "    $toolDescription\n" +
                    "  ]\n" +
                    "}"
            sendResponse(exchange, jsonResponse)
        } else {
            sendResponse(exchange, "{\"error\": \"Метод не поддерживается\"}", 405)
        }
    }
    
    private fun sendResponse(exchange: HttpExchange, response: String, statusCode: Int = 200) {
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(statusCode, response.length.toLong())
        val os: OutputStream = exchange.responseBody
        os.write(response.toByteArray())
        os.close()
    }
}

class DateTimeHandler : HttpHandler {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    private val epochFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

    override fun handle(exchange: HttpExchange) {
        if (exchange.requestMethod == "GET") {
            val startTime = System.currentTimeMillis()
            val currentDatetime = ZonedDateTime.now(ZoneOffset.UTC)
            val currentDatetimeStr = currentDatetime.format(formatter)
            val timestamp = currentDatetime.toInstant().toEpochMilli()

            // Формируем JSON-ответ в формате MCP
            val jsonResponse = "{\n" +
                    "  \"tool\": \"datetime_server\",\n" +
                    "  \"version\": \"1.0\",\n" +
                    "  \"data\": {\n" +
                    "    \"current_datetime\": \"$currentDatetimeStr\",\n" +
                    "    \"timezone\": \"UTC\",\n" +
                    "    \"timestamp\": $timestamp\n" +
                    "  },\n" +
                    "  \"metadata\": {\n" +
                    "    \"server_time\": \"$currentDatetimeStr\",\n" +
                    "    \"response_time_ms\": ${System.currentTimeMillis() - startTime}\n" +
                    "  }\n" +
                    "}"

            sendResponse(exchange, jsonResponse)
        } else {
            sendResponse(exchange, "{\"error\": \"Метод не поддерживается\"}", 405)
        }
    }

    private fun sendResponse(exchange: HttpExchange, response: String, statusCode: Int = 200) {
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(statusCode, response.length.toLong())
        val os: OutputStream = exchange.responseBody
        os.write(response.toByteArray())
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