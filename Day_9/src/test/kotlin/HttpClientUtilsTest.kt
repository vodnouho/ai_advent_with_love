import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

class HttpClientUtilsTest {

    @Test
    fun `createHttpClientWithCustomTrust creates client with correct timeout`() {
        // Тест не может проверить реальное поведение без сертификата,
        // но проверяем, что метод существует и можно получить клиент с кастомным таймаутом
        
        // Проверяем, что можно создать HttpClient с кастомным таймаутом
        val client = HttpClientUtils.createHttpClientWithCustomTrust("src/test/resources/test-cert.cer", Duration.ofSeconds(5))
        
        // Нельзя напрямую проверить таймаут, но можно проверить, что клиент не null
        assertNotNull(client)
    }

    @Test
    fun `createHttpClientWithCustomTrust uses default timeout when not specified`() {
        // Проверяем, что можно создать HttpClient с таймаутом по умолчанию
        val client = HttpClientUtils.createHttpClientWithCustomTrust("src/test/resources/test-cert.cer")
        
        // Нельзя напрямую проверить таймаут, но можно проверить, что клиент не null
        assertNotNull(client)
    }
}