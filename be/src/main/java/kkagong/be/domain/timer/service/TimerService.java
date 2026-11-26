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
      TimerState state = current.orElseGet(() -> readyFocus(guestId));

      Instant now = clock.instant();
      if (state.status() == Status.RUNNING && !now.isBefore(state.endsAt())) {
        taskManager.cancelEndTask(guestId);
        intervalExpired.set(true);
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
    }, updated -> {
      // 자연 종료가 먼저 반영됐다면 READY를 알린 뒤 늦은 START에 409를 반환한다.
      if (intervalExpired.get()) {
        publisher.publishReady(guestId, updated);
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }
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

      if (state.status() == Status.RUNNING && !now.isBefore(state.endsAt())) {
        taskManager.cancelEndTask(guestId);
        intervalExpired.set(true);
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
      pausedNow.set(true);
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
    }, updated -> {
      if (intervalExpired.get()) {
        publisher.publishReady(guestId, updated);
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }
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

      if (state.status() == Status.RUNNING && !now.isBefore(state.endsAt())) {
        taskManager.cancelEndTask(guestId);
        intervalExpired.set(true);
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
    }, updated -> {
      if (intervalExpired.get()) {
        publisher.publishReady(guestId, updated);
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }
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
    }, updated -> {
      // 정상 중지와 자연 종료 모두 저장된 READY를 알린다.
      publisher.publishReady(guestId, updated);
      if (intervalExpired.get()) {
        throw new ErrorException(ErrorCode.TIMER_INTERVAL_MISMATCH);
      }
    });
  }

  public TimerInfoResponse getTimerInfo(Long guestId) {
    AtomicBoolean finishedNow = new AtomicBoolean();

    TimerState state = inMemoryTimerStore
        .updateIfPresent(guestId, current -> {
          if (current.status() != Status.RUNNING || clock.instant().isBefore(current.endsAt())) {
            return current;
          }

          TimerState next = taskService.nextAfterNaturalEnd(current);
          taskManager.cancelEndTask(guestId);
          finishedNow.set(true);
          return next;
        }, updated -> {
          // 조회가 자연 종료를 처리했다면 같은 락 안에서 READY를 알린다.
          if (finishedNow.get()) {
            publisher.publishReady(guestId, updated);
          }
        })
        .orElseGet(() -> readyFocus(guestId));

    return TimerInfoResponse.from(state, clock.instant());
  }

  private TimerState readyFocus(Long guestId) {
    long durationMs = guestSettingRepository.getFocusMinutes(guestId) * 60_000L;
    return new TimerState(null, Phase.FOCUS, Status.READY, durationMs, null, null, null);
  }

}
