package art.arcane.holoui.editor.sync;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

final class EditorSyncRelayClient implements EditorSyncRelayGateway {
  private static final int SMALL_RESPONSE_BYTES = 64 * 1024;
  private static final int RESPONSE_ENVELOPE_BYTES = 64 * 1024;
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20L);

  private final HttpClient http;
  private final ScheduledExecutorService deadlineExecutor;
  private final IntSupplier maximumProjectBytes;
  private final Duration requestTimeout;
  private final Set<CompletableFuture<?>> activeRequests;
  private final AtomicBoolean closed;

  EditorSyncRelayClient(HttpClient http, ScheduledExecutorService deadlineExecutor,
                        IntSupplier maximumProjectBytes) {
    this(http, deadlineExecutor, maximumProjectBytes, REQUEST_TIMEOUT);
  }

  EditorSyncRelayClient(HttpClient http, ScheduledExecutorService deadlineExecutor,
                        IntSupplier maximumProjectBytes, Duration requestTimeout) {
    this.http = Objects.requireNonNull(http, "http");
    this.deadlineExecutor = Objects.requireNonNull(deadlineExecutor, "deadlineExecutor");
    this.maximumProjectBytes = Objects.requireNonNull(maximumProjectBytes, "maximumProjectBytes");
    this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    if (requestTimeout.isZero() || requestTimeout.isNegative()) {
      throw new IllegalArgumentException("requestTimeout must be positive");
    }
    this.activeRequests = java.util.concurrent.ConcurrentHashMap.newKeySet();
    this.closed = new AtomicBoolean();
  }

  static HttpClient productionHttpClient(Executor executor) {
    return HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10L))
        .followRedirects(HttpClient.Redirect.NEVER)
        .executor(Objects.requireNonNull(executor, "executor"))
        .build();
  }

  @Override
  public CompletableFuture<RelayCreated> create(String endpoint, String createToken,
                                         EditorSyncProject project,
                                         int expiresInSeconds) {
    Instant requestedAt = Instant.now();
    JsonObject request = new JsonObject();
    request.addProperty("protocol", EditorSyncJson.PROTOCOL_VERSION);
    request.addProperty("expiresInSeconds", expiresInSeconds);
    request.add("snapshot", project.json());
    String token = createToken == null || createToken.isBlank() ? null : createToken;
    if (token != null && !EditorSyncStoredSession.validCapability(token)) {
      return CompletableFuture.failedFuture(
          new EditorSyncRelayException("invalid relay create token"));
    }
    return sendJson("POST", endpointUri(endpoint, "/sessions"), token, request)
        .thenApply(response -> {
          requireStatus(response, 201);
          JsonObject body = parseObject(response.body());
          requireExactKeys(body, Set.of("protocol", "sessionId", "editorToken", "serverToken",
              "expiresAt", "baseRevision"), "create response");
          requireProtocol(body);
          String sessionId = EditorSyncJson.requireString(body, "sessionId");
          String editorToken = EditorSyncJson.requireString(body, "editorToken");
          String serverToken = EditorSyncJson.requireString(body, "serverToken");
          if (!EditorSyncStoredSession.validCapability(sessionId)
              || !EditorSyncStoredSession.validCapability(editorToken)
              || !EditorSyncStoredSession.validCapability(serverToken)) {
            throw new EditorSyncRelayException("relay returned an invalid capability");
          }
          try {
            Instant expiresAt = Instant.parse(EditorSyncJson.requireString(body, "expiresAt"));
            String baseRevision = EditorSyncJson.requireString(body, "baseRevision");
            Instant earliest = requestedAt.minusSeconds(30L);
            Instant latest = requestedAt.plusSeconds(expiresInSeconds + 60L);
            if (!baseRevision.equals(project.baseRevision()) || !expiresAt.isAfter(earliest)
                || expiresAt.isAfter(latest)) {
              throw new EditorSyncRelayException("relay returned an invalid session base or expiry");
            }
            return new RelayCreated(sessionId, editorToken, serverToken, expiresAt);
          } catch (DateTimeParseException exception) {
            throw new EditorSyncRelayException("relay returned an invalid expiry", exception);
          }
        });
  }

  @Override
  public CompletableFuture<Optional<EditorSyncPublication>> publication(
      EditorSyncStoredSession session) {
    URI uri = endpointUri(session.endpoint(), "/sessions/" + encode(session.sessionId())
        + "/publication?after=" + session.lastPublicationRevision());
    return send("GET", uri, session.serverToken(), null, publicationResponseLimit()).thenApply(response -> {
      if (response.statusCode() == 204) {
        return Optional.empty();
      }
      requireStatus(response, 200);
      JsonObject body = parseObject(response.body());
      requireExactKeys(body, Set.of("protocol", "sessionId", "publication"),
          "publication response");
      requireProtocol(body);
      String responseSessionId = EditorSyncJson.requireString(body, "sessionId");
      if (!responseSessionId.equals(session.sessionId())) {
        throw new EditorSyncRelayException("relay returned a publication for another session");
      }
      JsonObject publication = EditorSyncJson.requireObject(body, "publication");
      requireExactKeys(publication, Set.of("revision", "baseRevision", "snapshot",
          "publishedAt", "state"), "publication");
      long revision = requirePositiveSafeLong(publication, "revision");
      String baseRevision = EditorSyncJson.requireString(publication, "baseRevision");
      if (!baseRevision.matches("sha256:[0-9a-f]{64}")
          || !"pending".equals(EditorSyncJson.requireString(publication, "state"))) {
        throw new EditorSyncRelayException("relay returned an invalid pending publication");
      }
      requireInstant(publication, "publishedAt");
      JsonObject snapshot = EditorSyncJson.requireObject(publication, "snapshot");
      EditorSyncProject.validated(snapshot, maximumProjectBytes.getAsInt());
      return Optional.of(new EditorSyncPublication(revision, baseRevision, snapshot));
    });
  }

  @Override
  public CompletableFuture<Void> acknowledge(EditorSyncStoredSession session, long revision,
                                      String status, String message, EditorSyncProject serverProject) {
    JsonObject request = new JsonObject();
    request.addProperty("protocol", EditorSyncJson.PROTOCOL_VERSION);
    request.addProperty("status", status);
    request.addProperty("message", message);
    if (serverProject != null) {
      request.addProperty("serverRevision", serverProject.baseRevision());
      request.add("snapshot", serverProject.json());
    }
    URI uri = endpointUri(session.endpoint(), "/sessions/" + encode(session.sessionId())
        + "/publication/" + revision + "/ack");
    return sendJson("POST", uri, session.serverToken(), request).thenApply(response -> {
      requireStatus(response, 200);
      validateAcknowledgement(response.body(), session, revision, status, message, serverProject);
      return null;
    });
  }

  @Override
  public CompletableFuture<Void> revoke(EditorSyncStoredSession session) {
    URI uri = endpointUri(session.endpoint(), "/sessions/" + encode(session.sessionId()));
    return send("DELETE", uri, session.serverToken(), null, SMALL_RESPONSE_BYTES).thenApply(response -> {
      requireAnyStatus(response, 200, 204, 404, 410);
      return null;
    });
  }

  @Override
  public void cancelActiveRequests() {
    for (CompletableFuture<?> request : List.copyOf(activeRequests)) {
      request.cancel(true);
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    cancelActiveRequests();
    deadlineExecutor.shutdownNow();
  }

  private CompletableFuture<RelayResponse> sendJson(String method, URI uri, String token,
                                                    JsonObject body) {
    return send(method, uri, token, EditorSyncJson.canonical(body), SMALL_RESPONSE_BYTES);
  }

  private CompletableFuture<RelayResponse> send(String method, URI uri, String token, String body,
                                                int maximumResponseBytes) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(
          new EditorSyncRelayException("relay client is shutting down"));
    }
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
        .timeout(requestTimeout)
        .header("Accept", "application/json")
        .header("User-Agent", "HoloUi-Editor-Sync/1");
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body == null) {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      builder.header("Content-Type", "application/json; charset=utf-8")
          .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    }
    BoundedBodyHandler bodyHandler = new BoundedBodyHandler(maximumResponseBytes);
    CompletableFuture<HttpResponse<byte[]>> request =
        http.sendAsync(builder.build(), bodyHandler);
    activeRequests.add(request);
    if (closed.get()) {
      request.cancel(true);
      activeRequests.remove(request);
      return CompletableFuture.failedFuture(
          new EditorSyncRelayException("relay client is shutting down"));
    }
    CompletableFuture<RelayResponse> result = new CompletableFuture<>();
    bodyHandler.failure().thenAccept(result::completeExceptionally);
    ScheduledFuture<?> deadline;
    try {
      deadline = deadlineExecutor.schedule(() -> {
        if (result.completeExceptionally(
            new EditorSyncRelayException("relay response timed out"))) {
          bodyHandler.timeout();
          request.cancel(true);
        }
      }, requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (RejectedExecutionException failure) {
      request.cancel(true);
      activeRequests.remove(request);
      return CompletableFuture.failedFuture(
          new EditorSyncRelayException("relay client is shutting down", failure));
    }
    request.whenComplete((response, failure) -> {
      activeRequests.remove(request);
      if (failure != null) {
        result.completeExceptionally(failure);
        return;
      }
      try {
        result.complete(readResponse(response));
      } catch (RuntimeException responseFailure) {
        result.completeExceptionally(responseFailure);
      }
    });
    result.whenComplete((ignored, failure) -> deadline.cancel(false));
    return result;
  }

  private RelayResponse readResponse(HttpResponse<byte[]> response) {
    String contentType = response.headers().firstValue("Content-Type").orElse("");
    if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")
        && response.statusCode() != 204) {
      throw new EditorSyncRelayException("relay response must use application/json");
    }
    return new RelayResponse(response.statusCode(),
        new String(response.body(), StandardCharsets.UTF_8));
  }

  private int publicationResponseLimit() {
    int projectBytes = Math.max(1, maximumProjectBytes.getAsInt());
    return projectBytes > Integer.MAX_VALUE - RESPONSE_ENVELOPE_BYTES
        ? Integer.MAX_VALUE
        : projectBytes + RESPONSE_ENVELOPE_BYTES;
  }

  private static final class BoundedBodyHandler implements HttpResponse.BodyHandler<byte[]> {
    private final int maximumResponseBytes;
    private final AtomicBoolean timedOut;
    private final AtomicReference<BoundedBodySubscriber> subscriber;
    private final CompletableFuture<EditorSyncRelayException> failure;

    private BoundedBodyHandler(int maximumResponseBytes) {
      if (maximumResponseBytes < 1) {
        throw new IllegalArgumentException("maximumResponseBytes must be positive");
      }
      this.maximumResponseBytes = maximumResponseBytes;
      this.timedOut = new AtomicBoolean();
      this.subscriber = new AtomicReference<>();
      this.failure = new CompletableFuture<>();
    }

    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo responseInfo) {
      BoundedBodySubscriber created =
          new BoundedBodySubscriber(maximumResponseBytes, failure);
      subscriber.set(created);
      if (timedOut.get()) {
        created.timeout();
      }
      return created;
    }

    private void timeout() {
      timedOut.set(true);
      BoundedBodySubscriber active = subscriber.get();
      if (active != null) {
        active.timeout();
      }
    }

    private CompletableFuture<EditorSyncRelayException> failure() {
      return failure;
    }
  }

  private static final class BoundedBodySubscriber
      implements HttpResponse.BodySubscriber<byte[]> {
    private final int maximumResponseBytes;
    private final ByteArrayOutputStream output;
    private final CompletableFuture<byte[]> body;
    private final CompletableFuture<EditorSyncRelayException> reportedFailure;
    private final AtomicReference<Flow.Subscription> subscription;
    private int receivedBytes;

    private BoundedBodySubscriber(
        int maximumResponseBytes,
        CompletableFuture<EditorSyncRelayException> reportedFailure) {
      this.maximumResponseBytes = maximumResponseBytes;
      this.output = new ByteArrayOutputStream(Math.min(maximumResponseBytes, 8192));
      this.body = new CompletableFuture<>();
      this.reportedFailure = reportedFailure;
      this.subscription = new AtomicReference<>();
    }

    @Override
    public CompletionStage<byte[]> getBody() {
      return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription newSubscription) {
      Objects.requireNonNull(newSubscription, "newSubscription");
      if (!subscription.compareAndSet(null, newSubscription) || body.isDone()) {
        newSubscription.cancel();
        return;
      }
      newSubscription.request(1L);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      if (body.isDone()) {
        return;
      }
      long incomingBytes = 0L;
      for (ByteBuffer buffer : buffers) {
        incomingBytes += buffer.remaining();
      }
      if (incomingBytes > maximumResponseBytes - (long) receivedBytes) {
        fail(new EditorSyncRelayException(
            "relay response exceeds " + maximumResponseBytes + " bytes"));
        return;
      }
      for (ByteBuffer buffer : buffers) {
        int length = buffer.remaining();
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        output.write(bytes, 0, length);
        receivedBytes += length;
      }
      Flow.Subscription active = subscription.get();
      if (active != null && !body.isDone()) {
        active.request(1L);
      }
    }

    @Override
    public void onError(Throwable failure) {
      body.completeExceptionally(failure);
    }

    @Override
    public void onComplete() {
      body.complete(output.toByteArray());
    }

    private void timeout() {
      fail(new EditorSyncRelayException("relay response timed out"));
    }

    private void fail(Throwable failure) {
      if (failure instanceof EditorSyncRelayException relayFailure) {
        reportedFailure.complete(relayFailure);
      }
      if (!body.completeExceptionally(failure)) {
        return;
      }
      Flow.Subscription active = subscription.get();
      if (active != null) {
        active.cancel();
      }
    }
  }

  static URI endpointUri(String endpoint, String suffix) {
    String normalized = endpoint;
    while (normalized != null && normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized == null || normalized.length() > 1024) {
      throw new EditorSyncRelayException("relay endpoint exceeds the length limit");
    }
    URI base;
    try {
      base = URI.create(normalized);
    } catch (IllegalArgumentException failure) {
      throw new EditorSyncRelayException("invalid relay endpoint", failure);
    }
    String scheme = base.getScheme();
    String host = base.getHost();
    boolean https = "https".equalsIgnoreCase(scheme);
    boolean loopbackHttp = "http".equalsIgnoreCase(scheme) && isLoopbackHost(host);
    String path = base.getPath();
    if ((!https && !loopbackHttp)
        || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
        || base.getFragment() != null || path == null || !path.endsWith("/v1")
        || path.contains("//") || path.contains("/../") || path.contains("/./")) {
      throw new EditorSyncRelayException(
          "relay endpoint must be versioned HTTPS or loopback HTTP without credentials or query data");
    }
    return URI.create(normalized + suffix);
  }

  static boolean validEndpoint(String endpoint) {
    try {
      endpointUri(endpoint, "/sessions");
      return true;
    } catch (EditorSyncRelayException failure) {
      return false;
    }
  }

  private static boolean isLoopbackHost(String host) {
    return host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
        || host.equals("::1") || host.equals("[::1]"));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static JsonObject parseObject(String body) {
    try {
      JsonElement parsed = JsonParser.parseString(body);
      if (!parsed.isJsonObject()) {
        throw new EditorSyncRelayException("relay response must be a JSON object");
      }
      return parsed.getAsJsonObject();
    } catch (RuntimeException failure) {
      if (failure instanceof EditorSyncRelayException relayFailure) {
        throw relayFailure;
      }
      throw new EditorSyncRelayException("relay returned malformed JSON", failure);
    }
  }

  private static void requireProtocol(JsonObject body) {
    if (EditorSyncJson.requireInt(body, "protocol") != EditorSyncJson.PROTOCOL_VERSION) {
      throw new EditorSyncRelayException("relay protocol version does not match");
    }
  }

  private static long requirePositiveSafeLong(JsonObject object, String field) {
    JsonElement value = object.get(field);
    try {
      if (value == null || !value.isJsonPrimitive()
          || !value.getAsJsonPrimitive().isNumber()) {
        throw new IllegalArgumentException();
      }
      java.math.BigDecimal decimal = value.getAsBigDecimal().stripTrailingZeros();
      if (decimal.scale() > 0) {
        throw new IllegalArgumentException();
      }
      long number = decimal.longValueExact();
      if (number < 1L || number > EditorSyncJson.MAX_SAFE_INTEGER) {
        throw new IllegalArgumentException();
      }
      return number;
    } catch (RuntimeException failure) {
      throw new EditorSyncRelayException(field + " must be a positive integer", failure);
    }
  }

  private static void validateAcknowledgement(
      String source, EditorSyncStoredSession session, long revision,
      String status, String message, EditorSyncProject serverProject) {
    JsonObject body = parseObject(source);
    requireExactKeys(body, Set.of("protocol", "baseRevision", "publication"),
        "acknowledgement response");
    requireProtocol(body);
    String expectedBase = serverProject == null
        ? session.baseRevision()
        : serverProject.baseRevision();
    if (!expectedBase.equals(EditorSyncJson.requireString(body, "baseRevision"))) {
      throw new EditorSyncRelayException("relay acknowledgement baseRevision does not match");
    }
    JsonObject publication = EditorSyncJson.requireObject(body, "publication");
    requireExactKeys(publication, Set.of("revision", "state", "ack"),
        "acknowledged publication");
    if (requirePositiveSafeLong(publication, "revision") != revision
        || !status.equals(EditorSyncJson.requireString(publication, "state"))) {
      throw new EditorSyncRelayException("relay acknowledged another publication state");
    }
    JsonObject acknowledgement = EditorSyncJson.requireObject(publication, "ack");
    requireExactKeys(acknowledgement,
        Set.of("status", "message", "serverRevision", "acknowledgedAt"),
        "publication acknowledgement");
    JsonElement rawServerRevision = acknowledgement.get("serverRevision");
    String actualServerRevision = rawServerRevision == null || rawServerRevision.isJsonNull()
        ? null
        : rawServerRevision.getAsString();
    String expectedServerRevision = serverProject == null ? null : serverProject.baseRevision();
    if (!status.equals(EditorSyncJson.requireString(acknowledgement, "status"))
        || !message.equals(EditorSyncJson.requireString(acknowledgement, "message"))
        || !Objects.equals(expectedServerRevision, actualServerRevision)) {
      throw new EditorSyncRelayException("relay acknowledgement content does not match");
    }
    requireInstant(acknowledgement, "acknowledgedAt");
  }

  private static Instant requireInstant(JsonObject object, String field) {
    try {
      return Instant.parse(EditorSyncJson.requireString(object, field));
    } catch (DateTimeParseException failure) {
      throw new EditorSyncRelayException("relay returned an invalid " + field, failure);
    }
  }

  private static void requireExactKeys(JsonObject object, Set<String> expected, String label) {
    if (!object.keySet().equals(expected)) {
      throw new EditorSyncRelayException(label + " contains missing or unsupported fields");
    }
  }

  private static void requireStatus(RelayResponse response, int expected) {
    if (response.statusCode() != expected) {
      throw new EditorSyncRelayException("relay returned HTTP " + response.statusCode());
    }
  }

  private static void requireAnyStatus(RelayResponse response, int... expected) {
    for (int candidate : expected) {
      if (response.statusCode() == candidate) {
        return;
      }
    }
    throw new EditorSyncRelayException("relay returned HTTP " + response.statusCode());
  }

  record RelayCreated(String sessionId, String editorToken, String serverToken, Instant expiresAt) {
  }

  private record RelayResponse(int statusCode, String body) {
  }
}
