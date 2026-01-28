import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

import client.HttpClientUtils

import client.GigaChatClient
import client.OAuthTokenClient

class GigaChatClientTest {

    @Test
    fun `parseResponseContent successfully extracts content from valid response`() {
        val client = GigaChatClientStub(object : OAuthTokenClient("", "") {
        override fun getAccessToken(): String? = "test-token"
    })
        
        val jsonResponse = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Привет! Как я могу помочь вам сегодня?\"}}]}"
        
        val result = client.parseResponseContentForTest(jsonResponse)
        
        assertEquals("Привет! Как я могу помочь вам сегодня?", result)
    }

    @Test
    fun `parseResponseContent returns null for response without content field`() {
        val client = GigaChatClientStub(object : OAuthTokenClient("", "") {
        override fun getAccessToken(): String? = "test-token"
    })
        
        val jsonResponse = "{\"choices\":[{\"message\":{\"role\":\"assistant\"}}]}"
        
        val result = client.parseResponseContentForTest(jsonResponse)
        
        assertNull(result)
    }

    @Test
    fun `parseResponseContent returns null for empty response`() {
        val client = GigaChatClientStub(object : OAuthTokenClient("", "") {
        override fun getAccessToken(): String? = "test-token"
    })
        
        val jsonResponse = ""
        
        val result = client.parseResponseContentForTest(jsonResponse)
        
        assertNull(result)
    }

    @Test
    fun `parseResponseContent handles content with special characters`() {
        val client = GigaChatClientStub(object : OAuthTokenClient("", "") {
        override fun getAccessToken(): String? = "test-token"
    })
        
        val jsonResponse = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Привет, \u0041\u0042\u0043! 123 + 456 = 579\nНовая строка здесь\"}}]}"
        
        val result = client.parseResponseContentForTest(jsonResponse)
        
        assertEquals("Привет, ABC! 123 + 456 = 579\nНовая строка здесь", result)
    }
}

class GigaChatClientStub(oauthClient: OAuthTokenClient) : GigaChatClient(oauthClient) {
    // Метод-обертка для тестирования приватного метода parseResponseContent
    fun parseResponseContentForTest(responseBody: String): String? {
        return parseResponseContent(responseBody)?.content
    }
}