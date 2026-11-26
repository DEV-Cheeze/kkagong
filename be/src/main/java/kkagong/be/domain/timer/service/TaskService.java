package kkagong.be.domain.timer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.global.task.TimerTaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskService {

  private final InMemoryTimerStore inMemoryTimerStore;
  private final Clock clock;
  private final TimerStatePublisher publisher;

  public void finishIfDue(Long guestId, UUID intervalId, Instant expectedEndsAt) {
    AtomicBoolean finished = new AtomicBoolean();

    inMemoryTimerStore.updateIfPresent(guestId, current -> {
      if (current.status() != TimerState.Status.RUNNING
          || !intervalId.equals(current.intervalId())
          || !expectedEndsAt.equals(current.endsAt())
          || clock.instant().isBefore(expectedEndsAt)) {
        return current;
      }
      finished.set(true);
      return nextAfterNaturalEnd(current);
    }, updated -> {
      // 이 예약이 실제로 전이시킨 READY를 저장한 뒤 같은 게스트 락 안에서 알린다.
      if (finished.get()) {
        publisher.publishReady(guestId, updated);
      }
    });
  }

  public TimerState nextAfterNaturalEnd(TimerState current) { //오류 없이 시간이 만료됐다면 다음 상태로 자동 전이
    boolean focusEnded = current.phase() == TimerState.Phase.FOCUS;
    TimerState.Phase nextPhase = focusEnded
        ? TimerState.Phase.SHORT_BREAK
        : TimerState.Phase.FOCUS;
    long durationMs = (focusEnded ? 5 : 25) * 60_000L;

    return new TimerState(
        null, nextPhase, TimerState.Status.READY,
        durationMs, null, null, null
    );
  }

}
