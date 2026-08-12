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
package art.arcane.holoui.config;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.config.menu.MenuDocument;
import art.arcane.holoui.config.menu.MenuDocumentParser;
import art.arcane.holoui.config.menu.MenuIds;
import art.arcane.holoui.config.menu.MenuMutationService;
import art.arcane.holoui.config.menu.MenuRevisionConflictException;
import art.arcane.holoui.importer.LegacyHologramImportService;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.holoui.persistence.HoloUiPersistenceCoordinator;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSlotClaim;
import art.arcane.volmlib.util.hud.HudSlotRequest;
import art.arcane.volmlib.util.hud.HudSurface;
import art.arcane.volmlib.util.io.FolderWatcher;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class ConfigManager {

  private static final String RELOAD_LANE = "holoui:reload";
  private static final String MENU_EXTENSION = ".json";
  private static final long RELOAD_LANE_LINGER_TICKS = 60L;
  private static final HudSlotRequest RELOAD_REQUEST = new HudSlotRequest("holoui:reload", HudPriority.NOTICE, 2500L, List.of(HudSurface.ACTION_BAR, HudSurface.BOSS_BAR));

  private final Map<String, MenuDefinitionData> menuRegistry = new ConcurrentHashMap<>();
  private final Map<String, String> menuSourceRegistry = new ConcurrentHashMap<>();
  private final Map<UUID, Long> reloadNoticeStamps = new ConcurrentHashMap<>();
  private final AtomicLong reloadNoticeSeq = new AtomicLong();

  private final File menuDir, imageDir;
  private final FolderWatcher menuDefinitionFolder, imageFolder;
  private final MenuMutationService menuMutations;
  private final LegacyHologramImportService legacyImporter;

  private volatile boolean acceptingMenuMutations;

  @Getter
  private final HuiSettings settings;

  public ConfigManager(File configDir) {
    this.imageDir = new File(configDir, "images");
    if (!imageDir.exists())
      imageDir.mkdirs();
    this.menuDir = new File(configDir, "menus");
    if (!menuDir.exists())
      menuDir.mkdirs();

    menuMutations = new MenuMutationService(HoloUI.INSTANCE, configDir);
    legacyImporter = new LegacyHologramImportService(HoloUI.INSTANCE, this, configDir);
    menuDefinitionFolder = new FolderWatcher(menuDir);
    imageFolder = new FolderWatcher(imageDir);
    settings = new HuiSettings(configDir);
    acceptingMenuMutations = true;

    scanMenus();

    SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 5L, () -> guarded("5-tick", this::fastTick), true);
    SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 20L, () -> guarded("20-tick", this::slowTick), true);
  }

  private void scanMenus() {
    for (File file : discoverMenuFiles(menuDir)) {
      registerMenu(file);
    }
  }

  private void fastTick() {
    HoloUiPersistenceCoordinator coordinator = HoloUI.INSTANCE.getPersistenceCoordinator();
    if (coordinator != null && !coordinator.tryRead(this::fastFileTick)) {
      settings.update();
      HoloUI.INSTANCE.getLocalization().update();
      return;
    }
    if (coordinator == null) {
      fastFileTick();
    }
    settings.update();
    HoloUI.INSTANCE.getLocalization().update();
  }

  private void fastFileTick() {
    if (menuDefinitionFolder.checkModifiedFast()) {
      menuDefinitionFolder.getChanged().forEach(f -> {
        if (isMenuFile(menuDir, f)) {
          String name = menuId(menuDir, f);
          String previousRevision = getRevision(name).orElse(null);
          Optional<MenuDocument> loaded = loadConfig(name, f);
          loaded.filter(document -> !document.revision().equals(previousRevision)).ifPresent(document -> {
            HoloUI.INSTANCE.getSessionManager().destroyAllType(name, p -> {
              SchedulerUtils.runEntity(HoloUI.INSTANCE, p, () -> {
                String notice = HoloUI.INSTANCE.getLocalization().legacy(
                    HoloMessages.CONFIG_RELOADED,
                    MessageArgs.builder().untrusted("name", name).build()
                );
                HudSlotClaim claim = HoloUI.INSTANCE.getHudSlots().open(p, RELOAD_REQUEST);
                HudSurface surface = claim.resolve();
                if (surface == HudSurface.ACTION_BAR) {
                  p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(notice));
                } else if (surface == HudSurface.BOSS_BAR) {
                  long stamp = reloadNoticeSeq.incrementAndGet();
                  reloadNoticeStamps.put(p.getUniqueId(), stamp);
                  HoloUI.INSTANCE.getHudLanes().show(p, RELOAD_LANE, notice, 1.0D, BarColor.WHITE, BarStyle.SOLID, 2500L);
                  SchedulerUtils.scheduleSyncTimer(HoloUI.INSTANCE, RELOAD_LANE_LINGER_TICKS, 1L, iteration -> {
                  }, () -> {
                    HoloUI plugin = HoloUI.INSTANCE;
                    if (plugin != null && reloadNoticeStamps.remove(p.getUniqueId(), stamp)) {
                      plugin.getHudLanes().hide(p, RELOAD_LANE);
                    }
                  });
                }
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .5F, 1);
              });
            });
            publishDefinition(document);
            refreshBoardMenu(name);
            HoloUI.log(Level.INFO, "Menu config \"%s\" has been changed and re-registered.", name);
          });
        }
      });
      menuDefinitionFolder.getCreated().forEach(this::registerCreatedMenus);
      menuDefinitionFolder.getDeleted().forEach(this::unregisterDeletedPath);
    }
    if (imageFolder.checkModifiedFast()) {
      if (!imageFolder.getChanged().isEmpty()) {
        imageFolder.getChanged().forEach(f -> HoloUI.log(Level.INFO, "Image asset \"%s\" changed and was hot reloaded.", f.getName()));
        if (HoloUI.INSTANCE.getSessionManager() != null) {
          HoloUI.INSTANCE.getSessionManager().refreshVisuals();
        }
        if (HoloUI.INSTANCE.getBoardRuntime() != null) {
          HoloUI.INSTANCE.getBoardRuntime().refreshVisuals();
        }
      }
    }
  }

  private void slowTick() {
    HoloUiPersistenceCoordinator coordinator = HoloUI.INSTANCE.getPersistenceCoordinator();
    if (coordinator != null) {
      coordinator.tryRead(this::slowFileTick);
    } else {
      slowFileTick();
    }
  }

  private void slowFileTick() {
    if (menuDefinitionFolder.checkModified()) {
      menuDefinitionFolder.getCreated().forEach(this::registerCreatedMenus);
      menuDefinitionFolder.getDeleted().forEach(this::unregisterDeletedPath);
    }
    if (imageFolder.checkModified()) {
      if (!imageFolder.getCreated().isEmpty()) {
        imageFolder.getCreated().forEach(f -> HoloUI.log(Level.INFO, "Image asset \"%s\" was detected and hot loaded.", f.getName()));
      }
      if (!imageFolder.getDeleted().isEmpty()) {
        imageFolder.getDeleted().forEach(f -> HoloUI.log(Level.INFO, "Image asset \"%s\" was removed.", f.getName()));
      }
      if ((!imageFolder.getCreated().isEmpty() || !imageFolder.getDeleted().isEmpty()) && HoloUI.INSTANCE.getSessionManager() != null) {
        HoloUI.INSTANCE.getSessionManager().refreshVisuals();
        if (HoloUI.INSTANCE.getBoardRuntime() != null) {
          HoloUI.INSTANCE.getBoardRuntime().refreshVisuals();
        }
      }
    }
  }

  private static void guarded(String label, Runnable body) {
    try {
      body.run();
    } catch (Exception failure) {
      HoloUI.INSTANCE.getLogger().log(Level.WARNING, "Config " + label + " reload pass failed.", failure);
    }
  }

  static boolean isMenuFile(File root, File file) {
    if (root == null || file == null) {
      return false;
    }
    if (!file.getName().toLowerCase(Locale.ROOT).endsWith(MENU_EXTENSION)) {
      return false;
    }
    Path rootPath = root.toPath().toAbsolutePath().normalize();
    Path filePath = file.toPath().toAbsolutePath().normalize();
    if (!filePath.startsWith(rootPath) || filePath.equals(rootPath)
        || Files.isSymbolicLink(rootPath)) {
      return false;
    }
    Path relative = rootPath.relativize(filePath);
    Path current = rootPath;
    int segmentIndex = 0;
    for (Path segment : relative) {
      if (segment.toString().startsWith(".")) {
        return false;
      }
      current = current.resolve(segment);
      segmentIndex++;
      if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        continue;
      }
      if (Files.isSymbolicLink(current)) {
        return false;
      }
      boolean target = segmentIndex == relative.getNameCount();
      if ((target && !Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS))
          || (!target && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
        return false;
      }
    }
    return true;
  }

  static List<File> discoverMenuFiles(File root) {
    if (root == null || !root.exists()) {
      return List.of();
    }
    if (root.isFile()) {
      return isMenuFile(root.getParentFile(), root) ? List.of(root) : List.of();
    }

    List<File> menus = new ArrayList<>();
    try (Stream<Path> candidates = Files.walk(root.toPath())) {
      Iterator<Path> iterator = candidates.iterator();
      while (iterator.hasNext()) {
        Path candidate = iterator.next();
        File file = candidate.toFile();
        if (isMenuFile(root, file)) {
          menus.add(file);
        }
      }
    } catch (IOException failure) {
      HoloUI.logExceptionStack(true, failure, "Unable to scan menu files below %s.", root);
    }
    menus.sort(Comparator.comparing(file -> menuId(root, file)));
    return List.copyOf(menus);
  }

  static String menuId(File root, File file) {
    Path rootPath = root.toPath().toAbsolutePath().normalize();
    Path filePath = file.toPath().toAbsolutePath().normalize();
    if (!filePath.startsWith(rootPath) || filePath.equals(rootPath)) {
      throw new IllegalArgumentException("Menu file is not resolvable inside the menu root");
    }
    Path relative = rootPath.relativize(filePath);
    String path = relative.toString().replace(File.separatorChar, '/');
    return path.substring(0, path.length() - MENU_EXTENSION.length());
  }

  private void registerMenu(File f) {
    String name = menuId(menuDir, f);
    Optional<MenuDocument> loaded = loadConfig(name, f);
    loaded.filter(document -> !document.revision().equals(getRevision(name).orElse(null))).ifPresent(document -> {
      publishDefinition(document);
      refreshBoardMenu(name);
      HoloUI.log(Level.INFO, "New menu config \"%s\" detected and registered.", name);
    });
  }

  private void registerCreatedMenus(File path) {
    for (File created : discoverMenuFiles(path)) {
      if (isMenuFile(menuDir, created)) {
        registerMenu(created);
      }
    }
  }

  private void unregisterDeletedPath(File path) {
    if (isMenuFile(menuDir, path)) {
      unregisterMenu(path);
    }
    unregisterMenuPrefix(path);
  }

  private void unregisterMenu(File f) {
    String name = menuId(menuDir, f);
    if (menuRegistry.containsKey(name)) {
      HoloUI.INSTANCE.getSessionManager().destroyAllType(name, p -> {
      });
      menuRegistry.remove(name);
      menuSourceRegistry.remove(name);
      refreshBoardMenu(name);
      HoloUI.log(Level.INFO, "Menu config \"%s\" has been deleted and unregistered.", name);
    }
  }

  private void unregisterMenuPrefix(File directory) {
    String prefix;
    try {
      Path relative = menuDir.getCanonicalFile().toPath().relativize(directory.getCanonicalFile().toPath());
      prefix = relative.toString().replace(File.separatorChar, '/');
    } catch (IOException | IllegalArgumentException failure) {
      return;
    }
    if (prefix.isBlank() || prefix.startsWith("..")) {
      return;
    }
    String nestedPrefix = prefix + "/";
    List<String> removed = menuRegistry.keySet().stream()
        .filter(id -> id.startsWith(nestedPrefix))
        .toList();
    for (String id : removed) {
      HoloUI.INSTANCE.getSessionManager().destroyAllType(id, player -> {
      });
      menuRegistry.remove(id);
      menuSourceRegistry.remove(id);
      refreshBoardMenu(id);
      HoloUI.log(Level.INFO, "Menu config \"%s\" has been deleted and unregistered.", id);
    }
  }

  public void shutdown() {
    acceptingMenuMutations = false;
    legacyImporter.shutdown();
    menuMutations.shutdown();
    settings.write();
  }

  private void refreshBoardMenu(String menuId) {
    if (HoloUI.INSTANCE.getBoardRuntime() != null) {
      HoloUI.INSTANCE.getBoardRuntime().refreshMenu(menuId);
    }
  }

  public Set<String> keys() {
    return menuRegistry.keySet();
  }

  public LegacyHologramImportService getLegacyImporter() {
    return legacyImporter;
  }

  public Optional<MenuDefinitionData> get(String key) {
    return exists(key) ? Optional.of(menuRegistry.get(key)) : Optional.empty();
  }

  public Optional<String> getSource(String key) {
    return Optional.ofNullable(menuSourceRegistry.get(key));
  }

  public Optional<String> getRevision(String key) {
    return getSource(key).map(MenuDocument::revisionOf);
  }

  public CompletableFuture<MenuDocument> mutateMenu(String menuId,
                                                    UnaryOperator<JsonObject> mutation) {
    if (!acceptingMenuMutations) {
      return stoppedMutationFuture();
    }
    String id;
    try {
      id = MenuIds.require(menuId);
    } catch (IllegalArgumentException failure) {
      return CompletableFuture.failedFuture(failure);
    }
    String expectedRevision = getRevision(id).orElse(null);
    if (expectedRevision == null) {
      return CompletableFuture.failedFuture(new NoSuchElementException("unknown menu: " + id));
    }
    CompletableFuture<MenuDocument> written = menuMutations.mutate(id, expectedRevision, mutation);
    return publishAfterWrite(written, id, expectedRevision, false);
  }

  public CompletableFuture<MenuDocument> copyMenu(String sourceMenuId, String targetMenuId) {
    if (!acceptingMenuMutations) {
      return stoppedMutationFuture();
    }
    String sourceId;
    String targetId;
    try {
      sourceId = MenuIds.require(sourceMenuId);
      targetId = MenuIds.require(targetMenuId);
    } catch (IllegalArgumentException failure) {
      return CompletableFuture.failedFuture(failure);
    }
    String expectedRevision = getRevision(sourceId).orElse(null);
    if (expectedRevision == null) {
      return CompletableFuture.failedFuture(
          new NoSuchElementException("unknown menu: " + sourceId));
    }
    CompletableFuture<MenuDocument> written = menuMutations.copy(sourceId, expectedRevision, targetId);
    return publishAfterWrite(written, targetId, null, true);
  }

  public CompletableFuture<MenuDocument> createMenu(String menuId, String source) {
    if (!acceptingMenuMutations) {
      return stoppedMutationFuture();
    }
    String id;
    try {
      id = MenuIds.require(menuId);
    } catch (IllegalArgumentException failure) {
      return CompletableFuture.failedFuture(failure);
    }
    if (getRevision(id).isPresent()) {
      return CompletableFuture.failedFuture(new FileAlreadyExistsException(id));
    }
    CompletableFuture<MenuDocument> written = menuMutations.create(id, source);
    return publishAfterWrite(written, id, null, true);
  }

  public CompletableFuture<List<MenuDocument>> publishEditorSyncProject(Map<String, String> sources) {
    if (!acceptingMenuMutations) {
      return CompletableFuture.failedFuture(
          new CancellationException("menu mutation service is shut down"));
    }
    Map<String, String> requiredSources = Map.copyOf(sources);
    CompletableFuture<List<MenuDocument>> publication = new CompletableFuture<>();
    boolean accepted = SchedulerUtils.runGlobal(HoloUI.INSTANCE, () -> {
      if (publication.isDone()) {
        return;
      }
      try {
        List<MenuDocument> documents = new ArrayList<>(requiredSources.size());
        for (Map.Entry<String, String> entry : requiredSources.entrySet()) {
          documents.add(MenuDocumentParser.parse(entry.getKey(), entry.getValue()));
        }
        for (MenuDocument document : documents) {
          publishDefinition(document);
          if (HoloUI.INSTANCE.getSessionManager() != null) {
            HoloUI.INSTANCE.getSessionManager().destroyAllType(document.id(), player -> {
            });
          }
          refreshBoardMenu(document.id());
        }
        publication.complete(List.copyOf(documents));
      } catch (RuntimeException failure) {
        publication.completeExceptionally(failure);
      }
    });
    if (!accepted) {
      publication.completeExceptionally(
          new IllegalStateException("unable to schedule editor sync menu publication"));
    }
    return publication;
  }

  public boolean exists(String key) {
    return menuRegistry.containsKey(key);
  }

  public Pair<ImageFormat, BufferedImage> getImage(String relative) throws IOException {
    File f = resolveImageFile(imageDir, relative);
    ImageFormat format = Imaging.guessFormat(f);
    return Pair.of(format, Imaging.getBufferedImage(f));
  }

  public List<BufferedImage> getImages(String relative) throws IOException {
    File f = resolveImageFile(imageDir, relative);
    return Imaging.getAllBufferedImages(f);
  }

  static File resolveImageFile(File imageRoot, String relative) throws IOException {
    if (imageRoot == null || relative == null || relative.isBlank()) {
      throw new FileNotFoundException(String.valueOf(relative));
    }

    File root = imageRoot.getCanonicalFile();
    File image = new File(root, relative).getCanonicalFile();
    Path rootPath = root.toPath();
    if (!image.toPath().startsWith(rootPath) || !image.isFile()) {
      throw new FileNotFoundException(relative);
    }
    return image;
  }

  private Optional<MenuDocument> loadConfig(String menuName, File f) {
    try {
      String source = Files.readString(f.toPath(), StandardCharsets.UTF_8);
      if (source.isEmpty()) {
        HoloUI.log(Level.WARNING, "Menu config \"%s.json\" is empty, ignoring.", menuName);
        return Optional.empty();
      }

      return Optional.of(MenuDocumentParser.parse(menuName, source));
    } catch (Throwable ex) {
      HoloUI.logExceptionStack(false, ex, "An error occurred while parsing menu config \"%s.json\":", menuName);
    }
    return Optional.empty();
  }

  private CompletableFuture<MenuDocument> publishAfterWrite(CompletableFuture<MenuDocument> written,
                                                            String menuId,
                                                            String expectedRevision,
                                                            boolean create) {
    CompletableFuture<MenuDocument> published = new CompletableFuture<>();
    written.whenComplete((document, failure) -> {
      if (failure != null) {
        published.completeExceptionally(failure);
        return;
      }
      if (!acceptingMenuMutations) {
        published.completeExceptionally(
            new CancellationException("menu mutation service shut down before publication"));
        return;
      }
      boolean accepted = SchedulerUtils.runGlobal(HoloUI.INSTANCE,
          () -> publishWrittenDocument(published, document, menuId, expectedRevision, create));
      if (!accepted) {
        published.completeExceptionally(
            new IllegalStateException("unable to schedule menu publication for " + menuId));
      }
    });
    return published;
  }

  private void publishWrittenDocument(CompletableFuture<MenuDocument> published, MenuDocument document,
                                      String menuId, String expectedRevision, boolean create) {
    try {
      if (!acceptingMenuMutations) {
        throw new CancellationException("menu mutation service shut down before publication");
      }
      String currentSource = menuSourceRegistry.get(menuId);
      String currentRevision = currentSource == null ? null : MenuDocument.revisionOf(currentSource);
      if (document.revision().equals(currentRevision)) {
        published.complete(document);
        return;
      }
      if (create) {
        if (currentRevision != null) {
          throw new MenuRevisionConflictException(menuId, "absent", currentRevision);
        }
      } else if (!expectedRevision.equals(currentRevision)) {
        throw new MenuRevisionConflictException(menuId, expectedRevision,
            currentRevision == null ? "absent" : currentRevision);
      }
      publishDefinition(document);
      if (HoloUI.INSTANCE.getSessionManager() != null) {
        HoloUI.INSTANCE.getSessionManager().destroyAllType(menuId, player -> {
        });
      }
      refreshBoardMenu(menuId);
      HoloUI.log(Level.INFO, "Menu config \"%s\" was updated by an in-game content command.", menuId);
      published.complete(document);
    } catch (RuntimeException failure) {
      published.completeExceptionally(failure);
    }
  }

  private void publishDefinition(MenuDocument document) {
    menuRegistry.put(document.id(), document.definition());
    menuSourceRegistry.put(document.id(), document.source());
  }

  private static CompletableFuture<MenuDocument> stoppedMutationFuture() {
    return CompletableFuture.failedFuture(
        new CancellationException("menu mutation service is shut down"));
  }
}
