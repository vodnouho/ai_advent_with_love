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

    println("Введите запрос для GigaChat (или 'exit'):")
    var input: String
    do {
        print("> ")
        // TODO: экранировать ввод кавычек пользователем
        input = readLine().toString()
        if (input.lowercase() != "exit") {
            val response = gigaChatClient.sendPrompt(input)
            println("GigaChat: ${response ?: "Ошибка ответа"}")
        }
    } while (input.lowercase() != "exit")
    println("Завершение работы.")
}