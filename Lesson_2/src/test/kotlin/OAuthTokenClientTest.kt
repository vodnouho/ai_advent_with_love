import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

import HttpClientUtils

class OAuthTokenClientTest {

    @Test
    fun `parseTokenResponse successfully extracts token and expiry time`() {
        val client = OAuthTokenClient("test-client-id", "test-client-secret")
        
        val jsonResponse = "{\"access_token\": \"test-jwt-token-123\", \"expires_at\": 1700000000000, \"token_type\": \"Bearer\"}"
        
        // Используем рефлексию для вызова приватного метода parseTokenResponse
        val parseMethod = OAuthTokenClient::class.java.getDeclaredMethod("parseTokenResponse", String::class.java)
        parseMethod.isAccessible = true
        parseMethod.invoke(client, jsonResponse)

        // Проверяем, что токен и время истечения правильно установлены
        val tokenField = OAuthTokenClient::class.java.getDeclaredField("currentToken")
        tokenField.isAccessible = true
        val expiryField = OAuthTokenClient::class.java.getDeclaredField("tokenExpiryTime")
        expiryField.isAccessible = true

        assertEquals("test-jwt-token-123", tokenField.get(client))
        assertEquals(1700000000000L, expiryField.get(client)) // expires_at в миллисекундах
    }

    @Test
    fun `parseTokenResponse handles missing token in response`() {
        val client = OAuthTokenClient("test-client-id", "test-client-secret")
        
        val jsonResponse = "{\"expires_at\": 1700000000000}"
        
        // Используем рефлексию для вызова приватного метода parseTokenResponse
        val parseMethod = OAuthTokenClient::class.java.getDeclaredMethod("parseTokenResponse", String::class.java)
        parseMethod.isAccessible = true
        parseMethod.invoke(client, jsonResponse)

        // Проверяем, что токен остался null
        val tokenField = OAuthTokenClient::class.java.getDeclaredField("currentToken")
        tokenField.isAccessible = true
        
        assertNull(tokenField.get(client))
    }

    @Test
    fun `parseTokenResponse handles missing expiry_time in response`() {
        val client = OAuthTokenClient("test-client-id", "test-client-secret")
        
        val jsonResponse = "{\"access_token\": \"test-jwt-token-123\"}"
        
        // Используем рефлексию для вызова приватного метода parseTokenResponse
        val parseMethod = OAuthTokenClient::class.java.getDeclaredMethod("parseTokenResponse", String::class.java)
        parseMethod.isAccessible = true
        parseMethod.invoke(client, jsonResponse)

        // Проверяем, что время истечения осталось 0
        val expiryField = OAuthTokenClient::class.java.getDeclaredField("tokenExpiryTime")
        expiryField.isAccessible = true
        
        assertEquals(0, expiryField.get(client))
    }

    @Test
    fun `parseTokenResponse handles empty json response`() {
        val client = OAuthTokenClient("test-client-id", "test-client-secret")
        
        val jsonResponse = ""
        
        // Используем рефлексию для вызова приватного метода parseTokenResponse
        val parseMethod = OAuthTokenClient::class.java.getDeclaredMethod("parseTokenResponse", String::class.java)
        parseMethod.isAccessible = true
        parseMethod.invoke(client, jsonResponse)

        // Проверяем, что токен и время истечения остались по умолчанию
        val tokenField = OAuthTokenClient::class.java.getDeclaredField("currentToken")
        tokenField.isAccessible = true
        val expiryField = OAuthTokenClient::class.java.getDeclaredField("tokenExpiryTime")
        expiryField.isAccessible = true

        assertNull(tokenField.get(client))
        assertEquals(0, expiryField.get(client))
    }
}