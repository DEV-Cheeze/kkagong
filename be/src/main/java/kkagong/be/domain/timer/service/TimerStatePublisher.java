package kkagong.be.domain.timer.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import kkagong.be.domain.timer.dto.response.TimerNext;
import kkagong.be.domain.timer.entity.TimerState;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimerStatePublisher {

  private final SimpMessagingTemplate messagingTemplate;
  private final SimpUserRegistry userRegistry;

  public void publishRunning(Long guestId, TimerState state, Instant serverNow, TimerNext next) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("intervalId", state.intervalId());
    data.put("phase", state.phase());
    data.put("status", state.status());
    data.put("durationMs", state.durationMs());
    data.put("endsAt", state.endsAt());
    data.put("serverNow", serverNow);
    data.put("next", next);

    sendToGuest(guestId.toString(), Map.of("type", "TIMER_STATE_CHANGED", "data", data));
  }

  public void publishPaused(Long guestId, TimerState state) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("intervalId", state.intervalId());
    data.put("status", state.status());
    sendToGuest(guestId.toString(), Map.of("type", "TIMER_STATE_CHANGED", "data", data));
  }

  public void publishReady(Long guestId, TimerState state) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("status",  state.status());
    data.put("phase", state.phase());
    data.put("durationMs", state.durationMs());
    sendToGuest(guestId.toString(), Map.of("type", "TIMER_STATE_CHANGED", "data", data));
  }

  public void sendToGuest(String guestName, Map<String, Object> event) {
    SimpUser user = userRegistry.getUser(guestName);
    if (user == null) {
      return;
    }

    // Spring Framework #37680: 순서 보장이 켜져 있으면 세션마다 새 메시지를 만들어야 한다.
    // TODO: 수정 버전 적용 후 convertAndSendToUser(guestName, "/queue/timer", event) 단일 호출로 복원한다.
    for (SimpSession session : user.getSessions()) {
      String sessionId = session.getId();
      SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
      headers.setSessionId(sessionId);
      headers.setLeaveMutable(true);
      messagingTemplate.convertAndSendToUser(
          sessionId, "/queue/timer", event, headers.getMessageHeaders());
    }
  }
}
