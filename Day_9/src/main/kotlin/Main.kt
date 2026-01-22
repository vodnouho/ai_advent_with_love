fun main() {
    // Загрузка clientId и clientSecret из переменных окружения
    val clientId = System.getenv("GIGACHAT_CLIENT_ID") ?: error("Переменная окружения GIGACHAT_CLIENT_ID не установлена")
    val clientSecret = System.getenv("GIGACHAT_CLIENT_SECRET") ?: error("Переменная окружения GIGACHAT_CLIENT_SECRET не установлена")
    val certPath = "src/main/resources/russian_trusted_root_ca.cer"

    val oauthClient = OAuthTokenClient(
        clientId = clientId,
        clientSecret = clientSecret,
        certPath = certPath
    )
    val gigaChatClient = GigaChatClient(oauthClient)

    // Загружаем системный промпт из файла при старте, если он существует
    val systemPromptFile = java.io.File("src/main/resources/system_prompt.txt")
    if (systemPromptFile.exists()) {
        val systemPrompt = systemPromptFile.readText(Charsets.UTF_8)
        gigaChatClient.setSystemPrompt(systemPrompt)
    }

    var temperature = 0.87
    var totalTokensUsed = 0

    fun showAvailableCommands() {
        println("Доступные команды:")
        println("  /exit - выход из программы")
        println("  /temp <значение> - установка температуры (0.0-2.0)")
        println("  /file <имя_файла> - отправка содержимого файла из src/main/resources")
        println("  /system_prompt <текст> - установка системного промпта")
        println("  /summary - создание резюме истории диалога")
        println("  /help - показать доступные команды")
    }

    showAvailableCommands()
    println("Введите запрос для GigaChat или команду:")
    var input: String
    do {
        print("> ")
        input = readLine().toString()

        when {
            input.lowercase() == "exit" || input == "/exit" -> break
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
            else -> {
                // TODO: экранировать ввод кавычек пользователем
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
