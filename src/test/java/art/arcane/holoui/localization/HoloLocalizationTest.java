package art.arcane.holoui.localization;

import art.arcane.holoui.config.HuiSettings;
import art.arcane.volmlib.util.director.help.DirectorHelpMessages;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeMessages;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.VolmitLocales;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HoloLocalizationTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private HoloLocalization localization;

  @Before
  public void setUp() throws Exception {
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    localization = new HoloLocalization(temporaryFolder.newFolder(), logger);
  }

  @Test
  public void generatesSparseLocaleSelectorWithEnglishInTheTypedCatalog() throws Exception {
    YamlConfiguration yaml = loadLanguageFile();

    assertEquals("en_US", yaml.getString("locale"));
    assertFalse(yaml.contains("messages"));
    assertEquals("Open a menu by id, or show the menu list when set to *", HoloMessages.COMMAND_OPEN.english());
  }

  @Test
  public void everyBundledLocaleFullyCoversTheTypedCatalog() throws Exception {
    for (String locale : VolmitLocales.nonEnglish()) {
      YamlConfiguration yaml = loadLanguageFile();
      yaml.set("locale", locale);
      yaml.set("messages", null);
      yaml.save(localization.languageFile());

      assertTrue(locale, localization.reload());
      for (MessageKey key : localization.snapshot().catalog().keys()) {
        assertEquals(locale + ":" + key.id(), locale, localization.snapshot().sourceLocale(key));
      }
    }
  }

  @Test
  public void bundledResourceSetExactlyMatchesSharedManifest() throws Exception {
    Set<String> expected = VolmitLocales.nonEnglish().stream()
        .map(locale -> locale + ".yml")
        .collect(Collectors.toUnmodifiableSet());
    try (Stream<Path> paths = Files.list(Path.of("src/main/resources/languages"))) {
      Set<String> actual = paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .collect(Collectors.toUnmodifiableSet());
      assertEquals(expected, actual);
    }
    assertFalse(expected.contains(VolmitLocales.ENGLISH + ".yml"));
  }

  @Test
  public void builderLinkTakesItsUrlFromSettingsAndNoLocaleHardcodesOne() throws Exception {
    assertEquals("https://holoui.volmitsoftware.com", HuiSettings.builderUrl());
    assertTrue(HoloMessages.BUILDER_OPEN.english().contains("{url}"));

    for (String locale : VolmitLocales.nonEnglish()) {
      Path resource = Path.of("src/main/resources/languages", locale + ".yml");
      String messages = Files.readString(resource);
      assertTrue(locale, messages.contains("{url}"));
      assertFalse(locale, messages.contains("holoui.volmit.com"));
      assertFalse(locale, messages.contains("holoui.volmitsoftware.com"));
    }
  }

  @Test
  public void builderUrlFallsBackWhenTheConfiguredValueIsNotAPlainLink() {
    assertEquals("https://editor.example.com/hui", HuiSettings.sanitizeBuilderUrl("  https://editor.example.com/hui  "));
    assertEquals("http://127.0.0.1:8080", HuiSettings.sanitizeBuilderUrl("http://127.0.0.1:8080"));
    assertEquals(HuiSettings.BUILDER_URL_DEFAULT, HuiSettings.sanitizeBuilderUrl(null));
    assertEquals(HuiSettings.BUILDER_URL_DEFAULT, HuiSettings.sanitizeBuilderUrl(""));
    assertEquals(HuiSettings.BUILDER_URL_DEFAULT, HuiSettings.sanitizeBuilderUrl("holoui.volmitsoftware.com"));
    assertEquals(HuiSettings.BUILDER_URL_DEFAULT, HuiSettings.sanitizeBuilderUrl("javascript:alert(1)"));
    assertEquals(HuiSettings.BUILDER_URL_DEFAULT, HuiSettings.sanitizeBuilderUrl("https://a.example'><click:run_command:/op me>"));
    assertEquals(HuiSettings.BUILDER_URL_DEFAULT, HuiSettings.sanitizeBuilderUrl("https://a.example/ b"));
  }

  @Test
  public void appliesExternalOverrideWithNamedArguments() throws Exception {
    YamlConfiguration yaml = loadLanguageFile();
    yaml.set("locale", "fr_FR");
    yaml.set("messages." + HoloMessages.MENU_UNAVAILABLE.id(), "&cMenu indisponible: {menu}");
    yaml.save(localization.languageFile());

    assertTrue(localization.reload());
    String rendered = localization.legacy(
        HoloMessages.MENU_UNAVAILABLE,
        MessageArgs.builder().untrusted("menu", "market").build()
    );

    assertEquals(ChatColor.RED + "Menu indisponible: market", rendered);
    assertEquals("fr_FR", localization.activeLocale());
  }

  @Test
  public void rejectsInvalidReloadAndRetainsLastGoodSnapshot() throws Exception {
    YamlConfiguration yaml = loadLanguageFile();
    yaml.set("messages." + HoloMessages.PREVIEW_SCALE_SIZE.id(), "Taille {percent}%");
    yaml.save(localization.languageFile());
    assertTrue(localization.reload());

    MessageArgs arguments = MessageArgs.builder().untrusted("percent", 125).build();
    assertEquals("Taille 125%", localization.text(HoloMessages.PREVIEW_SCALE_SIZE, arguments));

    yaml.set("messages." + HoloMessages.PREVIEW_SCALE_SIZE.id(), "Argument absent");
    yaml.save(localization.languageFile());

    assertFalse(localization.reload());
    assertEquals("Taille 125%", localization.text(HoloMessages.PREVIEW_SCALE_SIZE, arguments));
  }

  @Test
  public void resolvesDirectorLabelsAndDoesNotRenderUntrustedFormatting() throws Exception {
    YamlConfiguration yaml = loadLanguageFile();
    yaml.set("messages.director.help.navigation.back", "&aRetour");
    yaml.save(localization.languageFile());
    assertTrue(localization.reload());

    assertEquals("Retour", localization.directorResolver().resolve(DirectorHelpMessages.BACK));
    assertEquals(
        "Unknown parameter key: BadName",
        localization.directorResolver().resolve(
            DirectorRuntimeMessages.UNKNOWN_PARAMETER,
            MessageArgs.builder().untrusted("key", "&cBad" + ChatColor.DARK_RED + "Name").build()
        )
    );
    String rendered = localization.legacy(
        HoloMessages.MENU_UNAVAILABLE,
        MessageArgs.builder().untrusted("menu", "&cBad" + ChatColor.DARK_RED + "Name").build()
    );
    assertTrue(rendered.contains("&cBadName"));
    assertFalse(rendered.contains(String.valueOf(ChatColor.DARK_RED)));
  }

  @Test
  public void insertedArgumentsAreNeverReprocessedAsLaterSentinels() {
    String rendered = localization.text(
        HoloMessages.PREVIEW_FUEL_LEVEL,
        MessageArgs.builder()
            .untrusted("fuel", "\uE0001\uE001")
            .untrusted("maximum", "replacement")
            .build()
    );

    assertTrue(rendered.contains("\uE0001\uE001"));
    assertTrue(rendered.contains("replacement"));
  }

  @Test
  public void boardPagerMessagesDeclareEveryRuntimePlaceholder() {
    assertEquals(Set.of("page", "pages", "from", "to"), HoloMessages.BOARDS_LIST_PAGE.placeholders());
    assertEquals(Set.of("page", "pages"), HoloMessages.BOARDS_LIST_PAGE_INVALID.placeholders());
    assertEquals(
        "&7Page &f2&7/&f3 &8- &7showing &f11-20",
        localization.text(
            HoloMessages.BOARDS_LIST_PAGE,
            MessageArgs.builder()
                .untrusted("page", 2)
                .untrusted("pages", 3)
                .untrusted("from", 11)
                .untrusted("to", 20)
                .build()
        )
    );
  }

  @Test
  public void syncConsoleMessagesAcceptOnlyTheirDeclaredArguments() {
    assertEquals(Set.of(), HoloMessages.SYNC_CAPABILITY_WARNING.placeholders());
    assertEquals(Set.of("subject", "url"), HoloMessages.SYNC_OPEN_CONSOLE.placeholders());
    assertEquals(Set.of("subject", "session"), HoloMessages.SYNC_LINK_HOVER.placeholders());
    String warning = localization.legacy(HoloMessages.SYNC_CAPABILITY_WARNING);
    String open = localization.legacy(
        HoloMessages.SYNC_OPEN_CONSOLE,
        MessageArgs.builder()
            .untrusted("subject", "sync-qa")
            .untrusted("url", "https://editor.example/#/sync/secret")
            .build()
    );
    assertTrue(warning.contains("Treat it as a secret"));
    assertTrue(open.contains("sync-qa"));
    assertTrue(open.contains("https://editor.example/#/sync/secret"));
    String hover = localization.text(
        HoloMessages.SYNC_LINK_HOVER,
        MessageArgs.builder()
            .untrusted("subject", "sync-qa")
            .untrusted("session", "session12345")
            .build()
    );
    assertTrue(hover.contains("sync-qa"));
    assertTrue(hover.contains("session12345"));
  }

  private YamlConfiguration loadLanguageFile() throws Exception {
    File file = localization.languageFile();
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.load(file);
    return yaml;
  }
}
