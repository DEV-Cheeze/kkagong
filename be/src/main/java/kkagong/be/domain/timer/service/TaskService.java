package kkagong.be.domain.timer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.global.task.TimerTaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskService {

  private final InMemoryTimerStore inMemoryTimerStore;
  private final Clock clock;

  public void finishIfDue(Long guestId, UUID intervalId, Instant expectedEndsAt) {
    inMemoryTimerStore.updateIfPresent(guestId, current -> {
      if (current.status() != TimerState.Status.RUNNING
          || !intervalId.equals(current.intervalId())
          || !expectedEndsAt.equals(current.endsAt())
          || clock.instant().isBefore(expectedEndsAt)) {
        return current;
      }

      return nextAfterNaturalEnd(current);
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
