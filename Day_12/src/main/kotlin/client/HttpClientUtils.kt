package client

import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Paths
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.KeyManager
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object HttpClientUtils {
    /**
     * Создает HttpClient с кастомным TrustManager, доверяющим указанному сертификату.
     * Используется для безопасного соединения с серверами Сбера, требующими доверия к сертификату Минцифры.
     *
     * @param certPath путь к файлу сертификата (.cer, .crt)
     * @param connectTimeout таймаут подключения (по умолчанию 30 секунд)
     * @return настроенный экземпляр HttpClient
     */
    fun createHttpClientWithCustomTrust(certPath: String, connectTimeout: Duration = Duration.ofSeconds(30)): HttpClient {
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
            .connectTimeout(connectTimeout)
            .build()
    }
}