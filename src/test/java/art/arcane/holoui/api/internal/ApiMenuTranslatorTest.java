package art.arcane.holoui.api.internal;

import art.arcane.holoui.api.HoloClickHandler;
import art.arcane.holoui.api.HoloComponent;
import art.arcane.holoui.api.HoloIcon;
import art.arcane.holoui.api.HoloMenu;
import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.config.components.ButtonComponentData;
import art.arcane.holoui.config.components.DecoComponentData;
import art.arcane.holoui.config.icon.AnimatedImageData;
import art.arcane.holoui.config.icon.BlockIconData;
import art.arcane.holoui.config.icon.EntityIconData;
import art.arcane.holoui.config.icon.TextIconData;
import art.arcane.holoui.config.icon.TextImageIconData;
import art.arcane.holoui.enums.MenuComponentType;
import org.bukkit.entity.EntityType;
import org.bukkit.Material;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ApiMenuTranslatorTest {

  private final HoloClickHandler buyHandler = click -> {
  };

  @Test
  public void aDescriptorMapsOntoTheJsonConfigModelFieldForField() {
    HoloMenu menu = HoloMenu.builder()
        .id("example-shop")
        .offset(1.0D, 2.0D, 3.0D)
        .lockPosition(true)
        .followPlayer(true)
        .maxDistance(12.0D)
        .closeOnDeath(false)
        .closeOnTeleport(false)
        .component(HoloComponent.decoration("title", 0.5D, 1.2D, -0.25D, HoloIcon.text("<red>Shop")))
        .build();

    MenuDefinitionData definition = ApiMenuTranslator.definition(menu);

    assertEquals("example-shop", definition.getId());
    assertEquals(1.0D, definition.getOffset().getX(), 0.0D);
    assertEquals(2.0D, definition.getOffset().getY(), 0.0D);
    assertEquals(3.0D, definition.getOffset().getZ(), 0.0D);
    assertTrue(definition.isLockPosition());
    assertTrue(definition.isFollowPlayer());
    assertEquals(12.0D, definition.getMaxDistance(), 0.0D);
    assertEquals(false, definition.isCloseOnDeath());
    assertEquals(false, definition.isCloseOnTeleport());

    MenuComponentData component = definition.getComponents().get(0);
    assertEquals("title", component.id());
    assertEquals(0.5D, component.offset().getX(), 0.0D);
    assertEquals(1.2D, component.offset().getY(), 0.0D);
    assertEquals(-0.25D, component.offset().getZ(), 0.0D);
    assertEquals(MenuComponentType.DECO, component.data().getType());
    assertEquals(new TextIconData("<red>Shop", null, null), ((DecoComponentData) component.data()).iconData());
  }

  @Test
  public void apiButtonsCarryNoJsonActionsSoOnlyTheHandlerEverRuns() {
    HoloMenu menu = HoloMenu.builder()
        .id("example-shop")
        .component(new HoloComponent.Button("buy", 0.0D, 0.0D, 0.0D, HoloIcon.text("Buy"), 0.25F, buyHandler))
        .build();

    MenuDefinitionData definition = ApiMenuTranslator.definition(menu);
    ButtonComponentData button = (ButtonComponentData) definition.getComponents().get(0).data();

    assertEquals(MenuComponentType.BUTTON, button.getType());
    assertTrue(button.actions().isEmpty());
    assertEquals(0.25F, button.highlightMod(), 0.0F);
  }

  @Test
  public void handlersAreIndexedByComponentIdAndDecorationsContributeNone() {
    HoloMenu menu = HoloMenu.builder()
        .id("example-shop")
        .component(HoloComponent.decoration("title", 0.0D, 0.0D, 0.0D, HoloIcon.text("Shop")))
        .component(HoloComponent.button("buy", 0.0D, 0.0D, 0.0D, HoloIcon.text("Buy"), buyHandler))
        .build();

    Map<String, HoloClickHandler> handlers = ApiMenuTranslator.handlers(menu);

    assertEquals(1, handlers.size());
    assertSame(buyHandler, handlers.get("buy"));
    assertNull(handlers.get("title"));
  }

  @Test
  public void imageIconsMapToTheExistingImageConfigTypes() {
    assertEquals(new TextImageIconData("icons/buy.png", null),
        ApiMenuTranslator.iconData(HoloIcon.image("icons/buy.png")));
    assertEquals(new AnimatedImageData(List.of("a.png", "b.png"), 4, null),
        ApiMenuTranslator.iconData(HoloIcon.animatedImage(List.of("a.png", "b.png"), 4)));
  }

  @Test
  public void entityIconsMapToThePacketEntityConfigType() {
    assertEquals(new EntityIconData(EntityType.PARROT, 0.5F, 0.9F),
        ApiMenuTranslator.iconData(HoloIcon.entity(EntityType.PARROT, 0.5F, 0.9F)));
  }

  @Test
  public void blockIconsMapToThePacketBlockDisplayConfigType() {
    assertEquals(new BlockIconData(Material.STONE, null),
        ApiMenuTranslator.iconData(HoloIcon.block(Material.STONE)));
  }
}
