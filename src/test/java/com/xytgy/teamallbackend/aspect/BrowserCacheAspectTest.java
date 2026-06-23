package com.xytgy.teamallbackend.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.annotation.BrowserCache;
import com.xytgy.teamallbackend.annotation.CacheStrategy;
import com.xytgy.teamallbackend.cache.browser.LastModifiedProvider;
import com.xytgy.teamallbackend.properties.BrowserCacheProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrowserCacheAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private TestLastModifiedProvider lastModifiedProvider;

    private BrowserCacheProperties properties;
    private ObjectMapper objectMapper;
    private BrowserCacheAspect aspect;

    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        properties = new BrowserCacheProperties();
        objectMapper = new ObjectMapper();
        aspect = new BrowserCacheAspect(properties, objectMapper, applicationContext);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // --- 辅助方法 ---

    private void setupJoinPointForMethod(String methodName, Class<?>... paramTypes) throws Exception {
        Method method = TestController.class.getMethod(methodName, paramTypes);
        lenient().when(joinPoint.getSignature()).thenReturn(methodSignature);
        lenient().when(methodSignature.getMethod()).thenReturn(method);
    }

    private void setupRequestContext(MockHttpServletRequest request, MockHttpServletResponse response) {
        ServletRequestAttributes attrs = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(attrs);
    }

    private String computeEtag(Object result) throws Exception {
        String json = objectMapper.writeValueAsString(result);
        String md5 = com.xytgy.teamallbackend.utils.DigestUtils.md5Hex(json.getBytes(StandardCharsets.UTF_8));
        return "\"" + md5 + "\"";
    }

    // --- 测试用 Controller 桩 ---

    public static class TestController {
        @BrowserCache(strategy = CacheStrategy.PRODUCT_LIST)
        public String productList() { return "product-list"; }

        @BrowserCache(strategy = CacheStrategy.STATIC, vary = {"Accept-Encoding", "Accept-Language"})
        public String staticResource() { return "static"; }

        @BrowserCache(noStore = true)
        public String noStoreEndpoint() { return "no-store"; }

        @BrowserCache(strategy = CacheStrategy.PRODUCT_DETAIL, etag = true)
        public String withEtag() { return "test-result"; }

        @BrowserCache(strategy = CacheStrategy.DEFAULT)
        public String defaultStrategy() { return "default"; }

        @BrowserCache(
                strategy = CacheStrategy.PRODUCT_DETAIL,
                lastModifiedProvider = TestLastModifiedProvider.class
        )
        public String withLastModified(Long id) { return "product-" + id; }
    }

    public static class TestLastModifiedProvider implements LastModifiedProvider {
        @Override
        public Optional<Instant> getLastModified(Method method, Object[] args) {
            return Optional.empty();
        }
    }

    // ======================== 全局开关关闭 ========================

    @Nested
    @DisplayName("around — 全局开关关闭")
    class GlobalDisabledTests {

        @Test
        @DisplayName("enabled=false：直接放行，不设置任何缓存头")
        void disabled_passesThrough() throws Throwable {
            properties.setEnabled(false);
            setupJoinPointForMethod("productList");
            when(joinPoint.proceed()).thenReturn("result");

            Object result = aspect.around(joinPoint);

            assertEquals("result", result);
            verify(joinPoint).proceed();
        }
    }

    // ======================== 正常请求 ========================

    @Nested
    @DisplayName("around — 正常请求设置缓存头")
    class NormalRequestTests {

        @Test
        @DisplayName("未配置 Provider 时设置 Cache-Control 和 ETag，不设置 Last-Modified")
        void setsCacheHeaders() throws Throwable {
            setupJoinPointForMethod("productList");
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            when(joinPoint.proceed()).thenReturn("product-list");

            aspect.around(joinPoint);

            // Cache-Control: public, max-age=60, s-maxage=30 (PRODUCT_LIST)
            String cc = response.getHeader(HttpHeaders.CACHE_CONTROL);
            assertNotNull(cc);
            assertTrue(cc.contains("max-age=60"));
            assertTrue(cc.contains("s-maxage=30"));

            assertNull(response.getHeader(HttpHeaders.LAST_MODIFIED));

            // ETag
            assertNotNull(response.getHeader(HttpHeaders.ETAG));
        }

        @Test
        @DisplayName("设置 Vary 响应头")
        void setsVaryHeader() throws Throwable {
            setupJoinPointForMethod("staticResource");
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            when(joinPoint.proceed()).thenReturn("static");

            aspect.around(joinPoint);

            String vary = response.getHeader(HttpHeaders.VARY);
            assertNotNull(vary);
            assertTrue(vary.contains("Accept-Encoding"));
            assertTrue(vary.contains("Accept-Language"));
        }

        @Test
        @DisplayName("无 Vary 配置时不设置 Vary 头")
        void noVaryConfig_doesNotSetVaryHeader() throws Throwable {
            setupJoinPointForMethod("productList");
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            when(joinPoint.proceed()).thenReturn("data");

            aspect.around(joinPoint);

            assertNull(response.getHeader(HttpHeaders.VARY));
        }

        @Test
        @DisplayName("标记 BROWSER_CACHE_ASPECT_HANDLED request attribute")
        void setsAspectHandledAttribute() throws Throwable {
            setupJoinPointForMethod("productList");
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            when(joinPoint.proceed()).thenReturn("data");

            aspect.around(joinPoint);

            assertEquals(true, request.getAttribute("BROWSER_CACHE_ASPECT_HANDLED"));
        }
    }

    // ======================== noStore 模式 ========================

    @Nested
    @DisplayName("around — noStore 模式")
    class NoStoreTests {

        @Test
        @DisplayName("noStore=true：只设置 Cache-Control: no-store，不设置 Last-Modified 和 ETag")
        void noStore_onlySetsCacheControlNoStore() throws Throwable {
            setupJoinPointForMethod("noStoreEndpoint");
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            when(joinPoint.proceed()).thenReturn("no-store");

            aspect.around(joinPoint);

            assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
            assertNull(response.getHeader(HttpHeaders.LAST_MODIFIED));
            assertNull(response.getHeader(HttpHeaders.ETAG));
        }
    }

    // ======================== If-Modified-Since 304 ========================

    @Nested
    @DisplayName("around — If-Modified-Since 协商缓存")
    class IfModifiedSinceTests {

        @Test
        @DisplayName("真实更新时间未变化：返回 304 且不执行 Controller")
        void ifModifiedSinceMatch_returns304() throws Throwable {
            setupJoinPointForMethod("withLastModified", Long.class);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            Instant updateTime = Instant.parse("2026-06-13T03:00:00Z");
            request.addHeader(HttpHeaders.IF_MODIFIED_SINCE,
                    HTTP_DATE_FORMATTER.format(updateTime));
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(applicationContext.getBean(TestLastModifiedProvider.class))
                    .thenReturn(lastModifiedProvider);
            when(lastModifiedProvider.getLastModified(any(Method.class), any(Object[].class)))
                    .thenReturn(Optional.of(updateTime));

            Object result = aspect.around(joinPoint);

            assertNull(result);
            assertEquals(HttpServletResponse.SC_NOT_MODIFIED, response.getStatus());
            assertEquals(HTTP_DATE_FORMATTER.format(updateTime),
                    response.getHeader(HttpHeaders.LAST_MODIFIED));
            verify(joinPoint, never()).proceed();
        }

        @Test
        @DisplayName("资源更新时间较新：正常执行 Controller")
        void ifModifiedSinceMismatch_proceedsNormally() throws Throwable {
            setupJoinPointForMethod("withLastModified", Long.class);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            Instant clientTime = Instant.parse("2026-06-13T03:00:00Z");
            Instant updateTime = Instant.parse("2026-06-13T03:05:00Z");
            request.addHeader(HttpHeaders.IF_MODIFIED_SINCE,
                    HTTP_DATE_FORMATTER.format(clientTime));
            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(applicationContext.getBean(TestLastModifiedProvider.class))
                    .thenReturn(lastModifiedProvider);
            when(lastModifiedProvider.getLastModified(any(Method.class), any(Object[].class)))
                    .thenReturn(Optional.of(updateTime));
            when(joinPoint.proceed()).thenReturn("result");

            Object result = aspect.around(joinPoint);

            assertEquals("result", result);
            assertNotEquals(HttpServletResponse.SC_NOT_MODIFIED, response.getStatus());
            assertEquals(HTTP_DATE_FORMATTER.format(updateTime),
                    response.getHeader(HttpHeaders.LAST_MODIFIED));
        }

        @Test
        @DisplayName("Provider 返回空：继续执行 Controller")
        void providerReturnsEmpty_proceedsNormally() throws Throwable {
            setupJoinPointForMethod("withLastModified", Long.class);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            when(joinPoint.getArgs()).thenReturn(new Object[]{99L});
            when(applicationContext.getBean(TestLastModifiedProvider.class))
                    .thenReturn(lastModifiedProvider);
            when(lastModifiedProvider.getLastModified(any(Method.class), any(Object[].class)))
                    .thenReturn(Optional.empty());
            when(joinPoint.proceed()).thenReturn("not-found-flow");

            assertEquals("not-found-flow", aspect.around(joinPoint));
            assertNull(response.getHeader(HttpHeaders.LAST_MODIFIED));
        }

        @Test
        @DisplayName("Provider 异常：降级执行 Controller 和 ETag")
        void providerFailure_fallsBackToEtag() throws Throwable {
            setupJoinPointForMethod("withLastModified", Long.class);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            when(joinPoint.getArgs()).thenReturn(new Object[]{1L});
            when(applicationContext.getBean(TestLastModifiedProvider.class))
                    .thenReturn(lastModifiedProvider);
            when(lastModifiedProvider.getLastModified(any(Method.class), any(Object[].class)))
                    .thenThrow(new IllegalStateException("database unavailable"));
            when(joinPoint.proceed()).thenReturn("result");

            assertEquals("result", aspect.around(joinPoint));
            assertNull(response.getHeader(HttpHeaders.LAST_MODIFIED));
            assertNotNull(response.getHeader(HttpHeaders.ETAG));
        }
    }

    // ======================== If-None-Match 304 ========================

    @Nested
    @DisplayName("around — If-None-Match 协商缓存")
    class IfNoneMatchTests {

        @Test
        @DisplayName("If-None-Match 匹配：返回 304")
        void ifNoneMatchMatch_returns304() throws Throwable {
            setupJoinPointForMethod("withEtag");
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            // 先执行一次获取 ETag
            when(joinPoint.proceed()).thenReturn("test-result");
            aspect.around(joinPoint);
            String etag = response.getHeader(HttpHeaders.ETAG);

            // 重置响应，带上 If-None-Match
            response = new MockHttpServletResponse();
            setupRequestContext(request, response);
            request.addHeader(HttpHeaders.IF_NONE_MATCH, etag);

            Object result = aspect.around(joinPoint);

            assertNull(result);
            assertEquals(HttpServletResponse.SC_NOT_MODIFIED, response.getStatus());
        }
    }

    // ======================== ETag 不匹配 200 ========================

    @Nested
    @DisplayName("around — ETag 不匹配")
    class EtagMismatchTests {

        @Test
        @DisplayName("ETag 不匹配：正常返回 200 和响应体")
        void etagMismatch_returns200() throws Throwable {
            setupJoinPointForMethod("withEtag");
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            setupRequestContext(request, response);

            // 带上不匹配的 ETag
            request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"old-etag-value\"");

            when(joinPoint.proceed()).thenReturn("test-result");

            Object result = aspect.around(joinPoint);

            assertEquals("test-result", result);
            assertNotEquals(HttpServletResponse.SC_NOT_MODIFIED, response.getStatus());
            assertNotNull(response.getHeader(HttpHeaders.ETAG));
        }
    }
}
