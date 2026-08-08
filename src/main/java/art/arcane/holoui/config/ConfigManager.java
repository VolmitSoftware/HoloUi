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
import art.arcane.holoui.config.action.MenuActionData;
import art.arcane.holoui.config.components.ButtonComponentData;
import art.arcane.holoui.config.components.ComponentData;
import art.arcane.holoui.config.components.ToggleComponentData;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.holoui.menu.action.MenuAction;
import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import art.arcane.volmlib.util.hud.HudPriority;
import art.arcane.volmlib.util.hud.HudSlotClaim;
import art.arcane.volmlib.util.hud.HudSlotRequest;
import art.arcane.volmlib.util.hud.HudSurface;
import art.arcane.volmlib.util.io.FolderWatcher;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import lombok.Getter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class ConfigManager {

  private static final String RELOAD_LANE = "holoui:reload";
  private static final String MENU_EXTENSION = ".json";
  private static final long RELOAD_LANE_LINGER_TICKS = 60L;
  private static final HudSlotRequest RELOAD_REQUEST = new HudSlotRequest("holoui:reload", HudPriority.NOTICE, 2500L, List.of(HudSurface.ACTION_BAR, HudSurface.BOSS_BAR));

  private final Map<String, MenuDefinitionData> menuRegistry = new ConcurrentHashMap<>();
  private final Map<UUID, Long> reloadNoticeStamps = new ConcurrentHashMap<>();
  private final AtomicLong reloadNoticeSeq = new AtomicLong();

  private final File menuDir, imageDir;
  private final FolderWatcher menuDefinitionFolder, imageFolder;

  @Getter
  private final HuiSettings settings;

  public ConfigManager(File configDir) {
    this.imageDir = new File(configDir, "images");
    if (!imageDir.exists())
      imageDir.mkdirs();
    this.menuDir = new File(configDir, "menus");
    if (!menuDir.exists())
      menuDir.mkdirs();

    menuDefinitionFolder = new FolderWatcher(menuDir);
    imageFolder = new FolderWatcher(imageDir);
    settings = new HuiSettings(configDir);

    scanMenus();

    SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 5L, () -> guarded("5-tick", this::fastTick), true);
    SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 20L, () -> guarded("20-tick", this::slowTick), true);
  }

  private void scanMenus() {
    int ignored = 0;
    for (File f : menuDefinitionFolder.getWatchers().keySet()) {
      if (isMenuFile(menuDir, f)) {
        registerMenu(f);
      } else {
        ignored++;
      }
    }
    if (ignored > 0) {
      HoloUI.log(Level.FINE, "menus: ignored %d entries that are not top level json files.", ignored);
    }
  }

  private void fastTick() {
    if (menuDefinitionFolder.checkModifiedFast()) {
      menuDefinitionFolder.getChanged().forEach(f -> {
        if (isMenuFile(menuDir, f)) {
          String name = FilenameUtils.getBaseName(f.getName());
          Optional<MenuDefinitionData> data = loadConfig(name, f);
          data.ifPresent(d -> {
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
            menuRegistry.put(name, d);
            HoloUI.log(Level.INFO, "Menu config \"%s\" has been changed and re-registered.", name);
          });
        }
      });
    }
    if (imageFolder.checkModifiedFast()) {
      if (!imageFolder.getChanged().isEmpty()) {
        imageFolder.getChanged().forEach(f -> HoloUI.log(Level.INFO, "Image asset \"%s\" changed and was hot reloaded.", f.getName()));
        if (HoloUI.INSTANCE.getSessionManager() != null) {
          HoloUI.INSTANCE.getSessionManager().refreshVisuals();
        }
      }
    }
    settings.update();
    HoloUI.INSTANCE.getLocalization().update();
  }

  private void slowTick() {
    if (menuDefinitionFolder.checkModified()) {
      menuDefinitionFolder.getCreated().forEach(f -> {
        if (isMenuFile(menuDir, f)) {
          registerMenu(f);
        }
      });
      menuDefinitionFolder.getDeleted().forEach(f -> {
        if (isMenuFile(menuDir, f)) {
          unregisterMenu(f);
        }
      });
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
    if (file == null || file.isDirectory()) {
      return false;
    }
    if (!file.getName().toLowerCase(Locale.ROOT).endsWith(MENU_EXTENSION)) {
      return false;
    }
    File parent = file.getAbsoluteFile().getParentFile();
    return parent != null && parent.equals(root.getAbsoluteFile());
  }

  private void registerMenu(File f) {
    String name = FilenameUtils.getBaseName(f.getName());
    Optional<MenuDefinitionData> data = loadConfig(name, f);
    data.ifPresent(d -> {
      menuRegistry.put(name, d);
      HoloUI.log(Level.INFO, "New menu config \"%s\" detected and registered.", name);
    });
  }

  private void unregisterMenu(File f) {
    String name = FilenameUtils.getBaseName(f.getName());
    if (menuRegistry.containsKey(name)) {
      HoloUI.INSTANCE.getSessionManager().destroyAllType(name, p -> {
      });
      menuRegistry.remove(name);
      HoloUI.log(Level.INFO, "Menu config \"%s\" has been deleted and unregistered.", name);
    }
  }

  public void shutdown() {
    settings.write();
  }

  public Set<String> keys() {
    return menuRegistry.keySet();
  }

  public Optional<MenuDefinitionData> get(String key) {
    return exists(key) ? Optional.of(menuRegistry.get(key)) : Optional.empty();
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

  private Optional<MenuDefinitionData> loadConfig(String menuName, File f) {
    try (FileReader reader = new FileReader(f)) {
      if (FileUtils.sizeOf(f) == 0) {
        HoloUI.log(Level.WARNING, "Menu config \"%s.json\" is empty, ignoring.", menuName);
        return Optional.empty();
      }

      MenuDefinitionData data = BukkitJson.parse(reader, MenuDefinitionData.class);
      if (data != null) {
        data.setId(menuName);
        precompileActions(data);
      } else {
        HoloUI.log(Level.WARNING, "An unknown error occurred while parsing menu config \"%s.json\"! Skipping.", menuName);
      }
      return Optional.ofNullable(data);
    } catch (Throwable ex) {
      HoloUI.logExceptionStack(false, ex, "An error occurred while parsing menu config \"%s.json\":", menuName);
    }
    return Optional.empty();
  }

  private static void precompileActions(MenuDefinitionData menu) {
    if (menu.getComponents() == null) {
      return;
    }

    for (MenuComponentData component : menu.getComponents()) {
      if (component == null || component.data() == null) {
        continue;
      }

      ComponentData data = component.data();
      if (data instanceof ButtonComponentData button) {
        resolveActions(button.actions(), menu.getId(), component.id());
      } else if (data instanceof ToggleComponentData toggle) {
        resolveActions(toggle.trueActions(), menu.getId(), component.id());
        resolveActions(toggle.falseActions(), menu.getId(), component.id());
      }
    }
  }

  private static void resolveActions(List<MenuActionData> actions, String menuId, String componentId) {
    MenuAction.resolve(actions, menuId, componentId);
  }
}
