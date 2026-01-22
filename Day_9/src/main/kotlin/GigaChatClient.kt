import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

import HttpClientUtils.createHttpClientWithCustomTrust

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

data class Message(
    val role: String,
    val content: String,
    val tokens: Int = 0
)

data class GigaChatResponse(
    val content: String?,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val contextSize: Int = 0,
    val contextTokens: Int = 0
)



open class GigaChatClient(private val oauthClient: OAuthTokenClient) {
    private val messages = mutableListOf<Message>()

    fun getContextSize(): Int = messages.size
    private val maxMessages = 10
    private var systemPrompt = ""

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

    fun setSystemPrompt(prompt: String) {
        systemPrompt = prompt
        // Обновляем системное сообщение в истории, если оно уже есть
        if (messages.isNotEmpty() && messages[0].role == "system") {
            messages[0] = Message("system", prompt)
        }
    }

    fun getSystemPrompt(): String = systemPrompt

    fun sendPrompt(prompt: String, temperature: Double = 0.87): GigaChatResponse? {
        // Добавляем пользовательское сообщение в историю
        messages.add(Message("user", prompt))
        

        
        // Формируем полный список сообщений для отправки
        val messagesToSend = mutableListOf<Message>()
        
        // Добавляем системный промпт, если он задан
        if (systemPrompt.isNotBlank()) {
            if (messages.isEmpty() || messages[0].role != "system") {
                messagesToSend.add(Message("system", systemPrompt))
            }
        }
        
        // Добавляем историю сообщений
        messagesToSend.addAll(messages)
        

        val accessToken = oauthClient.getAccessToken() ?: run {
            println("Не удалось получить access token")
            return null
        }
        // Преобразуем сообщения в JSON строку
        val messagesJson = messagesToSend.joinToString(",\n") { msg ->
            "            {\n                \"role\": \"${msg.role}\",\n                \"content\": \"${escapeJsonString(msg.content)}\"\n            }"
        }
        
        val jsonBody = """{
            "model": "GigaChat",
            "messages": [
$messagesJson
            ],
            "temperature": $temperature,
            "top_p": 0.9,
            "n": 1,
            "stream": false,
            "max_tokens": 1024
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
            
            sendPrompt(escapedContent, temperature)
        } catch (e: java.io.IOException) {
            println("Ошибка при чтении файла $filePath: ${e.message}")
            null
        } catch (e: Exception) {
            println("Неожиданная ошибка при обработке файла $filePath: ${e.message}")
            null
        }
    }
    
    private var totalContextTokens = 0

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
            
            // Добавляем ответ в историю
            if (content != null) {
                messages.add(Message("assistant", content, totalTokens))
            }
            
            // Вычисляем размер контекста (количество сообщений)
            val contextSize = messages.size
            
            // Обновляем общее количество токенов в контексте
            totalContextTokens = messages.sumOf { it.tokens }
            
            return GigaChatResponse(content, promptTokens, completionTokens, totalTokens, contextSize, totalContextTokens)
        } catch (e: Exception) {
            println("Ошибка при парсинге JSON: ${e.message}")
            return null
        }
    }



    fun compressContext() {
        if (messages.size <= 2) return // Нечего сжимать
        
        // Сохраняем системное сообщение (если есть)
        val systemMessage = messages.getOrNull(0)?.takeIf { it.role == "system" }
        val lastMessages = messages.takeLast(2)
        
        // Формируем полный контекст для summarization
        val contextToSummarize = messages.filter { it.role != "system" }.joinToString("\n") { "${it.role}: ${it.content}" }
        
        // Создаем промпт для summarization
        val summaryPrompt = "Сделай краткое резюме следующего диалога, сохранив основную суть и ключевые моменты. " +
                "Представь результат в виде 2-3 предложений:\n\n$contextToSummarize"
        
        // Отправляем запрос на summarization
        val summaryResponse = sendPrompt(summaryPrompt, 0.5)
        
        // Очищаем историю
        messages.clear()
        
        // Восстанавливаем системное сообщение
        if (systemMessage != null) {
            messages.add(systemMessage)
        }
        
        // Добавляем summary как системное сообщение
        if (summaryResponse?.content != null) {
            messages.add(Message("assistant", "Контекст диалога: ${summaryResponse.content}"))
            println("\nСоздано резюме диалога: ${summaryResponse.content}")
        }
        
        // Восстанавливаем последние 2 сообщения
        messages.addAll(lastMessages)
    }

    fun isContextFull(): Boolean = messages.size >= maxMessages
}
