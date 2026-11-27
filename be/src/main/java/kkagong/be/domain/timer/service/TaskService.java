package kkagong.be.domain.timer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import kkagong.be.domain.guest.repository.GuestSettingRepository;
import kkagong.be.domain.timer.entity.TimerDurations;
import kkagong.be.domain.timer.entity.TimerSettings;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.domain.timer.entity.TimerState.Phase;
import kkagong.be.domain.timer.entity.TimerState.Status;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import kkagong.be.global.task.TimerTaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskService {

  private final InMemoryTimerStore inMemoryTimerStore;
  private final Clock clock;
  private final TimerStatePublisher publisher;
  private final GuestSettingRepository guestSettingRepository;
  private final TimerTaskManager taskManager;

  public void scheduleEndTask(Long guestId, TimerState running) {
    // 예약 당시의 구간 ID와 종료 시각으로 실행 시점의 상태를 검증한다.
    taskManager.scheduleEndTask(guestId, running.endsAt(),
        () -> finishIfDue(guestId, running.intervalId(), running.endsAt()));
  }

  public void finishIfDue(Long guestId, UUID intervalId, Instant expectedEndsAt) {
    AtomicBoolean finished = new AtomicBoolean();

    inMemoryTimerStore.updateIfPresent(guestId, current -> {
      // 이전 구간이나 재개 전의 예약은 현재 상태를 바꾸지 않는다.
      if (!intervalId.equals(current.intervalId())
          || !expectedEndsAt.equals(current.endsAt())
          || !current.isDue(clock.instant())) {
        return current;
      }
      finished.set(true);
      return nextAfterNaturalEnd(guestId, current);
    }, updated -> {
      if (finished.get()) {
        onNaturalEndSaved(guestId, updated);
      }
    });
  }

  public TimerState nextAfterNaturalEnd(Long guestId, TimerState current) {
    // 자연 종료에서 최신 자동 전환 값을 읽는다. 대기와 일시정지는 사용자 조작을 기다린다.
    TimerSettings settings = readSettings(guestId);
    Phase nextPhase = current.phase() == Phase.FOCUS ? Phase.SHORT_BREAK : Phase.FOCUS;
    // 새 집중에 최신 길이를 적용하고, 이어지는 휴식에는 앞선 집중의 길이를 적용한다.
    TimerDurations durations = nextPhase == Phase.FOCUS
        ? settings.durations()
        : current.appliedDurations();
    TimerState next = ready(nextPhase, durations);
    if (settings.autoTransition()) {
      // 예약이 늦게 실행되어도 다음 구간은 이전 구간의 예정 종료 시각에 시작한다.
      return next.start(UUID.randomUUID(), current.endsAt());
    }
    return next;
  }

  void onNaturalEndSaved(Long guestId, TimerState next) {
    // 저장 직후 같은 게스트 락 안에서 예약을 정리하고 확정된 상태를 한 번 알린다.
    if (next.status() == Status.RUNNING) {
      scheduleEndTask(guestId, next);
      publisher.publishRunning(guestId, next, clock.instant());
      return;
    }
    taskManager.cancelEndTask(guestId);
    publisher.publishReady(guestId, next);
  }

  public TimerState readyFocus(Long guestId) {
    return ready(Phase.FOCUS, readSettings(guestId).durations());
  }

  private TimerSettings readSettings(Long guestId) {
    return guestSettingRepository.findTimerSettings(guestId)
        .orElseThrow(() -> new ErrorException(ErrorCode.GUEST_NOT_FOUND));
  }

  private TimerState ready(Phase phase, TimerDurations durations) {
    return new TimerState(null, phase, Status.READY, durations.durationMs(phase),
        null, null, null, durations);
  }

}
