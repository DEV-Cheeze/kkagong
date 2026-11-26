package kkagong.be.domain.timer.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import kkagong.be.domain.guest.repository.GuestSettingRepository;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import kkagong.be.global.task.TimerTaskManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimerServiceTest {

  private static final long GUEST_ID = 1L;
  private static final long FOCUS_DURATION_MS = 25 * 60_000L;
  private static final Instant NOW = Instant.parse("2026-11-25T01:25:00Z");

  private final InMemoryTimerStore store = new InMemoryTimerStore();
  private final TimerTaskManager taskManager = mock(TimerTaskManager.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final GuestSettingRepository guestSettingRepository = mock(GuestSettingRepository.class);
  private final TimerStatePublisher publisher = mock(TimerStatePublisher.class);
  private final TaskService taskService = new TaskService(store, clock, publisher);
  private final TimerService service =
      new TimerService(store, taskManager, clock, taskService, guestSettingRepository, publisher);

  @Test
  void firstStartStoresAndSchedulesTheSameInterval() {
    when(guestSettingRepository.getFocusMinutes(GUEST_ID)).thenReturn(40);
    service.start(GUEST_ID, null);
    TimerState running = store.find(GUEST_ID).orElseThrow();

    assertThat(running.status()).isEqualTo(TimerState.Status.RUNNING);
    assertThat(running.intervalId()).isNotNull();
    assertThat(running.durationMs()).isEqualTo(40 * 60_000L);
    assertThat(running.endsAt()).isEqualTo(NOW.plusSeconds(40 * 60));
    verify(taskManager).scheduleEndTask(GUEST_ID, running, running.intervalId());
    verify(publisher).publishRunning(GUEST_ID, running, NOW);
    verifyNoMoreInteractions(publisher);
  }

  @Test
  void duplicatePauseFreezesRemainingTimeAndCancelsOnce() {
    TimerState running = storeRunningInterval(NOW.plusSeconds(20 * 60));

    service.pause(GUEST_ID, running.intervalId());
    TimerState paused = store.find(GUEST_ID).orElseThrow();
    service.pause(GUEST_ID, running.intervalId());

    assertThat(paused.status()).isEqualTo(TimerState.Status.PAUSED);
    assertThat(paused.intervalId()).isEqualTo(running.intervalId());
    assertThat(paused.pausedRemainingMs()).isEqualTo(20 * 60_000L);
    assertThat(paused.endsAt()).isNull();
    assertThat(store.find(GUEST_ID)).contains(paused);
    verify(taskManager).cancelEndTask(GUEST_ID);
  }

  @Test
  void pauseAtDeadlineStoresNaturalEndBeforeReturningConflict() {
    TimerState running = storeRunningInterval(NOW);

    assertThatThrownBy(() -> service.pause(GUEST_ID, running.intervalId()))
        .isInstanceOfSatisfying(ErrorException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TIMER_INTERVAL_MISMATCH));

    TimerState ready = store.find(GUEST_ID).orElseThrow();
    assertThat(ready.status()).isEqualTo(TimerState.Status.READY);
    assertThat(ready.phase()).isEqualTo(TimerState.Phase.SHORT_BREAK);
    verify(taskManager).cancelEndTask(GUEST_ID);
  }

  @Test
  void pauseRequiresIntervalId() {
    TimerState running = storeRunningInterval(NOW);

    assertThatThrownBy(() -> service.pause(GUEST_ID, null))
        .isInstanceOfSatisfying(ErrorException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

    assertThat(store.find(GUEST_ID)).contains(running);
    verifyNoInteractions(taskManager);
  }

  @Test
  void finishIgnoresOldDeadlineAndEndsCurrentRunOnce() {
    TimerState running = storeRunningInterval(NOW);

    taskService.finishIfDue(GUEST_ID, running.intervalId(), NOW.minusSeconds(1));
    assertThat(store.find(GUEST_ID)).contains(running);
    verifyNoInteractions(publisher);

    taskService.finishIfDue(GUEST_ID, running.intervalId(), NOW);
    TimerState ready = store.find(GUEST_ID).orElseThrow();
    assertThat(ready.status()).isEqualTo(TimerState.Status.READY);
    assertThat(ready.phase()).isEqualTo(TimerState.Phase.SHORT_BREAK);
    verify(publisher).publishReady(GUEST_ID, ready);

    taskService.finishIfDue(GUEST_ID, running.intervalId(), NOW);
    assertThat(store.find(GUEST_ID)).contains(ready);
    verifyNoMoreInteractions(publisher);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1})
  void startEndsExpiredIntervalBeforeRejectingItsId(long secondsOverdue) {
    TimerState running = storeRunningInterval(NOW.minusSeconds(secondsOverdue));

    assertThatThrownBy(() -> service.start(GUEST_ID, running.intervalId()))
        .isInstanceOfSatisfying(ErrorException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TIMER_INTERVAL_MISMATCH));

    TimerState ready = store.find(GUEST_ID).orElseThrow();
    assertThat(ready.status()).isEqualTo(TimerState.Status.READY);
    assertThat(ready.phase()).isEqualTo(TimerState.Phase.SHORT_BREAK);
    assertThat(ready.intervalId()).isNull();
    verify(taskManager).cancelEndTask(GUEST_ID);
  }

  @Test
  void duplicateStartBeforeDeadlineKeepsCurrentRun() {
    TimerState running = storeRunningInterval(NOW.plusSeconds(1));

    service.start(GUEST_ID, running.intervalId());
    assertThat(store.find(GUEST_ID)).contains(running);
    verifyNoInteractions(taskManager);
    verifyNoInteractions(publisher);
  }

  @Test
  void resumeSchedulesSameIntervalUsingPausedRemainingTime() {
    TimerState paused = new TimerState(
        UUID.randomUUID(), TimerState.Phase.FOCUS, TimerState.Status.PAUSED,
        FOCUS_DURATION_MS, null, null, 20 * 60_000L);
    store.update(GUEST_ID, current -> paused);

    service.resume(GUEST_ID, paused.intervalId());

    TimerState resumed = store.find(GUEST_ID).orElseThrow();
    assertThat(resumed).isEqualTo(new TimerState(
        paused.intervalId(), paused.phase(), TimerState.Status.RUNNING,
        FOCUS_DURATION_MS, NOW, NOW.plusSeconds(20 * 60), null));
    verify(taskManager).scheduleEndTask(GUEST_ID, resumed, resumed.intervalId());

    assertThatThrownBy(() -> service.resume(GUEST_ID, paused.intervalId()))
        .isInstanceOfSatisfying(ErrorException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIMER_STATE));
    assertThat(store.find(GUEST_ID)).contains(resumed);
    verifyNoMoreInteractions(taskManager);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stopReturnsRunningOrPausedIntervalToSamePhaseReady(boolean paused) {
    TimerState running = storeRunningInterval(NOW.plusSeconds(60));
    if (paused) {
      service.pause(GUEST_ID, running.intervalId());
    }

    service.stop(GUEST_ID, running.intervalId());

    TimerState ready = store.find(GUEST_ID).orElseThrow();
    assertThat(ready).isEqualTo(new TimerState(
        null, running.phase(), TimerState.Status.READY, running.durationMs(), null, null, null));

    service.start(GUEST_ID, null);
    TimerState restarted = store.find(GUEST_ID).orElseThrow();
    assertThat(restarted.intervalId()).isNotNull().isNotEqualTo(running.intervalId());
    assertThat(restarted.endsAt()).isEqualTo(NOW.plusMillis(FOCUS_DURATION_MS));

    assertThatThrownBy(() -> service.stop(GUEST_ID, running.intervalId()))
        .isInstanceOfSatisfying(ErrorException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TIMER_INTERVAL_MISMATCH));
    assertThat(store.find(GUEST_ID).orElseThrow().intervalId()).isEqualTo(restarted.intervalId());
  }

  @Test
  void concurrentStopRequestsCancelTheReservationOnlyOnce() throws Exception {
    TimerState running = storeRunningInterval(NOW.plusSeconds(60));
    CountDownLatch firstReachedCancellation = new CountDownLatch(1);
    CountDownLatch allowFirstToFinish = new CountDownLatch(1);
    CountDownLatch secondStarted = new CountDownLatch(1);
    holdCancellation(firstReachedCancellation, allowFirstToFinish);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.stop(GUEST_ID, running.intervalId()));
      try {
        assertThat(firstReachedCancellation.await(5, SECONDS)).isTrue();
        var second = executor.submit(() -> {
          secondStarted.countDown();
          assertThatThrownBy(() -> service.stop(GUEST_ID, running.intervalId()))
              .isInstanceOfSatisfying(ErrorException.class, exception ->
                  assertThat(exception.getErrorCode())
                      .isEqualTo(ErrorCode.TIMER_INTERVAL_MISMATCH));
        });
        assertThat(secondStarted.await(5, SECONDS)).isTrue();
        allowFirstToFinish.countDown();
        first.get(5, SECONDS);
        second.get(5, SECONDS);
      } finally {
        allowFirstToFinish.countDown();
      }
    }

    TimerState ready = store.find(GUEST_ID).orElseThrow();
    assertThat(ready.status()).isEqualTo(TimerState.Status.READY);
    assertThat(ready.phase()).isEqualTo(running.phase());
    verify(taskManager).cancelEndTask(GUEST_ID);
  }

  @Test
  void stopAtDeadlineAndOverlappingCallbackLeaveNaturalEndReady() throws Exception {
    TimerState running = storeRunningInterval(NOW);
    CountDownLatch stopReachedCancellation = new CountDownLatch(1);
    CountDownLatch allowStopToFinish = new CountDownLatch(1);
    CountDownLatch callbackStarted = new CountDownLatch(1);
    holdCancellation(stopReachedCancellation, allowStopToFinish);

    try (var executor = Executors.newFixedThreadPool(2)) {
      var stop = executor.submit(() ->
          assertThatThrownBy(() -> service.stop(GUEST_ID, running.intervalId()))
              .isInstanceOfSatisfying(ErrorException.class, exception ->
                  assertThat(exception.getErrorCode())
                      .isEqualTo(ErrorCode.TIMER_INTERVAL_MISMATCH)));
      try {
        assertThat(stopReachedCancellation.await(5, SECONDS)).isTrue();
        var callback = executor.submit(() -> {
          callbackStarted.countDown();
          taskService.finishIfDue(GUEST_ID, running.intervalId(), running.endsAt());
        });
        assertThat(callbackStarted.await(5, SECONDS)).isTrue();
        allowStopToFinish.countDown();
        stop.get(5, SECONDS);
        callback.get(5, SECONDS);
      } finally {
        allowStopToFinish.countDown();
      }
    }

    TimerState ready = store.find(GUEST_ID).orElseThrow();
    assertThat(ready.status()).isEqualTo(TimerState.Status.READY);
    assertThat(ready.phase()).isEqualTo(TimerState.Phase.SHORT_BREAK);
    assertThat(ready.intervalId()).isNull();
    verify(taskManager).cancelEndTask(GUEST_ID);
    verify(publisher).publishReady(GUEST_ID, ready);
    verifyNoMoreInteractions(publisher);
  }

  private void holdCancellation(CountDownLatch entered, CountDownLatch release) {
    doAnswer(invocation -> {
      entered.countDown();
      assertThat(release.await(5, SECONDS)).isTrue();
      return null;
    }).when(taskManager).cancelEndTask(GUEST_ID);
  }

  private TimerState storeRunningInterval(Instant endsAt) {
    return store.update(GUEST_ID, current -> new TimerState(
        UUID.randomUUID(), TimerState.Phase.FOCUS, TimerState.Status.RUNNING,
        FOCUS_DURATION_MS, endsAt.minusMillis(FOCUS_DURATION_MS), endsAt, null));
  }
}
