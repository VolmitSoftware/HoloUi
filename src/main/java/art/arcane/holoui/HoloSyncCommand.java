package art.arcane.holoui;

import art.arcane.holoui.editor.sync.EditorSyncService;
import art.arcane.holoui.editor.sync.EditorSyncSessionInfo;
import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

@Director(name = "sync", description = "Manage active web editor sync sessions",
    descriptionKey = "holoui.command.sync.root")
public final class HoloSyncCommand {
  public static final String PERMISSION = HoloCommand.ROOT_PERM + ".sync";

  @Director(name = "list", description = "List active editor sync sessions",
      descriptionKey = "holoui.command.sync.list")
  public void list(
      @Param(name = "sender", contextual = true, description = "Command sender context",
          descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    if (!checkPermission(sender)) {
      return;
    }
    List<EditorSyncSessionInfo> sessions = service().sessions();
    send(sender, HoloMessages.SYNC_LIST_HEADER,
        MessageArgs.builder().untrusted("count", sessions.size()).build());
    if (sessions.isEmpty()) {
      send(sender, HoloMessages.SYNC_LIST_EMPTY);
      return;
    }
    for (EditorSyncSessionInfo session : sessions) {
      send(sender, HoloMessages.SYNC_LIST_ENTRY, infoArguments(session));
    }
  }

  @Director(name = "status", description = "Show one editor sync session",
      descriptionKey = "holoui.command.sync.status")
  public void status(
      @Param(name = "session", description = "Editor sync session id",
          descriptionKey = "holoui.parameter.sync_session", customHandler = SessionIdHandler.class)
      String sessionId,
      @Param(name = "sender", contextual = true, description = "Command sender context",
          descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    if (!checkPermission(sender)) {
      return;
    }
    String resolved = resolveForSender(sender, sessionId);
    if (resolved == null) {
      return;
    }
    EditorSyncSessionInfo session = service().session(resolved).orElse(null);
    if (session == null) {
      unknown(sender, sessionId);
      return;
    }
    send(sender, HoloMessages.SYNC_STATUS, infoArguments(session));
  }

  @Director(name = "revoke", description = "Revoke an editor sync capability",
      descriptionKey = "holoui.command.sync.revoke")
  public void revoke(
      @Param(name = "session", description = "Editor sync session id",
          descriptionKey = "holoui.parameter.sync_session", customHandler = SessionIdHandler.class)
      String sessionId,
      @Param(name = "sender", contextual = true, description = "Command sender context",
          descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    if (!checkPermission(sender)) {
      return;
    }
    String resolved = resolveForSender(sender, sessionId);
    if (resolved == null) {
      return;
    }
    service().revoke(resolved).whenComplete((ignored, failure) -> runForSender(sender, () -> {
      if (failure == null) {
        send(sender, HoloMessages.SYNC_REVOKED,
            MessageArgs.builder().untrusted("session", EditorSyncService.abbreviate(resolved)).build());
      } else {
        reportFailure(sender, resolved, failure);
      }
    }));
  }

  @Director(name = "pull", aliases = {"poll"}, description = "Poll an editor sync session now",
      descriptionKey = "holoui.command.sync.pull")
  public void pull(
      @Param(name = "session", description = "Editor sync session id",
          descriptionKey = "holoui.parameter.sync_session", customHandler = SessionIdHandler.class)
      String sessionId,
      @Param(name = "sender", contextual = true, description = "Command sender context",
          descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    if (!checkPermission(sender)) {
      return;
    }
    String resolved = resolveForSender(sender, sessionId);
    if (resolved == null) {
      return;
    }
    service().pullNow(resolved).whenComplete((ignored, failure) -> runForSender(sender, () -> {
      if (failure == null) {
        send(sender, HoloMessages.SYNC_PULLED,
            MessageArgs.builder().untrusted("session", EditorSyncService.abbreviate(resolved)).build());
      } else {
        reportFailure(sender, resolved, failure);
      }
    }));
  }

  private boolean checkPermission(CommandSender sender) {
    if (sender.hasPermission(PERMISSION)) {
      return true;
    }
    send(sender, HoloMessages.PERMISSION_DENIED,
        MessageArgs.builder().untrusted("permission", PERMISSION).build());
    return false;
  }

  private MessageArgs infoArguments(EditorSyncSessionInfo session) {
    long seconds = Math.max(0L, Duration.between(Instant.now(), session.expiresAt()).toSeconds());
    return MessageArgs.builder()
        .untrusted("session", EditorSyncService.abbreviate(session.sessionId()))
        .untrusted("kind", session.kind().wireName())
        .untrusted("subject", session.subjectId())
        .untrusted("seconds", seconds)
        .untrusted("revision", session.lastPublicationRevision())
        .untrusted("pending", session.pendingStatus() == null ? "-" : session.pendingStatus())
        .build();
  }

  private void reportFailure(CommandSender sender, String sessionId, Throwable failure) {
    Throwable cause = rootCause(failure);
    if (cause instanceof NoSuchElementException) {
      unknown(sender, sessionId);
      return;
    }
    HoloUI.logExceptionStack(false, cause, "Editor sync command failed for session %s.",
        EditorSyncService.abbreviate(sessionId));
    send(sender, HoloMessages.SYNC_FAILED,
        MessageArgs.builder()
            .untrusted("session", EditorSyncService.abbreviate(sessionId))
            .untrusted("reason", safeMessage(cause))
            .build());
  }

  private void unknown(CommandSender sender, String sessionId) {
    send(sender, HoloMessages.SYNC_UNKNOWN,
        MessageArgs.builder().untrusted("session", EditorSyncService.abbreviate(sessionId)).build());
  }

  private String resolveForSender(CommandSender sender, String supplied) {
    try {
      Optional<String> resolved = resolveSessionId(supplied, service().sessions());
      if (resolved.isPresent()) {
        return resolved.get();
      }
      unknown(sender, supplied);
      return null;
    } catch (IllegalArgumentException failure) {
      send(sender, HoloMessages.SYNC_FAILED,
          MessageArgs.builder()
              .untrusted("session", EditorSyncService.abbreviate(supplied))
              .untrusted("reason", safeMessage(failure))
              .build());
      return null;
    }
  }

  static Optional<String> resolveSessionId(String supplied,
                                           List<EditorSyncSessionInfo> sessions) {
    String input = supplied == null ? "" : supplied.strip();
    for (EditorSyncSessionInfo session : sessions) {
      if (session.sessionId().equals(input)) {
        return Optional.of(input);
      }
    }
    if (input.length() < 12) {
      return Optional.empty();
    }
    String match = null;
    for (EditorSyncSessionInfo session : sessions) {
      if (!session.sessionId().startsWith(input)) {
        continue;
      }
      if (match != null) {
        throw new IllegalArgumentException("session prefix is ambiguous");
      }
      match = session.sessionId();
    }
    return Optional.ofNullable(match);
  }

  private void runForSender(CommandSender sender, Runnable task) {
    boolean accepted = sender instanceof Player player
        ? SchedulerUtils.runEntity(plugin(), player, task)
        : SchedulerUtils.runGlobal(plugin(), task);
    if (!accepted) {
      HoloUI.log(Level.WARNING, "Unable to schedule editor sync feedback for %s.", sender.getName());
    }
  }

  private void send(CommandSender sender, art.arcane.volmlib.util.localization.TextKey key) {
    send(sender, key, MessageArgs.empty());
  }

  private void send(CommandSender sender, art.arcane.volmlib.util.localization.TextKey key,
                    MessageArgs arguments) {
    sender.sendMessage(plugin().getLocalization().legacy(key, arguments));
  }

  private EditorSyncService service() {
    return plugin().getEditorSyncService();
  }

  private HoloUI plugin() {
    HoloUI plugin = HoloUI.INSTANCE;
    if (plugin == null) {
      throw new IllegalStateException("HoloUI is not enabled");
    }
    return plugin;
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static String safeMessage(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
  }

  public static final class SessionIdHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
      KList<String> ids = new KList<>();
      HoloUI plugin = HoloUI.INSTANCE;
      if (plugin == null || plugin.getEditorSyncService() == null) {
        return ids;
      }
      plugin.getEditorSyncService().sessions().stream()
          .map(EditorSyncSessionInfo::sessionId)
          .forEach(ids::add);
      return ids;
    }

    @Override
    public String toString(String value) {
      return value == null ? "" : value;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
      if (in == null || in.isBlank()) {
        throw new DirectorParsingException(
            HoloLocalization.globalText(HoloMessages.ERROR_SYNC_SESSION_REQUIRED));
      }
      return in.strip();
    }

    @Override
    public boolean supports(Class<?> type) {
      return type == String.class;
    }
  }
}
