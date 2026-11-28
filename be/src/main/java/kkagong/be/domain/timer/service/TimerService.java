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
      TimerState state = current.orElseGet(() -> taskService.readyFocus(guestId, 0));

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

      // 새 집중에는 최신 길이와 현재 주기의 완료 횟수를 적용한다.
      if (current.isPresent() && state.phase() == Phase.FOCUS) {
        state = taskService.readyFocus(guestId, state.completedFocusCount());
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

      // 일시정지 시 잔여시간을 고정하고 적용한 길이와 완료 횟수를 보관한다.
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
          state.appliedDurations(),
          state.completedFocusCount()
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

      // 같은 구간을 고정된 잔여시간부터 재개하며 완료 횟수를 이어간다.
      TimerState resumed = new TimerState(
          state.intervalId(),
          state.phase(),
          Status.RUNNING,
          state.durationMs(),
          now,
          now.plusMillis(state.pausedRemainingMs()),
          null,
          state.appliedDurations(),
          state.completedFocusCount()
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

      // 중지는 완료 횟수를 유지한 채 같은 구간의 대기로 돌아간다.
      // 다시 시작할 집중은 최신 길이, 휴식은 현재 구간에 적용한 길이를 사용한다.
      TimerState ready = state.phase() == Phase.FOCUS
          ? taskService.readyFocus(guestId, state.completedFocusCount())
          : new TimerState(null, state.phase(), Status.READY, state.durationMs(),
              null, null, null, state.appliedDurations(), state.completedFocusCount());
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
            // 준비 화면의 집중 길이를 갱신할 때도 현재 주기의 완료 횟수를 유지한다.
            return taskService.readyFocus(guestId, current.completedFocusCount());
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
        .orElseGet(() -> taskService.readyFocus(guestId, 0));

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
