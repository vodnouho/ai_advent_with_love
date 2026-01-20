import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

import HttpClientUtils

open class OAuthTokenClient(
    private val clientId: String,
    private val clientSecret: String,
    private val tokenUrl: String = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
    private val certPath: String? = null // Путь к сертификату Минцифры (опционально)
) {
    private val client: HttpClient = if (certPath != null) {
        HttpClientUtils.createHttpClientWithCustomTrust(certPath)
    } else {
        HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build()
    }

    private var currentToken: String? = null
    private var tokenExpiryTime: Long = 0

    /**
     * Возвращает действительный access token. При необходимости обновляет его.
     */
    open fun getAccessToken(): String? {
        // Если токен есть и не истек — возвращаем его
        if (currentToken != null && Instant.now().epochSecond < tokenExpiryTime - 60) {
            return currentToken
        }

        // Иначе запрашиваем новый
        fetchNewToken()
        return currentToken
    }

    private fun fetchNewToken() {
        val authHeader = buildAuthHeader()
        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Authorization", "Basic $authHeader")
            .header("RqUID", java.util.UUID.randomUUID().toString())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("scope=GIGACHAT_API_PERS"))
            .build()

        try {
            val response: HttpResponse<String> = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                val json = response.body()
                parseTokenResponse(json)
            } else {
                println("Ошибка получения токена: ${response.statusCode()}")
                println("Тело ответа: ${response.body()}")
            }
        } catch (e: Exception) {
            println("Ошибка при запросе токена: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun buildAuthHeader(): String {
        val credentials = "$clientId:$clientSecret"
        return String(Base64.getEncoder().encode(credentials.toByteArray(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)
    }

    private fun parseTokenResponse(json: String) {
        // Упрощённый парсинг JSON
        val tokenRegex = Regex("\\\"access_token\\\":\\s*\\\"([^\\\"]+)\\\"")
        val expiresAtRegex = Regex("\\\"expires_at\\\":\\s*(\\d+)")

        tokenRegex.find(json)?.groupValues?.get(1)?.let { token ->
            currentToken = token
        }

        expiresAtRegex.find(json)?.groupValues?.get(1)?.let { expiresAt ->
            // Сохраняем время истечения в миллисекундах
            tokenExpiryTime = expiresAt.toLong()
        }
    }

    // createHttpClientWithCustomTrust теперь находится в HttpClientUtils
}