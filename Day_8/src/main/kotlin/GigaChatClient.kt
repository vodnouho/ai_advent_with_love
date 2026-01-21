import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import HttpClientUtils.createHttpClientWithCustomTrust

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

data class GigaChatResponse(
    val content: String?,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

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

    fun sendPrompt(prompt: String, systemPrompt: String = "", temperature: Double = 0.87): GigaChatResponse? {

        val accessToken = oauthClient.getAccessToken() ?: run {
            println("Не удалось получить access token")
            return null
        }
        val escapedContent = escapeJsonString(prompt)
        val systemPromptContent = ""   // (javaClass.classLoader.getResource("system_prompt.txt")?.readText() ?: "").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
        val jsonBody = """{
            "model": "GigaChat",
            "messages": [
                {
                    "role": "system",
                    "content": "$systemPromptContent"
                },
                {
                    "role": "user",
                    "content": "$escapedContent"
                }
            ],
            "temperature": $temperature,
            "top_p": 0.9,
            "n": 1,
            "stream": false,
            "max_tokens": 10240
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



    private fun escapeJsonString(input: String): String {
        return input
            .replace("\\", "\\\\") // Экранирование обратных слешей
            .replace("\"", "\\\"") // Экранирование кавычек
            .replace("\n", "\\n") // Экранирование новых строк
            .replace("\r", "\\r") // Экранирование возвратов каретки
            .replace("\t", "\\t") // Экранирование табуляций
    }

    /**
     * Отправляет запрос к модели GigaChat с текстом из файла
     *
     * @param filePath путь к файлу с текстом запроса
     * @param systemPrompt системный промпт (опционально)
     * @param temperature параметр температуры (по умолчанию 0.87)
     * @return объект GigaChatResponse с ответом и статистикой токенов, или null при ошибке
     */
    fun sendPromptFromFile(filePath: String, systemPrompt: String = "", temperature: Double = 0.87): GigaChatResponse? {
        return try {
            val file = java.io.File(filePath)
            if (!file.exists()) {
                println("Ошибка: файл не найден по пути: $filePath")
                return null
            }
            
            val prompt = file.readText(Charsets.UTF_8)
            val escapedContent = escapeJsonString(prompt)
            
            sendPrompt(escapedContent, systemPrompt, temperature)
        } catch (e: java.io.IOException) {
            println("Ошибка при чтении файла $filePath: ${e.message}")
            null
        } catch (e: Exception) {
            println("Неожиданная ошибка при обработке файла $filePath: ${e.message}")
            null
        }
    }
    
    protected fun parseResponseContent(responseBody: String): GigaChatResponse? {
        // Используем Jackson для парсинга JSON
        val mapper = ObjectMapper().registerKotlinModule()
        try {
            val rootNode = mapper.readTree(responseBody.replace("\n", "\\n"))
            
            // Извлекаем содержимое ответа
            var content: String? = null
            val choicesNode = rootNode.get("choices")
            if (choicesNode != null && choicesNode.isArray && choicesNode.size() > 0) {
                val messageNode = choicesNode.get(0).get("message")
                if (messageNode != null) {
                    val contentNode = messageNode.get("content")
                    if (contentNode != null && !contentNode.isNull) {
                        content = contentNode.asText()
                    }
                }
            }
            
            // Извлекаем информацию о токенах
            val usageNode = rootNode.get("usage")
            val promptTokens = if (usageNode != null) usageNode.get("prompt_tokens")?.asInt() ?: 0 else 0
            val completionTokens = if (usageNode != null) usageNode.get("completion_tokens")?.asInt() ?: 0 else 0
            val totalTokens = if (usageNode != null) usageNode.get("total_tokens")?.asInt() ?: 0 else 0
            
            return GigaChatResponse(content, promptTokens, completionTokens, totalTokens)
        } catch (e: Exception) {
            println("Ошибка при парсинге JSON: ${e.message}")
            return null
        }
    }
}
