package tools

import client.HttpClientUtils
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DateTimeTool : MCPTool {
    override val name: String = "datetime_server"
    override val description: String = "Получает текущую дату и время в формате UTC. Используется для точного определения текущего времени."

    private val client: HttpClient = HttpClient.newHttpClient()

    override fun execute(arguments: Map<String, Any>): String {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/datetime"))
                .header("Accept", "application/json")
                .GET()
                .build()

            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                "Текущее время (UTC): ${response.body()}"
            } else {
                "Ошибка при получении времени: код ${response.statusCode()}"
            }
        } catch (e: Exception) {
            "Ошибка при запросе к серверу даты и времени: ${e.message}"
        }
    }
}