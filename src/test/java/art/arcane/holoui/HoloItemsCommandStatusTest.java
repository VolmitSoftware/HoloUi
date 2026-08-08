package art.arcane.holoui;

import art.arcane.holoui.integration.ProviderStatus;
import art.arcane.holoui.localization.HoloLocalization;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class HoloItemsCommandStatusTest {
  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void providerStatesSupplyOnlyTheArgumentsTheirMessagesDeclare() throws Exception {
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    HoloLocalization localization = new HoloLocalization(temporaryFolder.newFolder(), logger);

    assertEquals("not installed", HoloItemsCommand.stateText(
        localization,
        new ProviderStatus("missing", "Missing", false, false, false, 0)
    ));
    assertEquals("present, no adapter", HoloItemsCommand.stateText(
        localization,
        new ProviderStatus("inactive", "Inactive", true, false, false, 0)
    ));
    assertEquals("present, still loading", HoloItemsCommand.stateText(
        localization,
        new ProviderStatus("loading", "Loading", true, true, false, 0)
    ));
    assertEquals("ready, 12 ids", HoloItemsCommand.stateText(
        localization,
        new ProviderStatus("ready", "Ready", true, true, true, 12)
    ));
  }
}
