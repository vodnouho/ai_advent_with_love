import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.nio.file.Files
import java.nio.file.Paths
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.KeyManager

open class GigaChatClient(private val oauthClient: OAuthTokenClient) {

    private val client: HttpClient = if (oauthClient is OAuthTokenClient && oauthClient::class.java.declaredFields.any { it.name == "certPath" }) {
        val certPathField = OAuthTokenClient::class.java.getDeclaredField("certPath")
        certPathField.isAccessible = true
        val certPath = certPathField.get(oauthClient) as String?
        if (certPath != null) {
            createHttpClientWithCustomTrust(certPath)
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
            .connectTimeout(Duration.ofSeconds(30))
            .build()
    }

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