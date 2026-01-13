import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import HttpClientUtils

open class GigaChatClient(private val oauthClient: OAuthTokenClient) {

    private val client: HttpClient = if (oauthClient is OAuthTokenClient && oauthClient::class.java.declaredFields.any { it.name == "certPath" }) {
        val certPathField = OAuthTokenClient::class.java.getDeclaredField("certPath")
        certPathField.isAccessible = true
        val certPath = certPathField.get(oauthClient) as String?
        if (certPath != null) {
            HttpClientUtils.createHttpClientWithCustomTrust(certPath, Duration.ofSeconds(30))
        } else {
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build()
        }
    } else {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

    // createHttpClientWithCustomTrust теперь находится в HttpClientUtils

    private val apiUrl = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions"

    fun sendPrompt(prompt: String): String? {
        val accessToken = oauthClient.getAccessToken() ?: run {
            println("Не удалось получить access token")
            return null
        }

        val jsonBody = """{
            "model": "GigaChat",
            "messages": [
                {
                    "role": "user",
                    "content": "$prompt"
                }
            ],
            "temperature": 0.7,
            "top_p": 0.9,
            "n": 1,
            "stream": false,
            "max_tokens": 512
        }""".trimIndent()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        return try {
            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                parseResponseContent(response.body())
            } else {
                println("Ошибка API: ${response.statusCode()}")
                println("Тело ответа: ${response.body()}")
                null
            }
        } catch (e: Exception) {
            println("Ошибка при вызове GigaChat API: ${e.message}")
            null
        }
    }

    protected fun parseResponseContent(responseBody: String): String? {
        // Ищем содержимое в поле "content" в JSON-ответе
        val contentRegex = Regex("""\"content\"\s*:\s*\"([^"]*)\"""")
        return contentRegex.find(responseBody)?.groupValues?.get(1)
    }
}