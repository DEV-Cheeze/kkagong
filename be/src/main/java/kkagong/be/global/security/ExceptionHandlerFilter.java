package kkagong.be.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import kkagong.be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class ExceptionHandlerFilter extends OncePerRequestFilter { //GuestAuthenticationFilter에서 발생하는 에러 catch

  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    try{
      filterChain.doFilter(request, response);
    } catch(ErrorException e){ //필터 에러 - 4xx
      setErrorResponse(response, e.getErrorCode());
    } catch(Exception e){ //이 외의 서버 에러 - 5xx
      setErrorResponse(response, ErrorCode.INTERNAL_SERVER_ERROR);
    }
  }

  private void setErrorResponse(HttpServletResponse response, ErrorCode e) throws IOException {
    response.setContentType("application/json;charset=UTF-8");
    response.setStatus(e.getHttpStatus().value());

    objectMapper.writeValue(
        response.getOutputStream(),
        ApiResponse.error(e)
    );
  }

}
