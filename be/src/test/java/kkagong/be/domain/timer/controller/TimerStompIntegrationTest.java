package kkagong.be.domain.timer.controller;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.domain.timer.service.InMemoryTimerStore;
import kkagong.be.domain.timer.service.TimerStatePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.AbstractMessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = Replace.ANY)
@ActiveProfiles("test")
class TimerStompIntegrationTest {

  private static final String TIMER_SUBSCRIPTION = "/user/queue/timer";
  private static final long FOCUS_DURATION_MS = 2_000;

  private final HttpClient http = HttpClient.newHttpClient();
  private final WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
  private final List<StompSession> sessions = new ArrayList<>();
  private final BlockingQueue<Message<?>> deniedMessages = new LinkedBlockingQueue<>();
  private final ChannelInterceptor rejectionProbe = new ChannelInterceptor() {
    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent,
        Exception exception) {
      if (exception instanceof AccessDeniedException) {
        deniedMessages.add(message);
      }
    }
  };

  @LocalServerPort
  private int port;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private InMemoryTimerStore timerStore;

  @MockitoSpyBean
  private TimerStatePublisher publisher;

  @Autowired
  @Qualifier("clientInboundChannel")
  private AbstractMessageChannel inboundChannel;

  @BeforeEach
  void observeAuthorizationRejections() {
    inboundChannel.addInterceptor(0, rejectionProbe);
  }

  @AfterEach
  void disconnectSessions() {
    inboundChannel.removeInterceptor(rejectionProbe);
    sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    stomp.stop();
  }

  @Test
  void handshakeRequiresGuestCookie() {
    var handshake = http.newWebSocketBuilder().subprotocols("v12.stomp")
        .buildAsync(URI.create(webSocketUrl()), new WebSocket.Listener() {})
        .thenAccept(WebSocket::abort);

    assertThatThrownBy(() -> handshake.get(5, SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(WebSocketHandshakeException.class)
        .satisfies(exception -> assertThat(
            ((WebSocketHandshakeException) exception.getCause()).getResponse().statusCode())
            .isEqualTo(401));
  }

  @Test
  void guestCannotSubscribeDirectlyToBrokerQueue() throws Exception {
    StompSession session = connect(signUp("구독검증"));

    session.subscribe("/queue/timer", new TimerEvents());

    assertDenied(SimpMessageType.SUBSCRIBE, "/queue/timer");
  }

  @ParameterizedTest
  @ValueSource(strings = {TIMER_SUBSCRIPTION, "/queue/timer"})
  void guestCannotSendToTimerNotificationDestinations(String destination) throws Exception {
    StompSession session = connect(signUp("발행검증"));

    session.send(destination, "{}".getBytes(StandardCharsets.UTF_8));

    assertDenied(SimpMessageType.MESSAGE, destination);
  }

  @Test
  void scheduledNaturalEndPublishesReadyOnceToEachTabOfTheSameGuest() throws Exception {
    String cookie = signUp("자연종료게스트");
    TimerEvents firstTab = subscribeToTimer(cookie);
    TimerEvents secondTab = subscribeToTimer(cookie);
    TimerEvents otherGuest = subscribeToTimer(signUp("다른타이머게스트"));
    Long guestId = guestIdOf("자연종료게스트");
    timerStore.update(guestId, current -> new TimerState(null, TimerState.Phase.FOCUS,
        TimerState.Status.READY, FOCUS_DURATION_MS, null, null, null));

    assertThat(post("/api/v1/timer/actions", "{\"action\":\"START\"}", cookie).statusCode())
        .isEqualTo(200);

    JsonNode running = firstTab.nextState();
    assertThat(running.path("status").asString()).isEqualTo("RUNNING");
    assertThat(secondTab.nextState()).isEqualTo(running);

    JsonNode ready = firstTab.nextState();
    assertThat(ready.path("status").asString()).isEqualTo("READY");
    assertThat(ready.path("phase").asString()).isEqualTo("SHORT_BREAK");
    assertThat(secondTab.nextState()).isEqualTo(ready);
    assertThat(timerStore.find(guestId).orElseThrow().status()).isEqualTo(TimerState.Status.READY);

    assertThat(firstTab.states.poll(300, MILLISECONDS)).isNull();
    assertThat(secondTab.states.poll(300, MILLISECONDS)).isNull();
    assertThat(otherGuest.states.poll(300, MILLISECONDS)).isNull();
  }

  @Test
  void overlappingPauseAndResumeNotifyBothTabsInStoredOrder() throws Exception {
    String nickname = "재개경합게스트";
    String cookie = signUp(nickname);
    Long guestId = guestIdOf(nickname);
    TimerEvents firstTab = subscribeToTimer(cookie);
    TimerEvents secondTab = subscribeToTimer(cookie);

    assertThat(post("/api/v1/timer/actions", "{\"action\":\"START\"}", cookie).statusCode())
        .isEqualTo(200);
    JsonNode running = firstTab.nextState();
    assertThat(secondTab.nextState()).isEqualTo(running);
    assertThat(running.path("status").asString()).isEqualTo("RUNNING");
    String intervalId = running.path("intervalId").asString();

    CountDownLatch pauseReachedPublisher = new CountDownLatch(1);
    CountDownLatch publishPause = new CountDownLatch(1);
    doAnswer(invocation -> {
      pauseReachedPublisher.countDown();
      assertThat(publishPause.await(10, SECONDS)).isTrue();
      return invocation.callRealMethod();
    }).when(publisher).publishPaused(eq(guestId), any(TimerState.class));

    JsonNode receivedWhilePausedPublicationWaited;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var pause = executor.submit(() -> postTimerAction("PAUSE", intervalId, cookie));
      Future<HttpResponse<String>> resume;
      try {
        assertThat(pauseReachedPublisher.await(5, SECONDS)).isTrue();
        assertThat(timerStore.find(guestId).orElseThrow().status()).isEqualTo(TimerState.Status.PAUSED);
        resume = executor.submit(() -> postTimerAction("RESUME", intervalId, cookie));
        // Let an unordered RESUME reach a tab before releasing the earlier PAUSE publication.
        receivedWhilePausedPublicationWaited = firstTab.states.poll(2, SECONDS);
      } finally {
        publishPause.countDown();
      }
      assertThat(pause.get(5, SECONDS).statusCode()).isEqualTo(200);
      assertThat(resume.get(5, SECONDS).statusCode()).isEqualTo(200);
    }

    var firstTabUpdates = List.of(
        receivedWhilePausedPublicationWaited != null ? receivedWhilePausedPublicationWaited : firstTab.nextState(),
        firstTab.nextState());
    var secondTabUpdates = List.of(secondTab.nextState(), secondTab.nextState());
    assertThat(timerStore.find(guestId).orElseThrow().status()).isEqualTo(TimerState.Status.RUNNING);
    assertThat(List.of(firstTabUpdates, secondTabUpdates))
        .as("notification order and final state in both tabs")
        .map(updates -> updates.stream().map(state -> state.path("status").asString()).toList())
        .containsExactly(List.of("PAUSED", "RUNNING"), List.of("PAUSED", "RUNNING"));
  }

  private TimerEvents subscribeToTimer(String cookie) throws Exception {
    StompSession session = connect(cookie);
    TimerEvents events = new TimerEvents();
    session.subscribe(TIMER_SUBSCRIPTION, events);
    // A same-session probe confirms the broker has processed the subscription.
    session.send("/app/timer/test", new byte[0]);
    events.subscribed.get(5, SECONDS);
    return events;
  }

  private StompSession connect(String cookie) throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.add(HttpHeaders.COOKIE, cookie);
    StompSession session = stomp.connectAsync(webSocketUrl(), headers, new StompHeaders(),
            new StompSessionHandlerAdapter() {})
        .get(5, SECONDS);
    sessions.add(session);
    return session;
  }

  private String webSocketUrl() {
    return "ws://localhost:" + port + "/ws-stomp";
  }

  private String signUp(String nickname) throws Exception {
    HttpResponse<String> response = post("/api/v1/guests/signup",
        "{\"nickname\":\"%s\"}".formatted(nickname), null);
    assertThat(response.statusCode()).isEqualTo(204);
    return response.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
  }

  private Long guestIdOf(String nickname) {
    return jdbcTemplate.queryForObject("select guest_id from guest where nickname = ?",
        Long.class, nickname);
  }

  private HttpResponse<String> postTimerAction(String action, String intervalId, String cookie)
      throws Exception {
    return post("/api/v1/timer/actions", """
        {"action":"%s","intervalId":"%s"}
        """.formatted(action, intervalId), cookie);
  }

  private HttpResponse<String> post(String path, String body, String cookie) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .timeout(Duration.ofSeconds(10))
        .header(HttpHeaders.CONTENT_TYPE, "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
    if (cookie != null) {
      request.header(HttpHeaders.COOKIE, cookie);
    }
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private void assertDenied(SimpMessageType type, String destination) throws InterruptedException {
    Message<?> rejected = deniedMessages.poll(5, SECONDS);
    assertThat(rejected).as("denied STOMP message").isNotNull();
    assertThat(SimpMessageHeaderAccessor.getMessageType(rejected.getHeaders())).isEqualTo(type);
    assertThat(SimpMessageHeaderAccessor.getDestination(rejected.getHeaders()))
        .isEqualTo(destination);
  }

  private class TimerEvents implements StompFrameHandler {

    private final CompletableFuture<Void> subscribed = new CompletableFuture<>();
    private final BlockingQueue<JsonNode> states = new LinkedBlockingQueue<>();

    @Override
    public Type getPayloadType(StompHeaders headers) {
      return byte[].class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
      JsonNode event = objectMapper.readTree((byte[]) payload);
      switch (event.path("type").asString()) {
        case "TIMER_TEST" -> subscribed.complete(null);
        case "TIMER_STATE_CHANGED" -> states.add(event.path("data"));
        default -> throw new AssertionError("Unexpected timer event: " + event);
      }
    }

    JsonNode nextState() throws InterruptedException {
      JsonNode state = states.poll(5, SECONDS);
      assertThat(state).as("timer state notification").isNotNull();
      return state;
    }
  }
}
