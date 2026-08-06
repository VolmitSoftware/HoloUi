/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.holoui;

import art.arcane.holoui.localization.HoloMessages;
import art.arcane.holoui.menu.special.inventories.PreviewElement;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument;
import art.arcane.holoui.menu.special.inventories.doc.PreviewDocumentRegistry;
import art.arcane.holoui.menu.special.inventories.doc.PreviewStateContext;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.theme.DirectorProduct;
import art.arcane.volmlib.util.director.theme.DirectorThemes;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /holoui previews} — inspects and manages the JSON container-preview documents owned by
 * {@link PreviewDocumentRegistry}. Threading and permission patterns copied verbatim from
 * {@link HoloItemsCommand}.
 */
@Director(name = "previews", description = "Preview document tools", descriptionKey = "holoui.command.previews.root")
public class HoloPreviewsCommand {

  private static final int MAX_REPORTED_ERRORS = 3;

  private static void sendOnSender(CommandSender sender, String message) {
    runOnSender(sender, () -> sender.sendMessage(message));
  }

  private static void runOnSender(CommandSender sender, Runnable action) {
    if (sender instanceof Player player) {
      SchedulerUtils.runEntity(HoloUI.INSTANCE, player, action);
      return;
    }

    SchedulerUtils.runGlobal(HoloUI.INSTANCE, action);
  }

  @Director(name = "list", description = "List preview documents and their match rules", descriptionKey = "holoui.command.previews.list")
  public void list(@Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender") CommandSender sender) {
    String permission = HoloCommand.ROOT_PERM + ".previews";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    PreviewDocumentRegistry registry = HoloUI.INSTANCE.getPreviewRegistry();
    List<String> names = new ArrayList<>(registry.names());
    names.sort(String::compareTo);

    DirectorMiniMenu.Theme theme = DirectorMiniMenu.Theme.fromDirectorTheme(DirectorThemes.forProduct(DirectorProduct.HOLOUI));
    List<String> lines = new ArrayList<>();
    lines.add(DirectorMiniMenu.banner(HoloUI.INSTANCE.getLocalization().text(HoloMessages.PREVIEWS_LIST_HEADER), theme));

    if (names.isEmpty()) {
      String empty = HoloUI.INSTANCE.getLocalization().text(HoloMessages.PREVIEWS_LIST_EMPTY);
      lines.add("<" + theme.description() + ">" + DirectorMiniMenu.escapeText(empty) + "</" + theme.description() + ">");
    }

    for (String name : names) {
      CompiledPreviewDocument document = registry.get(name);
      if (document == null) {
        continue;
      }
      lines.add(listEntry(name, document, theme));
    }

    lines.add(DirectorMiniMenu.bar(theme));
    DirectorMiniMenu.deliver(sender, lines);
  }

  private String listEntry(String name, CompiledPreviewDocument document, DirectorMiniMenu.Theme theme) {
    CompiledPreviewDocument.MatchSummary summary = document.matchSummary();
    String summaryText = HoloUI.INSTANCE.getLocalization().text(
        HoloMessages.PREVIEWS_LIST_ENTRY,
        MessageArgs.builder()
            .untrusted("blocks", summary.blocks())
            .untrusted("entities", summary.entities())
            .untrusted("special", summary.special() == null ? "-" : summary.special())
            .untrusted("priority", summary.priority())
            .build()
    );
    return "<" + theme.muted() + ">⇀</" + theme.muted() + "> "
        + "<gradient:" + theme.primaryLeft() + ":" + theme.primaryRight() + ">" + DirectorMiniMenu.escapeText(name) + "</gradient> "
        + "<" + theme.description() + ">" + DirectorMiniMenu.escapeText(summaryText) + "</" + theme.description() + ">";
  }

  @Director(name = "reset", description = "Restore shipped preview defaults (does not remove extra user documents that may shadow them)", descriptionKey = "holoui.command.previews.reset")
  public void reset(
      @Param(name = "name", description = "Document name to reset, or * for every shipped document", descriptionKey = "holoui.parameter.previews_name", defaultValue = "*")
      String name,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    String permission = HoloCommand.ROOT_PERM + ".previews.reset";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    String target = name == null || name.trim().isEmpty() ? "*" : name.trim();

    // resetToDefault performs up to thirteen file writes plus a full reparse; never block the
    // calling thread (which may be the main thread) on that.
    SchedulerUtils.runAsync(HoloUI.INSTANCE, () -> {
      sendOnSender(sender, HoloUI.INSTANCE.getLocalization().legacy(
          HoloMessages.PREVIEWS_RESET_STARTED,
          MessageArgs.builder().untrusted("name", target).build()
      ));
      List<String> affected = HoloUI.INSTANCE.getPreviewRegistry().resetToDefault(target);
      if (affected.isEmpty()) {
        sendOnSender(sender, HoloUI.INSTANCE.getLocalization().legacy(
            HoloMessages.PREVIEWS_RESET_NONE,
            MessageArgs.builder().untrusted("name", target).build()
        ));
        return;
      }
      sendOnSender(sender, HoloUI.INSTANCE.getLocalization().legacy(
          HoloMessages.PREVIEWS_RESET_DONE,
          MessageArgs.builder().untrusted("count", affected.size()).build()
      ));
    });
  }

  @Director(name = "dump", description = "Build a preview document once and print its element counts", descriptionKey = "holoui.command.previews.dump")
  public void dump(
      @Param(name = "name", description = "Document name to build", descriptionKey = "holoui.parameter.previews_name")
      String name,
      @Param(name = "sender", contextual = true, description = "Command sender context", descriptionKey = "holoui.parameter.sender")
      CommandSender sender
  ) {
    String permission = HoloCommand.ROOT_PERM + ".previews.dump";
    if (!sender.hasPermission(permission)) {
      sendPermissionDenied(sender, permission);
      return;
    }

    String docName = name == null ? "" : name.trim();
    if (sender instanceof Player player) {
      // Building touches live block/inventory state, so the player path must run on the region
      // thread that owns the player (a no-op scheduling hop on non-Folia servers).
      runOnSender(player, () -> executeDump(player, docName));
      return;
    }

    // Console never touches world state (statics-only) and RCON reads the response buffer the
    // instant dispatch returns, so a next-tick hop here would hand RCON an empty response.
    executeDump(sender, docName);
  }

  private void executeDump(CommandSender sender, String docName) {
    // Same trailing-".json" tolerance reset() gets for free through resetToDefault -> extract.
    CompiledPreviewDocument document = HoloUI.INSTANCE.getPreviewRegistry().get(PreviewDocumentRegistry.normalize(docName));
    if (document == null) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
          HoloMessages.PREVIEWS_DUMP_UNKNOWN,
          MessageArgs.builder().untrusted("name", docName).build()
      ));
      return;
    }

    PreviewStateContext context = dumpContext(sender, document);
    List<String> errors = new ArrayList<>();
    List<PreviewElement> elements = document.build(context, errors::add);
    reportDump(sender, document, elements, errors);
  }

  /** The looked-at block when the sender is a player looking at one this document matches, else statics. */
  private PreviewStateContext dumpContext(CommandSender sender, CompiledPreviewDocument document) {
    if (sender instanceof Player player) {
      Block block = HoloUI.INSTANCE.getSessionManager().lookedAtBlock(player);
      if (block != null && document.matchesBlock(block.getType())) {
        return PreviewStateContext.forBlock(block, player, document.varsForBlock(block.getType()));
      }
    }
    return PreviewStateContext.statics(document.varsForBlock(null));
  }

  private void reportDump(CommandSender sender, CompiledPreviewDocument document, List<PreviewElement> elements, List<String> errors) {
    int panels = 0;
    int cells = 0;
    int slots = 0;
    int labels = 0;
    for (PreviewElement element : elements) {
      if (element instanceof PreviewElement.Panel) {
        panels++;
      } else if (element instanceof PreviewElement.Cell) {
        cells++;
      } else if (element instanceof PreviewElement.Slot) {
        slots++;
      } else if (element instanceof PreviewElement.Label) {
        labels++;
      }
    }

    sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
        HoloMessages.PREVIEWS_DUMP_RESULT,
        MessageArgs.builder()
            .untrusted("name", document.name())
            .untrusted("total", elements.size())
            .untrusted("panels", panels)
            .untrusted("cells", cells)
            .untrusted("slots", slots)
            .untrusted("labels", labels)
            .build()
    ));
    reportDumpErrors(sender, errors);
  }

  /** Up to {@link #MAX_REPORTED_ERRORS} build-error strings, then a "+N more" tail pointing at the console log. */
  private void reportDumpErrors(CommandSender sender, List<String> errors) {
    if (errors.isEmpty()) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(HoloMessages.PREVIEWS_DUMP_NO_ERRORS));
      return;
    }

    int shown = Math.min(MAX_REPORTED_ERRORS, errors.size());
    for (int index = 0; index < shown; index++) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
          HoloMessages.PREVIEWS_DUMP_ERROR_LINE,
          MessageArgs.builder().untrusted("message", errors.get(index)).build()
      ));
    }

    int remaining = errors.size() - shown;
    if (remaining > 0) {
      sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
          HoloMessages.PREVIEWS_DUMP_ERROR_MORE,
          MessageArgs.builder().untrusted("count", remaining).build()
      ));
    }
  }

  private void sendPermissionDenied(CommandSender sender, String permission) {
    sender.sendMessage(HoloUI.INSTANCE.getLocalization().legacy(
        HoloMessages.PERMISSION_DENIED,
        MessageArgs.builder().untrusted("permission", permission).build()
    ));
  }
}
