package kkagong.be.domain.timer.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import kkagong.be.domain.guest.repository.GuestSettingRepository;
import kkagong.be.domain.guest.service.TimerSettingsChanged;
import kkagong.be.domain.timer.dto.response.TimerNext;
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
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class TaskService {

  private static final int FOCUS_INTERVALS_PER_CYCLE = 4;

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
      // 확인을 통과한 자연 종료만 같은 게스트 락 안에서 완료 횟수와 상태를 함께 저장한다.
      finished.set(true);
      return nextAfterNaturalEnd(guestId, current);
    }, updated -> {
      if (finished.get()) {
        onNaturalEndSaved(guestId, updated);
      }
    });
  }

  public TimerState nextAfterNaturalEnd(Long guestId, TimerState current) {
    // 자연 종료 시 최신 자동 전환 설정과 다음 집중에 쓸 길이를 읽는다.
    TimerSettings settings = readSettings(guestId);
    TimerState next = nextReadyAfterCompletion(current, settings.durations());
    if (settings.autoTransition()) {
      // 예약이 늦게 실행되어도 다음 구간은 이전 구간의 예정 종료 시각에 시작한다.
      return next.start(UUID.randomUUID(), current.endsAt());
    }
    return next;
  }

  TimerState nextAfterSkip(Long guestId, TimerState current, Instant now) {
    // 수동 스킵은 현재 서버 시각부터 새 구간을 실행한다.
    TimerState next = nextReadyAfterCompletion(current, readSettings(guestId).durations());
    return next.start(UUID.randomUUID(), now);
  }

  private TimerState nextReadyAfterCompletion(TimerState current, TimerDurations nextFocusDurations) {
    // 자연 종료와 스킵은 같은 규칙으로 다음 구간과 완료 횟수를 결정한다.
    return switch (current.phase()) {
      case FOCUS -> {
        // 집중을 한 회로 세고, 휴식에는 이 집중을 시작할 때 보관한 길이를 사용한다.
        int completedFocusCount = current.completedFocusCount() + 1;
        Phase breakPhase = completedFocusCount == FOCUS_INTERVALS_PER_CYCLE
            ? Phase.LONG_BREAK : Phase.SHORT_BREAK;
        yield ready(breakPhase, current.appliedDurations(), completedFocusCount);
      }
      // 일반 휴식 뒤에는 횟수를 이어가고, 긴 휴식 뒤에는 새 주기를 시작한다.
      case SHORT_BREAK -> ready(Phase.FOCUS, nextFocusDurations, current.completedFocusCount());
      case LONG_BREAK -> ready(Phase.FOCUS, nextFocusDurations, 0);
    };
  }

  void onNaturalEndSaved(Long guestId, TimerState next) {
    // 저장 직후 같은 게스트 락 안에서 예약을 정리하고 확정된 상태를 한 번 알린다.
    if (next.status() == Status.RUNNING) {
      scheduleEndTask(guestId, next);
      publishRunning(guestId, next, clock.instant());
      return;
    }
    taskManager.cancelEndTask(guestId);
    publisher.publishReady(guestId, next);
  }

  void publishRunning(Long guestId, TimerState running, Instant serverNow) {
    publisher.publishRunning(guestId, running, serverNow, nextFor(guestId, running));
  }

  TimerNext nextFor(Long guestId, TimerState current) {
    return current.status() == Status.RUNNING ? nextFor(current, readSettings(guestId)) : null;
  }

  private TimerNext nextFor(TimerState running, TimerSettings settings) {
    if (!settings.autoTransition()) {
      return null;
    }
    // 실제 전이와 같은 계산을 쓰며, 예고를 만들 때는 현재 상태나 예약을 바꾸지 않는다.
    TimerState next = nextReadyAfterCompletion(running, settings.durations());
    return new TimerNext(next.phase(), next.durationMs());
  }

  @TransactionalEventListener
  public void refreshNextAfterSettingsChange(TimerSettingsChanged event) {
    // DB 커밋 뒤 게스트별 락 안에서 현재 구간을 기준으로 예고만 갱신한다.
    inMemoryTimerStore.updateIfPresent(event.guestId(), current -> current, current -> {
      Instant now = clock.instant();
      if (current.status() != Status.RUNNING || current.isDue(now)) {
        return;
      }
      TimerNext next = nextFor(event.guestId(), current);
      if (!Objects.equals(nextFor(current, event.previous()), next)) {
        publisher.publishRunning(event.guestId(), current, now, next);
      }
    });
  }

  public TimerState readyFocus(Long guestId, int completedFocusCount) {
    return ready(Phase.FOCUS, readSettings(guestId).durations(), completedFocusCount);
  }

  private TimerSettings readSettings(Long guestId) {
    return guestSettingRepository.findTimerSettings(guestId)
        .orElseThrow(() -> new ErrorException(ErrorCode.GUEST_NOT_FOUND));
  }

  private TimerState ready(Phase phase, TimerDurations durations, int completedFocusCount) {
    return new TimerState(null, phase, Status.READY, durations.durationMs(phase),
        null, null, null, durations, completedFocusCount);
  }

}
