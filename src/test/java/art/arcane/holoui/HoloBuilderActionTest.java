package art.arcane.holoui;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code /holoui builder} has to be a leaf action rather than a command group: the command service
 * resolves implicit help before it executes anything, and a group node would answer a bare
 * {@code /holoui builder} with a help page instead of the editor link.
 */
public class HoloBuilderActionTest {

  @Test
  public void builderIsAnInvocableLeafSoABareInvocationRunsInsteadOfPrintingHelp() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    DirectorRuntimeNode builder = child(engine, "builder");

    assertNotNull(builder);
    assertTrue(builder.isInvocable());
    assertTrue(builder.getChildren().isEmpty());

    Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(engine, List.of("builder"), 9);
    assertTrue(page.isEmpty());
  }

  @Test
  public void builderCarriesNoSubcommandsFromTheRetiredServerControls() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));

    assertEquals(0, child(engine, "builder").getChildren().size());
    assertEquals("/holoui builder", child(engine, "builder").path());
  }

  private static DirectorRuntimeNode child(DirectorRuntimeEngine engine, String name) {
    for (DirectorRuntimeNode node : engine.getRoot().getChildren()) {
      if (node.allNames().contains(name)) {
        return node;
      }
    }
    return null;
  }
}
