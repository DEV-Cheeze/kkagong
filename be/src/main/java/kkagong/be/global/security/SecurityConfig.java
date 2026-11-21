package kkagong.be.global.security;

import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.response.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@EnableWebSecurity
@Configuration
public class SecurityConfig {


  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
    http
        .cors(cors -> {})
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/**","/error", "/favicon.ico").permitAll()
            .anyRequest().authenticated()
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
