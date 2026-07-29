package dev.shph.grimoire

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class HtmxVaryHeaderConfiguration : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(HtmxVaryHeaderInterceptor())
            .addPathPatterns("/spells", "/monsters")
    }
}

internal class HtmxVaryHeaderInterceptor : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val variedHeaders = response.getHeaders(HttpHeaders.VARY)
            .flatMap { it.split(',') }
            .map(String::trim)

        if (variedHeaders.none { it.equals(HTMX_REQUEST_HEADER, ignoreCase = true) }) {
            response.addHeader(HttpHeaders.VARY, HTMX_REQUEST_HEADER)
        }
        return true
    }
}

private const val HTMX_REQUEST_HEADER = "HX-Request"
