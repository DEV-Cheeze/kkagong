package kkagong.be.global.stomp;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig
    implements WebSocketMessageBrokerConfigurer {

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) { //STOMP 연결 엔드포인트 설정
    registry.addEndpoint("/ws-stomp");

    registry.setPreserveReceiveOrder(true); //메시지 처리 순서 유지 true
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.setApplicationDestinationPrefixes("/app"); //STOMP 컨트롤러에서 처리할 Prefix 설정

    registry.enableSimpleBroker("/topic", "/queue"); //브로커 활성화 Prefix
    // 서버에서 발행한 순서대로 각 WebSocket 세션에 전달한다.
    registry.setPreservePublishOrder(true);
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    var authorization = MessageMatcherDelegatingAuthorizationManager.builder()
        .nullDestMatcher().authenticated()
        .simpSubscribeDestMatchers(
            "/user/queue/timer", "/user/queue/room-assigned",
            "/topic/rooms/*", "/topic/rooms/*/tasks").authenticated()
        .simpMessageDestMatchers(
            "/app/timer/test", "/app/rooms/join", "/app/rooms/leave",
            "/app/rooms/*/tasks").authenticated()
        .anyMessage().denyAll()
        .build();

    registration.interceptors(
        new SecurityContextChannelInterceptor(),
        new AuthorizationChannelInterceptor(authorization)
    );
  }
}
