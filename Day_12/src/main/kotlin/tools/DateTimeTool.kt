package tools

import client.HttpClientUtils
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DateTimeTool : MCPTool {
    override val name: String = "datetime_server"
    override val description: String = "Получает текущую дату и время в указанной временной зоне. Если временная зона не указана, используется UTC."

    private val client: HttpClient = HttpClient.newHttpClient()

    override fun execute(arguments: Map<String, Any>): String {
        return try {
            // Получаем временную зону из аргументов, по умолчанию — UTC
            val timezone = arguments["timezone"] as? String ?: "UTC"
            
            // Формируем URL с параметром
            val url = "http://localhost:8080/datetime?timezone=$timezone"
            
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build()

            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                "Результат от datetime_server: ${response.body()}"
            } else {
                "Ошибка при получении времени: код ${response.statusCode()}, тело: ${response.body()}"
            }
        } catch (e: Exception) {
            "Ошибка при запросе к серверу даты и времени: ${e.message}"
        }
    }
}