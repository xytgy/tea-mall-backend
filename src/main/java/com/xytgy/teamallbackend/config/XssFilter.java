package com.xytgy.teamallbackend.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class XssFilter extends OncePerRequestFilter {

    private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?i)<script[^>]*>.*?</script>", Pattern.DOTALL);
    private static final Pattern ON_EVENT_PATTERN = Pattern.compile("(?i)\\bon\\w+\\s*=");
    private static final Pattern JS_PROTOCOL_PATTERN = Pattern.compile("(?i)javascript\\s*:");
    private static final Pattern VB_PROTOCOL_PATTERN = Pattern.compile("(?i)vbscript\\s*:");
    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("(?i)expression\\s*\\(");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            filterChain.doFilter(new XssJsonRequestWrapper(request), response);
        } else {
            filterChain.doFilter(new XssParamRequestWrapper(request), response);
        }
    }

    static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        value = SCRIPT_PATTERN.matcher(value).replaceAll("");
        value = ON_EVENT_PATTERN.matcher(value).replaceAll("");
        value = JS_PROTOCOL_PATTERN.matcher(value).replaceAll("");
        value = VB_PROTOCOL_PATTERN.matcher(value).replaceAll("");
        value = EXPRESSION_PATTERN.matcher(value).replaceAll("");
        return value;
    }

    private static class XssParamRequestWrapper extends HttpServletRequestWrapper {

        XssParamRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return value == null ? null : sanitize(value);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) return new String[0];
            String[] sanitized = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitized[i] = sanitize(values[i]);
            }
            return sanitized;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> original = super.getParameterMap();
            Map<String, String[]> cleaned = new LinkedHashMap<>();
            original.forEach((key, values) -> {
                String[] sanitized = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    sanitized[i] = sanitize(values[i]);
                }
                cleaned.put(key, sanitized);
            });
            return cleaned;
        }
    }

    private static class XssJsonRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        XssJsonRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            String original = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            this.body = sanitize(original).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
