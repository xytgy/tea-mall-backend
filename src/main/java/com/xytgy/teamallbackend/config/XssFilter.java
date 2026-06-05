package com.xytgy.teamallbackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
        filterChain.doFilter(new XssRequestWrapper(request), response);
    }

    private static class XssRequestWrapper extends HttpServletRequestWrapper {

        XssRequestWrapper(HttpServletRequest request) {
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
            if (values == null) return null;
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

        private static String sanitize(String value) {
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
    }
}
