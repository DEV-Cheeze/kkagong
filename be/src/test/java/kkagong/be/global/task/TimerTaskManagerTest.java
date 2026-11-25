package kkagong.be.global.task;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.domain.timer.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class TimerTaskManagerTest {

  private static final long GUEST_ID = 1L;
  private static final Instant ENDS_AT = Instant.parse("2026-11-25T01:25:00Z");

  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private final TaskService taskService = mock(TaskService.class);
  private final TimerTaskManager manager = new TimerTaskManager(scheduler, taskService);
  private final TimerState running = new TimerState(
      UUID.randomUUID(), TimerState.Phase.FOCUS, TimerState.Status.RUNNING,
      25 * 60_000L, ENDS_AT.minusSeconds(25 * 60), ENDS_AT, null);

  @Test
  void completionRemovesTheReservation() {
    ScheduledFuture<?> future = mock(ScheduledFuture.class);
    doReturn(future).when(scheduler).schedule(any(Runnable.class), eq(ENDS_AT));
    manager.scheduleEndTask(GUEST_ID, running, running.intervalId());

    ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(callback.capture(), eq(ENDS_AT));
    callback.getValue().run();

    verify(taskService).finishIfDue(GUEST_ID, running.intervalId(), ENDS_AT);
    manager.cancelEndTask(GUEST_ID);
    verifyNoInteractions(future);
  }

  @Test
  void completionAlsoRemovesTheReservationWhenCallbackThrows() {
    ScheduledFuture<?> future = mock(ScheduledFuture.class);
    doReturn(future).when(scheduler).schedule(any(Runnable.class), eq(ENDS_AT));
    IllegalStateException failure = new IllegalStateException("finish failed");
    doThrow(failure).when(taskService).finishIfDue(GUEST_ID, running.intervalId(), ENDS_AT);
    manager.scheduleEndTask(GUEST_ID, running, running.intervalId());

    ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(callback.capture(), eq(ENDS_AT));
    assertThatThrownBy(() -> callback.getValue().run()).isSameAs(failure);

    manager.cancelEndTask(GUEST_ID);
    verifyNoInteractions(future);
  }

  @Test
  void oldCallbackCannotRemoveNewReservationForSameInterval() {
    ScheduledFuture<?> oldFuture = mock(ScheduledFuture.class);
    ScheduledFuture<?> newFuture = mock(ScheduledFuture.class);
    doReturn(oldFuture, newFuture).when(scheduler).schedule(any(Runnable.class), eq(ENDS_AT));
    manager.scheduleEndTask(GUEST_ID, running, running.intervalId());
    manager.scheduleEndTask(GUEST_ID, running, running.intervalId());

    ArgumentCaptor<Runnable> callbacks = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler, times(2)).schedule(callbacks.capture(), eq(ENDS_AT));
    verify(oldFuture).cancel(false);
    callbacks.getAllValues().getFirst().run();

    manager.cancelEndTask(GUEST_ID);
    verify(newFuture).cancel(false);
  }
}
