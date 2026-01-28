package client

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class Orchestrator {
    private val clientId = System.getenv("GIGACHAT_CLIENT_ID") ?: error("Переменная окружения GIGACHAT_CLIENT_ID не установлена")
    private val clientSecret = System.getenv("GIGACHAT_CLIENT_SECRET") ?: error("Переменная окружения GIGACHAT_CLIENT_SECRET не установлена")
    private val certPath = "src/main/resources/russian_trusted_root_ca.cer"

    private val oauthClient = OAuthTokenClient(
        clientId = clientId,
        clientSecret = clientSecret,
        certPath = certPath
    )
    private val gigaChatClient = GigaChatClient(oauthClient)
    
    private var temperature = 0.87
    private var totalTokensUsed = 0

    init {
        // Регистрируем MCP инструменты
        gigaChatClient.registerTool(tools.DateTimeTool())

        // Загружаем системный промпт из файла при старте, если он существует
        val systemPromptFile = File("src/main/resources/system_prompt.txt")
        if (systemPromptFile.exists()) {
            val systemPrompt = systemPromptFile.readText(Charsets.UTF_8)
            gigaChatClient.setSystemPrompt(systemPrompt)
        }
    }

    fun start() {
        showAvailableCommands()
        println("Введите запрос для GigaChat или команду:")
        var input: String
        do {
            print("> ")
            input = readLine().toString()

            when {
                input == "/exit" -> break
                input == "/help" -> showAvailableCommands()
                input.startsWith("/temp") -> {
                    val tempStr = input.substring(5).trim()
                    if (tempStr.isEmpty()) {
                        println("Текущая температура: ${"%.2f".format(temperature)}")
                    } else {
                        try {
                            val newTemp = tempStr.toDouble()
                            if (newTemp in 0.0..2.0) {
                                temperature = newTemp
                                println("Температура установлена: ${"%.2f".format(temperature)}")
                            } else {
                                println("Ошибка: значение температуры должно быть в диапазоне от 0.0 до 2.0")
                            }
                        } catch (e: NumberFormatException) {
                            println("Ошибка: некорректное значение температуры. Введите число от 0.0 до 2.0")
                        }
                    }
                }
                input.startsWith("/file") -> {
                    val fileName = input.substring(5).trim()
                    if (fileName.isEmpty()) {
                        println("Ошибка: не указано имя файла. Используйте: /file <имя_файла>")
                    } else {
                        val filePath = "src/main/resources/$fileName"
                        val response = gigaChatClient.sendPromptFromFile(filePath, temperature = temperature)
                        if (response != null) {
                            println("GigaChat: ${response.content ?: "Пустой ответ"}")
                            println("-----------------------------------------------------------------------------------------")
                            val tokensUsed = response.totalTokens
                            totalTokensUsed += tokensUsed
                            println("Токены - запрос: ${response.promptTokens}, ответ: ${response.completionTokens}, всего: ${response.totalTokens}")
                            println("Общее количество использованных токенов: $totalTokensUsed")
                            println("Размер контекста: ${response.contextSize} сообщений (${response.contextTokens} токенов)")
                        } else {
                            println("GigaChat: Ошибка при обработке файла")
                        }
                    }
                }
                input.startsWith("/system_prompt") -> {
                    val newPrompt = input.substring(14).trim()
                    if (newPrompt.isEmpty()) {
                        val currentPrompt = gigaChatClient.getSystemPrompt()
                        println("Текущий системный промпт: ${if (currentPrompt.isEmpty()) "не задан" else "\"$currentPrompt\""}")
                    } else {
                        gigaChatClient.setSystemPrompt(newPrompt)
                        println("Системный промпт обновлён")
                    }
                }
                input == "/summary" -> {
                    gigaChatClient.compressContext()
                    val contextSize = gigaChatClient.getContextSize()
                    println("Текущий размер контекста: $contextSize сообщений")
                }
                input == "/tools" -> {
                    if (gigaChatClient.toolCount() > 0) {
                        println("Доступные инструменты:")
                        gigaChatClient.listTools().forEach { tool ->
                            println("- ${tool.name}: ${tool.description}")
                        }
                    } else {
                        println("Нет зарегистрированных инструментов")
                    }
                }
                input == "/datetime" -> {
                    try {
                        val request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/datetime"))
                            .header("Accept", "application/json")
                            .GET()
                            .build()
                        
                        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
                        
                        if (response.statusCode() == 200) {
                            val jsonResponse = response.body()
                            println("Текущая дата и время: $jsonResponse")
                        } else {
                            println("Ошибка при получении даты и времени: ${response.statusCode()}")
                        }
                    } catch (e: Exception) {
                        println("Ошибка при запросе к серверу даты и времени: ${e.message}")
                    }
                }
                else -> {
                    val response = gigaChatClient.sendPrompt(input, temperature = temperature)
                    if (response != null) {
                        println("GigaChat: ${response.content ?: "Пустой ответ"}")
                        println("-----------------------------------------------------------------------------------------")
                        val tokensUsed = response.totalTokens
                        totalTokensUsed += tokensUsed
                        println("Токены - запрос: ${response.promptTokens}, ответ: ${response.completionTokens}, всего: ${response.totalTokens}")
                        println("Общее количество использованных токенов: $totalTokensUsed")
                        println("Размер контекста: ${response.contextSize} сообщений (${response.contextTokens} токенов)")
                    } else {
                        println("GigaChat: Ошибка ответа")
                    }
                    if (gigaChatClient.isContextFull())
                        gigaChatClient.compressContext()
                }
            }
        } while (true)
        println("Завершение работы.")
    }

    private fun showAvailableCommands() {
        println("Доступные команды:")
        println("  /exit - выход из программы")
        println("  /temp <значение> - установка температуры (0.0-2.0)")
        println("  /file <имя_файла> - отправка содержимого файла из src/main/resources")
        println("  /system_prompt <текст> - установка системного промпта")
        println("  /summary - создание резюме истории диалога")
        println("  /datetime - получение текущей даты и времени")
        println("  /tools - показать доступные инструменты")
        println("  /help - показать доступные команды")
    }
}