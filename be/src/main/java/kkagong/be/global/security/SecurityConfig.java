package kkagong.be.global.security;

import kkagong.be.domain.guest.service.AuthService;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final AuthService authService;

  @Bean
  public RoleHierarchy roleHierarchy(){
    return RoleHierarchyImpl
        .withDefaultRolePrefix()
        .role("ADMIN").implies("MANAGER")
        .role("MANAGER").implies("USER")
        .build();
  }

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper, AuthService authService)
      throws Exception {

    http
        .cors(cors -> {})
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(new GuestAuthenticationFilter(authService), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(new ExceptionHandlerFilter(objectMapper), GuestAuthenticationFilter.class)

        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/v1/guests/me").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/v1/guests/me/sound-settings").authenticated()
            .requestMatchers(HttpMethod.PATCH, "/api/v1/guests/me/sound-settings/{soundCode}").authenticated()
            .requestMatchers(HttpMethod.PATCH, "/api/v1/guests/me/profile").authenticated()
            .requestMatchers(HttpMethod.PATCH, "/api/v1/guests/me/timer-settings").authenticated()
            .requestMatchers(HttpMethod.POST, "/api/v1/timer/actions").authenticated()
            .requestMatchers(HttpMethod.GET, "/api/v1/timer").authenticated()
            .requestMatchers(HttpMethod.GET, "/ws-stomp").authenticated()
            .anyRequest().permitAll()
        )

        .exceptionHandling(handling -> handling
            .authenticationEntryPoint((request, response, ex) -> {
              ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
              response.setContentType("application/json;charset=UTF-8");
              response.setStatus(errorCode.getHttpStatus().value());
              objectMapper.writeValue(
                  response.getOutputStream(),
                  ApiResponse.error(errorCode)
              );
            })
        );

    return http.build();
  }



}
