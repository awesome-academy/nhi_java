package demo.tripgo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private HttpServletRequest authRequest(String ip) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/auth/login");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(ip);
        return request;
    }

    @Test
    void allowsUpToLimitThenReturns429() throws Exception {
        SecurityErrorResponder responder = mock(SecurityErrorResponder.class);
        RateLimitFilter filter = new RateLimitFilter(responder, 2, 60);
        HttpServletRequest request = authRequest("1.2.3.4");
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain); // 1 - qua
        filter.doFilterInternal(request, response, chain); // 2 - qua
        filter.doFilterInternal(request, response, chain); // 3 - chặn

        verify(chain, times(2)).doFilter(request, response);
        verify(responder, times(1)).write(response, 429, "Too many requests, please try again later");
    }

    @Test
    void differentIpsHaveSeparateBudgets() throws Exception {
        SecurityErrorResponder responder = mock(SecurityErrorResponder.class);
        RateLimitFilter filter = new RateLimitFilter(responder, 1, 60);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(authRequest("1.1.1.1"), response, chain);
        filter.doFilterInternal(authRequest("2.2.2.2"), response, chain);

        // Mỗi IP dùng ngân sách riêng nên cả hai đều qua.
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(response));
        verify(responder, never()).write(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onlyRateLimitsPostAuthPaths() {
        RateLimitFilter filter = new RateLimitFilter(mock(SecurityErrorResponder.class), 20, 60);

        assertThat(filter.shouldNotFilter(request("POST", "/auth/login"))).isFalse();
        assertThat(filter.shouldNotFilter(request("POST", "/auth/register"))).isFalse();
        assertThat(filter.shouldNotFilter(request("GET", "/auth/me"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/tours/1/reviews"))).isTrue();
        assertThat(filter.shouldNotFilter(request("POST", "/bookings"))).isTrue();
    }

    private HttpServletRequest request(String method, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getServletPath()).thenReturn(path);
        return request;
    }
}
