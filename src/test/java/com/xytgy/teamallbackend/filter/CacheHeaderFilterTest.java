package com.xytgy.teamallbackend.filter;

import com.xytgy.teamallbackend.properties.BrowserCacheProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheHeaderFilterTest {

    @Test
    void dynamicApiDoesNotGenerateFakeLastModified() throws Exception {
        BrowserCacheProperties properties = new BrowserCacheProperties();
        properties.getRules().put("/api/product", "public, max-age=300");
        CacheHeaderFilter filter = new CacheHeaderFilter(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/product/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (req, res) -> res.getWriter().write("{\"id\":1}"));

        assertNull(response.getHeader(HttpHeaders.LAST_MODIFIED));
        assertNotNull(response.getHeader(HttpHeaders.ETAG));
    }

    @Test
    void aspectHandledResponseHeadersAreNotOverwritten() throws Exception {
        BrowserCacheProperties properties = new BrowserCacheProperties();
        properties.getRules().put("/api/product", "public, max-age=60");
        CacheHeaderFilter filter = new CacheHeaderFilter(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/product/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute("BROWSER_CACHE_ASPECT_HANDLED", true);
            HttpServletResponse httpResponse = (HttpServletResponse) res;
            httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=300");
            httpResponse.setHeader(HttpHeaders.LAST_MODIFIED, "Sat, 13 Jun 2026 03:00:00 GMT");
            res.getWriter().write("{\"id\":1}");
        });

        assertEquals("public, max-age=300", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("Sat, 13 Jun 2026 03:00:00 GMT",
                response.getHeader(HttpHeaders.LAST_MODIFIED));
    }
}
