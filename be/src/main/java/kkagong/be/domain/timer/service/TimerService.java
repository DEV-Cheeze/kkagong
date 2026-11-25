package kkagong.be.domain.timer.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import kkagong.be.domain.guest.repository.GuestSettingRepository;
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
  private final GuestSettingRepository guestSettingRepository;

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

    TimerState start = inMemoryTimerStore.update(guestId, current -> {
      TimerState state = current.orElseGet(() -> readyFocus(guestId));

      Instant now = clock.instant();
      if (state.status() == Status.RUNNING && !now.isBefore(state.endsAt())) {
        taskManager.cancelEndTask(guestId);
        return taskService.nextAfterNaturalEnd(state);
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

      state = new TimerState(
          newIntervalId,
          state.phase(),
          TimerState.Status.RUNNING,
          state.durationMs(),
          now,
          now.plusMillis(state.durationMs()),
          null
      );
      taskManager.scheduleEndTask(guestId, state, newIntervalId); //taskScheduler에 특정 서버코드 (람다) 실행

      return state;
    });

    if (!newIntervalId.equals(start.intervalId())
        && (requestedIntervalId == null || !requestedIntervalId.equals(start.intervalId()))) {
      throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
    }
  }

  public void pause(Long guestId, UUID requestedIntervalId) {
    if(requestedIntervalId == null){
      throw new ErrorException(ErrorCode.INVALID_REQUEST);
    }

    TimerState updated = inMemoryTimerStore.update(guestId, current -> {
      TimerState state = current.orElseThrow(() -> new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH));
      Instant now = clock.instant();

      if (state.status() == Status.RUNNING && !now.isBefore(state.endsAt())) {
        taskManager.cancelEndTask(guestId);
        return taskService.nextAfterNaturalEnd(state);
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

      //아직 넘어가기 전이라면
      long remainingMs = Duration.between(now, state.endsAt()).toMillis();

      taskManager.cancelEndTask(guestId);
      return new TimerState(
          state.intervalId(),
          state.phase(),
          TimerState.Status.PAUSED,
          state.durationMs(),
          null,
          null,
          remainingMs
      );
    });

    if (!Objects.equals(updated.intervalId(), requestedIntervalId)) {
      throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
    }
  }

  public void resume(Long guestId, UUID requestedIntervalId) {

    if(requestedIntervalId == null){
      throw new ErrorException(ErrorCode.INVALID_REQUEST);
    }

    TimerState updated = inMemoryTimerStore.update(guestId, current -> {
      TimerState state = current.orElseThrow(() -> new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH));
      Instant now = clock.instant();

      if (state.status() == Status.RUNNING && !now.isBefore(state.endsAt())) {
        taskManager.cancelEndTask(guestId);
        return taskService.nextAfterNaturalEnd(state);
      }

      if(!Objects.equals(state.intervalId(), requestedIntervalId)) {
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }

      if(state.status() != Status.PAUSED) {
        throw new ErrorException(ErrorCode.INVALID_TIMER_STATE);
      }

      TimerState resumed = new TimerState(
          state.intervalId(),
          state.phase(),
          Status.RUNNING,
          state.durationMs(),
          now,
          now.plusMillis(state.pausedRemainingMs()),
          null
      );
      taskManager.scheduleEndTask(guestId, resumed, resumed.intervalId());
      return resumed;

    });

    if (!Objects.equals(updated.intervalId(), requestedIntervalId)) {
      throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
    }
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

      if (state.status() == Status.RUNNING && !now.isBefore(state.endsAt())) {
        TimerState next = taskService.nextAfterNaturalEnd(state);
        taskManager.cancelEndTask(guestId);
        intervalExpired.set(true); //null값만으로는 이후 요청에 대한 409반환을 못함
        return next;
      }

      if (!Objects.equals(state.intervalId(), requestedIntervalId)) {
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }
      if (state.status() != Status.RUNNING && state.status() != Status.PAUSED) {
        throw new ErrorException(ErrorCode.INVALID_TIMER_STATE);
      }

      taskManager.cancelEndTask(guestId);
      return new TimerState(
          null, state.phase(), Status.READY, state.durationMs(), null, null, null
      );
    });

    if (intervalExpired.get()) {
      throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
    }
  }

  public TimerInfoResponse getTimerInfo(Long guestId) {
    Instant now = clock.instant();

    TimerState state = inMemoryTimerStore
        .updateIfPresent(guestId, current -> {
          if (current.status() != Status.RUNNING || now.isBefore(current.endsAt())) {
            return current;
          }

          TimerState next = taskService.nextAfterNaturalEnd(current);
          taskManager.cancelEndTask(guestId);
          return next;
        })
        .orElseGet(() -> readyFocus(guestId));

    return TimerInfoResponse.from(state, now);
  }

  private TimerState readyFocus(Long guestId) {
    long durationMs = guestSettingRepository.getFocusMinutes(guestId) * 60_000L;
    return new TimerState(null, Phase.FOCUS, Status.READY, durationMs, null, null, null);
  }

}
