package dev.hindsight.decision.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Records request start time for decision latency published on {@code decision.made}. */
@Component
@Profile("!shadow")
public class DecisionLatencyFilter extends OncePerRequestFilter {

    static final String START_NANO_ATTR = "hindsight.decision.startNano";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().endsWith("/v1/decisions");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        request.setAttribute(START_NANO_ATTR, System.nanoTime());
        filterChain.doFilter(request, response);
    }

    static long latencyMs(HttpServletRequest request) {
        Object start = request.getAttribute(START_NANO_ATTR);
        if (start instanceof Long startNano) {
            return Math.max(0L, (System.nanoTime() - startNano) / 1_000_000L);
        }
        return 0L;
    }
}
