package kkagong.be.domain.timer.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import kkagong.be.domain.timer.dto.request.TimerActionRequest;
import kkagong.be.domain.timer.dto.response.TimerInfoResponse;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.domain.timer.entity.TimerState.Phase;
import kkagong.be.domain.timer.entity.TimerState.Status;
import kkagong.be.domain.timer.enums.TimerAction;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import kkagong.be.global.task.TimerTaskManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimerService {

  private final InMemoryTimerStore inMemoryTimerStore;
  private final TimerTaskManager taskManager;
  private final Clock clock;
  private final TaskService taskService;
  private final TimerStatePublisher publisher;

  public void action(Long guestId, TimerActionRequest request){
    TimerAction action = request.action();

    switch (action){
      case START -> start(guestId, request.intervalId());
      case PAUSE -> pause(guestId, request.intervalId());
      case RESUME -> resume(guestId, request.intervalId());
      case STOP -> stop(guestId, request.intervalId());
    }


  }

  public void start(Long guestId, UUID requestedIntervalId) { // 타이머 시작, READY 상태일 때, 처음일 경우 객체 생성
    UUID newIntervalId = UUID.randomUUID();
    AtomicBoolean intervalExpired = new AtomicBoolean();

    inMemoryTimerStore.update(guestId, current -> {
      TimerState state = current.orElseGet(() -> taskService.readyFocus(guestId));

      // 늦은 조작보다 현재 구간의 자연 종료를 먼저 반영한다.
      if (state.isDue(clock.instant())) {
        intervalExpired.set(true);
        return taskService.nextAfterNaturalEnd(guestId, state);
      }

      if (!Objects.equals(requestedIntervalId, state.intervalId())) { //동시성 제어
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }

      if (state.status() == TimerState.Status.RUNNING) {
        return state; // 같은 구간의 중복 시작
      }

      if (state.status() != TimerState.Status.READY) { //상태가 맞지 않을 경우
        throw new ErrorException(ErrorCode.INVALID_TIMER_STATE);
      }

      // 새 집중에서만 최신 설정을 확정한다. 휴식은 앞선 집중의 설정을 사용한다.
      if (current.isPresent() && state.phase() == Phase.FOCUS) {
        state = taskService.readyFocus(guestId);
      }
      TimerState running = state.start(newIntervalId, clock.instant());
      taskService.scheduleEndTask(guestId, running);
      return running;
    }, updated -> {
      rejectExpiredAction(guestId, updated, intervalExpired.get());
      // 이번 요청에서 새 구간을 만든 경우에만 저장된 RUNNING을 알린다.
      if (newIntervalId.equals(updated.intervalId())) {
        publisher.publishRunning(guestId, updated, updated.runningSince());
      }
    });
  }

  public void pause(Long guestId, UUID requestedIntervalId) {
    if(requestedIntervalId == null){
      throw new ErrorException(ErrorCode.INVALID_REQUEST);
    }

    AtomicBoolean pausedNow = new AtomicBoolean();
    AtomicBoolean intervalExpired = new AtomicBoolean();

    inMemoryTimerStore.update(guestId, current -> {
      TimerState state = current.orElseThrow(() -> new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH));
      Instant now = clock.instant();

      if (state.isDue(now)) {
        intervalExpired.set(true);
        return taskService.nextAfterNaturalEnd(guestId, state);
      }

      if(!Objects.equals(state.intervalId(), requestedIntervalId)) {
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }

      if(state.status() == Status.READY) {
        throw new ErrorException(ErrorCode.INVALID_TIMER_STATE);
      }

      if(state.status() == Status.PAUSED) {
        return state;
      }

      // 일시정지 시 나머지 시간과 시작 당시 적용한 길이를 함께 보존한다.
      long remainingMs = Duration.between(now, state.endsAt()).toMillis();
      pausedNow.set(true);
      taskManager.cancelEndTask(guestId);
      return new TimerState(
          state.intervalId(),
          state.phase(),
          TimerState.Status.PAUSED,
          state.durationMs(),
          null,
          null,
          remainingMs,
          state.appliedDurations()
      );
    }, updated -> {
      rejectExpiredAction(guestId, updated, intervalExpired.get());
      // 실제로 PAUSED로 바뀐 요청만 발행한다. 이 호출까지 게스트 락에 포함된다.
      if (pausedNow.get()) {
        publisher.publishPaused(guestId, updated);
      }
    });
  }

  public void resume(Long guestId, UUID requestedIntervalId) {

    if(requestedIntervalId == null){
      throw new ErrorException(ErrorCode.INVALID_REQUEST);
    }

    AtomicBoolean intervalExpired = new AtomicBoolean();
    inMemoryTimerStore.update(guestId, current -> {
      TimerState state = current.orElseThrow(() -> new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH));
      Instant now = clock.instant();

      if (state.isDue(now)) {
        intervalExpired.set(true);
        return taskService.nextAfterNaturalEnd(guestId, state);
      }

      if(!Objects.equals(state.intervalId(), requestedIntervalId)) {
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }

      if(state.status() != Status.PAUSED) {
        throw new ErrorException(ErrorCode.INVALID_TIMER_STATE);
      }

      // 같은 구간이므로 고정된 잔여시간과 기존 설정으로 재개한다.
      TimerState resumed = new TimerState(
          state.intervalId(),
          state.phase(),
          Status.RUNNING,
          state.durationMs(),
          now,
          now.plusMillis(state.pausedRemainingMs()),
          null,
          state.appliedDurations()
      );
      taskService.scheduleEndTask(guestId, resumed);
      return resumed;
    }, updated -> {
      rejectExpiredAction(guestId, updated, intervalExpired.get());
      // 앞선 PAUSE가 같은 락 안에서 발행을 끝낸 뒤 재개된 RUNNING을 보낸다.
      publisher.publishRunning(guestId, updated, updated.runningSince());
    });
  }

  public void stop(Long guestId, UUID requestedIntervalId) {
    if (requestedIntervalId == null) {
      throw new ErrorException(ErrorCode.INVALID_REQUEST);
    }

    AtomicBoolean intervalExpired = new AtomicBoolean();
    inMemoryTimerStore.update(guestId, current -> {
      TimerState state = current.orElseThrow(
          () -> new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH));
      Instant now = clock.instant();

      if (state.isDue(now)) {
        intervalExpired.set(true);
        return taskService.nextAfterNaturalEnd(guestId, state);
      }

      if (!Objects.equals(state.intervalId(), requestedIntervalId)) {
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }
      if (state.status() != Status.RUNNING && state.status() != Status.PAUSED) {
        throw new ErrorException(ErrorCode.INVALID_TIMER_STATE);
      }

      // 집중을 다시 시작할 때 쓸 길이는 최신 값이고, 휴식의 다시 시작에는 기존 길이를 쓴다.
      TimerState ready = state.phase() == Phase.FOCUS
          ? taskService.readyFocus(guestId)
          : new TimerState(null, state.phase(), Status.READY, state.durationMs(),
              null, null, null, state.appliedDurations());
      taskManager.cancelEndTask(guestId);
      return ready;
    }, updated -> {
      rejectExpiredAction(guestId, updated, intervalExpired.get());
      publisher.publishReady(guestId, updated);
    });
  }

  public TimerInfoResponse getTimerInfo(Long guestId) {
    AtomicBoolean finishedNow = new AtomicBoolean();

    TimerState state = inMemoryTimerStore
        .updateIfPresent(guestId, current -> {
          if (current.phase() == Phase.FOCUS && current.status() == Status.READY) {
            // 집중 준비 화면에 다음 시작 시 적용할 최신 길이를 표시한다.
            return taskService.readyFocus(guestId);
          }
          if (!current.isDue(clock.instant())) {
            return current;
          }

          // 조회가 예정 종료를 먼저 발견해도 예약 작업과 같은 방식으로 전환한다.
          finishedNow.set(true);
          return taskService.nextAfterNaturalEnd(guestId, current);
        }, updated -> {
          if (finishedNow.get()) {
            taskService.onNaturalEndSaved(guestId, updated);
          }
        })
        .orElseGet(() -> taskService.readyFocus(guestId));

    return TimerInfoResponse.from(state, clock.instant());
  }

  private void rejectExpiredAction(Long guestId, TimerState next, boolean intervalExpired) {
    if (intervalExpired) {
      // 다음 구간을 저장하고 통지한 뒤, 이전 구간을 대상으로 온 조작에 409를 반환한다.
      taskService.onNaturalEndSaved(guestId, next);
      throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
    }
  }

}
