package kkagong.be.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import kkagong.be.domain.guest.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class GuestAuthenticationFilter extends OncePerRequestFilter {

  private static final String GUEST_TOKEN_COOKIE_NAME = "guestId";
  private final AuthService authService;
  private final RequestMatcher excludedRequest = PathPatternRequestMatcher.withDefaults()
      .matcher(HttpMethod.POST, "/api/v1/guests/signup");;


  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    return excludedRequest.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String token = extractToken(request);

    if (token == null ||  token.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }

    Long id = authService.validateToken(token);

    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority("USER")));
    SecurityContextHolder.getContext().setAuthentication(authToken);

    filterChain.doFilter(request, response);
  }

  private String extractToken(HttpServletRequest request) { //토큰 추출
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }

    for (Cookie cookie : cookies) {
      if (GUEST_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
