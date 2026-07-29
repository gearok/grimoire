package dev.shph.grimoire

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals

class HtmxVaryHeaderInterceptorTest {
    private val interceptor = HtmxVaryHeaderInterceptor()

    @Test
    fun `existing vary values are preserved`() {
        val response = MockHttpServletResponse()
        response.addHeader(HttpHeaders.VARY, "Accept-Encoding")

        interceptor.preHandle(MockHttpServletRequest(), response, Any())

        assertEquals(listOf("Accept-Encoding", "HX-Request"), response.getHeaders(HttpHeaders.VARY))
    }

    @Test
    fun `existing htmx vary value is not duplicated`() {
        val response = MockHttpServletResponse()
        response.addHeader(HttpHeaders.VARY, "Accept-Encoding, hx-request")

        interceptor.preHandle(MockHttpServletRequest(), response, Any())

        assertEquals(listOf("Accept-Encoding, hx-request"), response.getHeaders(HttpHeaders.VARY))
    }
}
