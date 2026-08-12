package art.arcane.holoui;

import art.arcane.holoui.importer.LegacyHologramImportService;
import art.arcane.holoui.importer.LegacyImportApplyEntry;
import art.arcane.holoui.importer.LegacyImportApplyResult;
import art.arcane.holoui.importer.LegacyImportBusyException;
import art.arcane.holoui.importer.LegacyImportCandidate;
import art.arcane.holoui.importer.LegacyImportIssue;
import art.arcane.holoui.importer.LegacyImportPlan;
import art.arcane.holoui.importer.LegacyImportSource;
import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

@Director(name = "import", description = "Migrate legacy holograms without modifying source files",
    descriptionKey = "holoui.command.import.root")
public final class HoloImportCommand {
  public static final String PERMISSION = HoloCommand.ROOT_PERM + ".import";
  public static final String APPLY_PERMISSION = PERMISSION + ".apply";

  private static final int MAX_DETAIL_LINES = 12;

  @Director(name = "preview", aliases = {"dry-run", "dryrun"},
      description = "Preview a non-destructive legacy hologram migration",
      descriptionKey = "holoui.command.import.preview")
  public void preview(
      @Param(name = "source", description = "Legacy hologram source",
          descriptionKey = "holoui.parameter.import_source", customHandler = LegacySourceHandler.class)
      LegacyImportSource source,
      @Param(name = "sender", contextual = true, description = "Command sender context",
          descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender, PERMISSION)) {
      return;
    }
    LegacyHologramImportService importer = importer();
    if (importer.isBusy()) {
      send(sender, HoloMessages.IMPORT_BUSY, MessageArgs.empty());
      return;
    }
    send(sender, HoloMessages.IMPORT_PREVIEW_STARTED,
        MessageArgs.builder().untrusted("source", source.id()).build());
    importer.preview(source).whenComplete((plan, failure) -> {
      if (failure != null) {
        reportFailure(sender, source, failure);
        return;
      }
      sendLater(sender, () -> reportPreview(sender, plan));
    });
  }

  @Director(name = "apply", description = "Apply a no-overwrite legacy hologram migration",
      descriptionKey = "holoui.command.import.apply")
  public void apply(
      @Param(name = "source", description = "Legacy hologram source",
          descriptionKey = "holoui.parameter.import_source", customHandler = LegacySourceHandler.class)
      LegacyImportSource source,
      @Param(name = "sender", contextual = true, description = "Command sender context",
          descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    if (!checkPermission(sender, APPLY_PERMISSION)) {
      return;
    }
    LegacyHologramImportService importer = importer();
    if (importer.isBusy()) {
      send(sender, HoloMessages.IMPORT_BUSY, MessageArgs.empty());
      return;
    }
    send(sender, HoloMessages.IMPORT_APPLY_STARTED,
        MessageArgs.builder().untrusted("source", source.id()).build());
    importer.apply(source).whenComplete((result, failure) -> {
      if (failure != null) {
        reportFailure(sender, source, failure);
        return;
      }
      sendLater(sender, () -> reportApply(sender, result));
    });
  }

  private void reportPreview(CommandSender sender, LegacyImportPlan plan) {
    if (!plan.sourcePresent()) {
      send(sender, HoloMessages.IMPORT_SOURCE_MISSING,
          MessageArgs.builder()
              .untrusted("source", plan.source().id())
              .untrusted("path", plan.sourcePath())
              .build());
      return;
    }
    send(sender, HoloMessages.IMPORT_PREVIEW_SUMMARY,
        MessageArgs.builder()
            .untrusted("source", plan.source().id())
            .untrusted("ready", plan.readyCount())
            .untrusted("resume", plan.resumeCount())
            .untrusted("conflicts", plan.conflictCount())
            .untrusted("errors", plan.errorCount())
            .untrusted("warnings", plan.warningCount())
            .untrusted("path", plan.sourcePath())
            .build());
    int lines = 0;
    for (LegacyImportCandidate candidate : plan.candidates()) {
      if (lines++ >= MAX_DETAIL_LINES) {
        break;
      }
      send(sender, HoloMessages.IMPORT_PREVIEW_ENTRY,
          MessageArgs.builder()
              .untrusted("legacy", candidate.legacyId())
              .untrusted("board", candidate.boardId())
              .untrusted("state", candidate.disposition().name().toLowerCase())
              .untrusted("warnings", candidate.warnings().size())
              .build());
    }
    reportIssues(sender, plan);
  }

  private void reportApply(CommandSender sender, LegacyImportApplyResult result) {
    LegacyImportPlan plan = result.plan();
    if (!plan.sourcePresent()) {
      send(sender, HoloMessages.IMPORT_SOURCE_MISSING,
          MessageArgs.builder()
              .untrusted("source", plan.source().id())
              .untrusted("path", plan.sourcePath())
              .build());
      return;
    }
    send(sender, HoloMessages.IMPORT_APPLY_SUMMARY,
        MessageArgs.builder()
            .untrusted("source", plan.source().id())
            .untrusted("imported", result.importedCount())
            .untrusted("skipped", result.skippedCount())
            .untrusted("failed", result.failedCount())
            .untrusted("errors", plan.errorCount())
            .untrusted("warnings", plan.warningCount())
            .build());
    int lines = 0;
    for (LegacyImportApplyEntry entry : result.entries()) {
      if (entry.status() == LegacyImportApplyEntry.Status.IMPORTED
          || entry.status() == LegacyImportApplyEntry.Status.RESUMED) {
        continue;
      }
      if (lines++ >= MAX_DETAIL_LINES) {
        break;
      }
      send(sender, HoloMessages.IMPORT_APPLY_ENTRY,
          MessageArgs.builder()
              .untrusted("legacy", entry.legacyId())
              .untrusted("state", entry.status().name().toLowerCase())
              .untrusted("reason", entry.message().isBlank() ? "-" : entry.message())
              .build());
    }
    reportIssues(sender, plan);
  }

  private void reportIssues(CommandSender sender, LegacyImportPlan plan) {
    int lines = 0;
    for (LegacyImportIssue issue : plan.issues()) {
      if (lines >= MAX_DETAIL_LINES) {
        return;
      }
      sendIssue(sender, issue.severity().name().toLowerCase(), issue.legacyId(), issue.message());
      lines++;
    }
    for (LegacyImportCandidate candidate : plan.candidates()) {
      for (String warning : candidate.warnings()) {
        if (lines >= MAX_DETAIL_LINES) {
          return;
        }
        sendIssue(sender, "warning", candidate.legacyId(), warning);
        lines++;
      }
    }
  }

  private void sendIssue(CommandSender sender, String severity, String legacyId, String reason) {
    send(sender, HoloMessages.IMPORT_ISSUE,
        MessageArgs.builder()
            .untrusted("severity", severity)
            .untrusted("legacy", legacyId)
            .untrusted("reason", reason)
            .build());
  }

  private void reportFailure(CommandSender sender, LegacyImportSource source, Throwable failure) {
    Throwable cause = rootCause(failure);
    if (cause instanceof LegacyImportBusyException) {
      sendLater(sender, () -> send(sender, HoloMessages.IMPORT_BUSY, MessageArgs.empty()));
      return;
    }
    if (!(cause instanceof CancellationException)) {
      HoloUI.logExceptionStack(true, cause, "Legacy hologram import failed for source %s.", source.id());
    }
    sendLater(sender, () -> send(sender, HoloMessages.IMPORT_FAILED,
        MessageArgs.builder()
            .untrusted("source", source.id())
            .untrusted("reason", safeReason(cause))
            .build()));
  }

  private static LegacyHologramImportService importer() {
    return HoloUI.INSTANCE.getConfigManager().getLegacyImporter();
  }

  private static boolean checkPermission(CommandSender sender, String permission) {
    if (sender.hasPermission(permission)) {
      return true;
    }
    send(sender, HoloMessages.PERMISSION_DENIED,
        MessageArgs.builder().untrusted("permission", permission).build());
    return false;
  }

  private static void sendLater(CommandSender sender, Runnable feedback) {
    boolean accepted = sender instanceof Player player
        ? SchedulerUtils.runEntity(HoloUI.INSTANCE, player, feedback)
        : SchedulerUtils.runGlobal(HoloUI.INSTANCE, feedback);
    if (!accepted) {
      HoloUI.log(Level.WARNING, "Unable to schedule legacy import feedback for %s.", sender.getName());
    }
  }

  private static void send(CommandSender sender, TextKey key, MessageArgs arguments) {
    sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(key, arguments));
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while ((current instanceof CompletionException || current instanceof ExecutionException)
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static String safeReason(Throwable failure) {
    String message = failure == null ? null : failure.getMessage();
    return message == null || message.isBlank()
        ? failure == null ? "unknown failure" : failure.getClass().getSimpleName()
        : message;
  }

  public static final class LegacySourceHandler implements DirectorParameterHandler<LegacyImportSource> {
    @Override
    public KList<LegacyImportSource> getPossibilities() {
      KList<LegacyImportSource> sources = new KList<>();
      sources.addAll(Arrays.asList(LegacyImportSource.values()));
      return sources;
    }

    @Override
    public String toString(LegacyImportSource value) {
      return value == null ? "" : value.id();
    }

    @Override
    public LegacyImportSource parse(String in, boolean force) throws DirectorParsingException {
      if (in == null || in.isBlank()) {
        throw new DirectorParsingException(
            HoloLocalization.globalText(HoloMessages.ERROR_IMPORT_SOURCE_REQUIRED));
      }
      try {
        return LegacyImportSource.parse(in);
      } catch (IllegalArgumentException failure) {
        throw new DirectorParsingException(HoloLocalization.globalText(
            HoloMessages.ERROR_IMPORT_SOURCE_UNKNOWN,
            MessageArgs.builder().untrusted("source", in).build()));
      }
    }

    @Override
    public boolean supports(Class<?> type) {
      return type == LegacyImportSource.class;
    }
  }
}
