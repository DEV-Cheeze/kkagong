package kkagong.be.domain.timer.controller;

import java.security.Principal;
import java.util.Map;
import kkagong.be.domain.timer.service.TimerStatePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class TimerStompController {

  private final TimerStatePublisher publisher;

  @MessageMapping("/timer/test")
  public void sendTestMessage(Principal principal) {
    Map<String, Object> event = Map.of(
        "type", "TIMER_TEST",
        "data", Map.of("message", "타이머 구독 확인")
    );

    publisher.sendToGuest(principal.getName(), event);
  }

}
