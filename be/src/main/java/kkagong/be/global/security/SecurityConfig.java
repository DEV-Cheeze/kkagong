package kkagong.be.global.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SecurityConfig {


  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
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
              response.setContentType("application/json;charset=UTF-8");
              response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
              response.getWriter().write("""
                  {"resultCode":"UNAUTHORIZED","msg":"인증 정보가 없거나 만료되었습니다."}
                  """);
            })
        );

    return http.build();
  }



}
