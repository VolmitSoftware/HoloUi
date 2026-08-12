package art.arcane.holoui.editor.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class EditorSyncRelayClientTest {
  private HttpServer server;
  private ScheduledExecutorService clientExecutor;
  private ScheduledExecutorService deadlineExecutor;

  @After
  public void stopServer() {
    if (server != null) {
      server.stop(0);
    }
    if (clientExecutor != null) {
      clientExecutor.shutdownNow();
    }
    if (deadlineExecutor != null) {
      deadlineExecutor.shutdownNow();
    }
  }

  @Test
  public void createUsesTheExactVersionedRequestEnvelope() throws Exception {
    AtomicReference<JsonObject> requestBody = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<String> upgrade = new AtomicReference<>();
    AtomicReference<String> protocol = new AtomicReference<>();
    start(exchange -> {
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
      protocol.set(exchange.getProtocol());
      requestBody.set(JsonParser.parseString(new String(
          exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
      respond(exchange, 201, "application/json", """
          {"protocol":1,
           "sessionId":"session_id_123456789ab",
           "editorToken":"editor_token_123456789",
           "serverToken":"server_token_123456789",
           "expiresAt":"%s",
           "baseRevision":"%s"}
          """.formatted(Instant.now().plusSeconds(3590L), project().baseRevision()));
    });
    EditorSyncRelayClient client = client(1024 * 1024);

    EditorSyncRelayClient.RelayCreated created = client.create(endpoint(),
        "create_token_1234567890", project(), 3600).join();

    assertEquals("session_id_123456789ab", created.sessionId());
    assertEquals(java.util.Set.of("protocol", "expiresInSeconds", "snapshot"),
        requestBody.get().keySet());
    assertEquals(1, requestBody.get().get("protocol").getAsInt());
    assertEquals(3600, requestBody.get().get("expiresInSeconds").getAsInt());
    assertEquals("Bearer create_token_1234567890", authorization.get());
    assertEquals(null, upgrade.get());
    assertEquals("HTTP/1.1", protocol.get());
  }

  @Test
  public void anonymousCustomRelayCreateOmitsAuthorization() throws Exception {
    AtomicReference<String> authorization = new AtomicReference<>();
    start(exchange -> {
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      respond(exchange, 201, "application/json", """
          {"protocol":1,
           "sessionId":"session_id_123456789ab",
           "editorToken":"editor_token_123456789",
           "serverToken":"server_token_123456789",
           "expiresAt":"%s",
           "baseRevision":"%s"}
          """.formatted(Instant.now().plusSeconds(3590L), project().baseRevision()));
    });

    client(1024 * 1024).create(endpoint(), "", project(), 3600).join();

    assertEquals(null, authorization.get());
  }

  @Test
  public void publicationResponseLimitScalesWithConfiguredProjectSize() throws Exception {
    String large = "x".repeat(1200 * 1024);
    start(exchange -> respond(exchange, 200, "application/json",
        publicationResponse(large)));
    EditorSyncRelayClient client = client(2 * 1024 * 1024);

    EditorSyncPublication publication = client.publication(session()).join().orElseThrow();

    assertEquals(1L, publication.revision());
    assertEquals(large.length(), publication.snapshot().get("padding").getAsString().length());
  }

  @Test
  public void publicationStreamAbortsAboveProjectPlusEnvelopeLimit() throws Exception {
    start(exchange -> respond(exchange, 200, "application/json",
        publicationResponse("x".repeat(120 * 1024))));
    EditorSyncRelayClient client = client(32 * 1024);

    CompletionException failure = assertThrows(CompletionException.class,
        () -> client.publication(session()).join());
    assertTrue(failure.getCause() instanceof EditorSyncRelayException);
    assertTrue(failure.getCause().getMessage().contains("exceeds"));
  }

  @Test
  public void successfulRelayResponsesRequireJsonContentType() throws Exception {
    start(exchange -> respond(exchange, 200, "text/plain", publicationResponse("small")));
    EditorSyncRelayClient client = client(1024 * 1024);

    CompletionException failure = assertThrows(CompletionException.class,
        () -> client.publication(session()).join());
    assertTrue(failure.getCause() instanceof EditorSyncRelayException);
    assertTrue(failure.getCause().getMessage().contains("application/json"));
  }

  @Test(timeout = 3000L)
  public void responseDeadlineCancelsAStreamThatStallsAfterHeaders() throws Exception {
    ManualDeadlineExecutor deadlines = manualDeadlines();
    CountDownLatch headersSent = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    start(exchange -> {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, 0L);
      exchange.getResponseBody().flush();
      headersSent.countDown();
      try {
        release.await(2L, TimeUnit.SECONDS);
      } catch (InterruptedException interruption) {
        Thread.currentThread().interrupt();
      }
    });
    EditorSyncRelayClient client = client(1024 * 1024, Duration.ofSeconds(20L));

    CompletableFuture<Optional<EditorSyncPublication>> publication =
        client.publication(session());
    assertTrue(headersSent.await(1L, TimeUnit.SECONDS));
    deadlines.expire();
    try {
      CompletionException failure = assertThrows(CompletionException.class, publication::join);
      assertTrue(failure.getCause().toString(),
          failure.getCause() instanceof EditorSyncRelayException);
      assertTrue(failure.getCause().getMessage().contains("timed out"));
    } finally {
      release.countDown();
    }
  }

  @Test(timeout = 3000L)
  public void responseDeadlineIsNotResetByTrickledBodyBytes() throws Exception {
    ManualDeadlineExecutor deadlines = manualDeadlines();
    CountDownLatch firstByteSent = new CountDownLatch(1);
    CountDownLatch disconnected = new CountDownLatch(1);
    start(exchange -> {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, 0L);
      for (int index = 0; index < 100; index++) {
        try {
          exchange.getResponseBody().write(' ');
          exchange.getResponseBody().flush();
        } catch (IOException connectionClosed) {
          disconnected.countDown();
          return;
        }
        firstByteSent.countDown();
        try {
          Thread.sleep(25L);
        } catch (InterruptedException interruption) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    });
    EditorSyncRelayClient client = client(1024 * 1024, Duration.ofSeconds(20L));

    CompletableFuture<Optional<EditorSyncPublication>> publication =
        client.publication(session());
    assertTrue(firstByteSent.await(1L, TimeUnit.SECONDS));
    deadlines.expire();
    CompletionException failure = assertThrows(CompletionException.class, publication::join);
    assertTrue(failure.getCause().toString(),
        failure.getCause() instanceof EditorSyncRelayException);
    assertTrue(failure.getCause().getMessage().contains("timed out"));
    assertTrue(disconnected.await(1L, TimeUnit.SECONDS));
  }

  @Test
  public void closeRejectsNewRelayWorkWithoutStartingAnExchange() throws Exception {
    java.util.concurrent.atomic.AtomicInteger requests =
        new java.util.concurrent.atomic.AtomicInteger();
    start(exchange -> {
      requests.incrementAndGet();
      respond(exchange, 204, "application/json", "");
    });
    EditorSyncRelayClient client = client(1024 * 1024);
    client.close();

    CompletionException failure = assertThrows(CompletionException.class,
        () -> client.publication(session()).join());

    assertTrue(failure.getCause() instanceof EditorSyncRelayException);
    assertTrue(failure.getCause().getMessage().contains("shutting down"));
    assertEquals(0, requests.get());
  }

  @Test
  public void endpointParserRejectsCredentialsQueryAndFragments() {
    assertThrows(EditorSyncRelayException.class,
        () -> EditorSyncRelayClient.endpointUri("https://trusted@evil.example/v1", "/sessions"));
    assertThrows(EditorSyncRelayException.class,
        () -> EditorSyncRelayClient.endpointUri("https://relay.example/v1?q=x", "/sessions"));
    assertThrows(EditorSyncRelayException.class,
        () -> EditorSyncRelayClient.endpointUri("https://relay.example/v1#x", "/sessions"));
    assertEquals("https://relay.example/v1/sessions",
        EditorSyncRelayClient.endpointUri("https://relay.example/v1/", "/sessions").toString());
  }

  @Test
  public void publicationRequiresTheExactPendingEnvelopeAndSafeIntegerRevision() throws Exception {
    JsonObject response = JsonParser.parseString(publicationResponse("small")).getAsJsonObject();
    response.getAsJsonObject("publication").addProperty("revision", 1.5D);
    start(exchange -> respond(exchange, 200, "application/json", response.toString()));

    CompletionException fractional = assertThrows(CompletionException.class,
        () -> client(1024 * 1024).publication(session()).join());
    assertTrue(fractional.getCause() instanceof EditorSyncRelayException);

    server.stop(0);
    server = null;
    response.getAsJsonObject("publication").addProperty("revision", 1L);
    response.getAsJsonObject("publication").addProperty("extra", true);
    start(exchange -> respond(exchange, 200, "application/json", response.toString()));
    assertThrows(CompletionException.class,
        () -> client(1024 * 1024).publication(session()).join());
  }

  @Test
  public void createRejectsAmbiguousResponseFieldsAndMismatchedBase() throws Exception {
    start(exchange -> respond(exchange, 201, "application/json", """
        {"protocol":1,
         "sessionId":"session_id_123456789ab",
         "editorToken":"editor_token_123456789",
         "serverToken":"server_token_123456789",
         "expiresAt":"%s",
         "baseRevision":"sha256:%s",
         "extra":true}
        """.formatted(Instant.now().plusSeconds(3590L), "0".repeat(64))));

    CompletionException failure = assertThrows(CompletionException.class,
        () -> client(1024 * 1024).create(endpoint(), "", project(), 3600).join());
    assertTrue(failure.getCause() instanceof EditorSyncRelayException);
  }

  @Test
  public void acknowledgementRequiresTheOfficialMatchingResponseEnvelope() throws Exception {
    String baseRevision = project().baseRevision();
    start(exchange -> respond(exchange, 200, "application/json", """
        {"protocol":1,"baseRevision":"%s","publication":{
          "revision":1,"state":"rejected","ack":{
            "status":"rejected","message":"Invalid edit.","serverRevision":null,
            "acknowledgedAt":"2026-08-12T00:00:01Z"}}}
        """.formatted(baseRevision)));
    EditorSyncStoredSession session = session();

    client(1024 * 1024).acknowledge(session, 1L, "rejected", "Invalid edit.", null).join();

    server.stop(0);
    server = null;
    start(exchange -> respond(exchange, 204, "application/json", ""));
    assertThrows(CompletionException.class, () ->
        client(1024 * 1024).acknowledge(session(), 1L, "rejected", "Invalid edit.", null).join());
  }

  private EditorSyncRelayClient client(int maximumProjectBytes) {
    return client(maximumProjectBytes, Duration.ofSeconds(20L));
  }

  private EditorSyncRelayClient client(int maximumProjectBytes, Duration requestTimeout) {
    if (clientExecutor == null) {
      clientExecutor = Executors.newSingleThreadScheduledExecutor();
    }
    if (deadlineExecutor == null) {
      deadlineExecutor = Executors.newSingleThreadScheduledExecutor();
    }
    HttpClient http = EditorSyncRelayClient.productionHttpClient(clientExecutor);
    assertEquals(HttpClient.Version.HTTP_1_1, http.version());
    return new EditorSyncRelayClient(http, deadlineExecutor, () -> maximumProjectBytes,
        requestTimeout);
  }

  private ManualDeadlineExecutor manualDeadlines() {
    ManualDeadlineExecutor deadlines = new ManualDeadlineExecutor();
    deadlineExecutor = deadlines;
    return deadlines;
  }

  private EditorSyncStoredSession session() {
    return new EditorSyncStoredSession(new EditorSyncStoredSession.Capability(
        "session_id_123456789ab", "server_token_123456789", endpoint()),
        new EditorSyncStoredSession.Subject(EditorSyncKind.MENU, "fixture"),
        Instant.now().plusSeconds(3600L),
        0L, project().json(), null);
  }

  private EditorSyncProject project() {
    JsonObject project = new JsonObject();
    project.addProperty("format", EditorSyncJson.PROJECT_FORMAT);
    project.addProperty("version", 1);
    project.addProperty("kind", "menu");
    project.addProperty("subjectId", "fixture");
    JsonObject menu = new JsonObject();
    menu.addProperty("id", "fixture");
    menu.addProperty("json", "{\"components\":[]}");
    JsonArray menus = new JsonArray();
    menus.add(menu);
    project.add("menus", menus);
    project.add("images", new JsonArray());
    JsonObject constraints = new JsonObject();
    constraints.addProperty("subjectId", "fixture");
    JsonArray menuIds = new JsonArray();
    menuIds.add("fixture");
    constraints.add("menuIds", menuIds);
    constraints.add("imagePaths", new JsonArray());
    constraints.addProperty("newImagePrefix", "sync/menus/fixture/");
    constraints.addProperty("allowDeletes", false);
    project.add("constraints", constraints);
    project.add("warnings", new JsonArray());
    project.addProperty("baseRevision", EditorSyncJson.revision(project));
    return EditorSyncProject.validated(project, 1024 * 1024);
  }

  private String publicationResponse(String padding) {
    JsonObject snapshot = project().json();
    snapshot.addProperty("padding", padding);
    snapshot.addProperty("baseRevision", EditorSyncJson.revision(snapshot));
    JsonObject publication = new JsonObject();
    publication.addProperty("revision", 1L);
    publication.addProperty("baseRevision", project().baseRevision());
    publication.add("snapshot", snapshot);
    publication.addProperty("publishedAt", "2026-08-12T00:00:00Z");
    publication.addProperty("state", "pending");
    JsonObject response = new JsonObject();
    response.addProperty("protocol", 1);
    response.addProperty("sessionId", "session_id_123456789ab");
    response.add("publication", publication);
    return response.toString();
  }

  private void start(ExchangeHandler handler) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/sessions", exchange -> {
      try {
        handler.handle(exchange);
      } finally {
        exchange.close();
      }
    });
    server.start();
  }

  private String endpoint() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
  }

  private void respond(HttpExchange exchange, int status, String contentType, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  @FunctionalInterface
  private interface ExchangeHandler {
    void handle(HttpExchange exchange) throws IOException;
  }

  private static final class ManualDeadlineExecutor extends ScheduledThreadPoolExecutor {
    private final AtomicReference<ManualScheduledFuture> scheduled = new AtomicReference<>();

    private ManualDeadlineExecutor() {
      super(1);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      ManualScheduledFuture future = new ManualScheduledFuture(command);
      if (!scheduled.compareAndSet(null, future)) {
        throw new IllegalStateException("a deadline is already scheduled");
      }
      return future;
    }

    private void expire() {
      ManualScheduledFuture future = scheduled.get();
      if (future == null) {
        throw new IllegalStateException("no deadline is scheduled");
      }
      future.run();
    }
  }

  private static final class ManualScheduledFuture extends FutureTask<Void>
      implements ScheduledFuture<Void> {
    private ManualScheduledFuture(Runnable command) {
      super(command, null);
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return 0L;
    }

    @Override
    public int compareTo(Delayed other) {
      return 0;
    }
  }
}
