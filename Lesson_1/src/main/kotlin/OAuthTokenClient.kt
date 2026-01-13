import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.KeyManager

open class OAuthTokenClient(
    private val clientId: String,
    private val clientSecret: String,
    private val tokenUrl: String = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
    private val certPath: String? = null // Путь к сертификату Минцифры (опционально)
) {
    private val client: HttpClient = if (certPath != null) {
        createHttpClientWithCustomTrust(certPath)
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

    private fun createHttpClientWithCustomTrust(certPath: String): HttpClient {
        // Чтение сертификата из файла
        val certBytes = Files.readAllBytes(Paths.get(certPath))
        val certFactory = CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(certBytes.inputStream()) as X509Certificate

        // Создание TrustManager, доверяющего указанному сертификату
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers() = arrayOf(cert)
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                // Для клиентских сертификатов также проверяем наличие нашего доверенного сертификата
                for (certificate in chain) {
                    if (certificate.subjectDN == cert.subjectDN) {
                        return // Наш сертификат найден в цепочке - доверяем
                    }
                }
                throw java.security.cert.CertificateException("Клиентский сертификат не содержит доверенный корневой сертификат")
            }
            
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                // В цепочке сертификатов сервера должен быть наш доверенный корневой сертификат
                for (certificate in chain) {
                    if (certificate.subjectDN == cert.subjectDN) {
                        return // Наш сертификат найден в цепочке - доверяем
                    }
                }
                // Если наш сертификат не найден в цепочке
                throw java.security.cert.CertificateException("Сертификат сервера не содержит доверенный корневой сертификат")
            }
        }

        // Настройка SSLContext
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(arrayOf<KeyManager>() as Array<KeyManager>?, arrayOf(trustManager), null)

        return HttpClient.newBuilder()
            .sslContext(sslContext)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build()
    }
}