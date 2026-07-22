package art.arcane.holoui.localization;

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

  private YamlConfiguration loadLanguageFile() throws Exception {
    File file = localization.languageFile();
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.load(file);
    return yaml;
  }
}
