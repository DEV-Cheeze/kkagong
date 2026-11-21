package kkagong.be.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import kkagong.be.domain.guest.repository.GuestRepository;
import kkagong.be.domain.guest.service.AuthService;
import kkagong.be.global.exception.ErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class GuestAuthenticationFilter extends OncePerRequestFilter {

  private static final String GUEST_TOKEN_COOKIE_NAME = "guestId";
  private final AuthService authService;

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

    // TODO: 토큰 형식, DB 해시 조회 결과, 만료 여부를 검증한다.
    // TODO: 검증 성공 시 확정한 guestId로 Authentication을 만들고 SecurityContext에 등록한다.
    // TODO: 인증 실패 시 오류 응답을 반환하고 다음 필터 호출을 중단한다.

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
