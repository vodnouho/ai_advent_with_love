import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import HttpClientUtils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

// Исправление ошибки компиляции: добавлена аннотация для подавления предупреждений о неиспользуемых импортах
@Suppress("unused")

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

    fun sendPrompt(prompt: String, systemPrompt: String = ""): String? {
        val accessToken = oauthClient.getAccessToken() ?: run {
            println("Не удалось получить access token")
            return null
        }

        val systemPromptContent = (javaClass.classLoader.getResource("system_prompt.txt")?.readText() ?: "").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        val jsonBody = """{
            "model": "GigaChat",
            "messages": [
                {
                    "role": "system",
                    "content": "$systemPromptContent"
                },
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
        // Используем Jackson для парсинга JSON
        val mapper = ObjectMapper().registerKotlinModule()
        try {
            val rootNode = mapper.readTree(responseBody.replace("\n", "\\n"))
            // Ищем содержимое в поле "content" в JSON-ответе
            val choicesNode = rootNode.get("choices")
            if (choicesNode != null && choicesNode.isArray && choicesNode.size() > 0) {
                val messageNode = choicesNode.get(0).get("message")
                if (messageNode != null) {
                    val contentNode = messageNode.get("content")
                    if (contentNode != null && !contentNode.isNull) {
                        return contentNode.asText()
                    }
                }
            }
            return null
        } catch (e: Exception) {
            println("Ошибка при парсинге JSON: ${e.message}")
            return null
        }
    }
}
