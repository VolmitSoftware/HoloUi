package art.arcane.holoui.integration;

import art.arcane.holoui.config.icon.CustomItemIconData;
import art.arcane.holoui.config.icon.MenuIconData;
import art.arcane.holoui.enums.MenuIconType;
import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The whole integration layer with no third-party item plugin on the server, which is the only
 * shape this suite can reproduce: none of the host APIs are on the test classpath because every one
 * of them is a compileOnly dependency.
 */
public class CustomItemIntegrationTest {

  private static final String CANONICAL_JSON =
      "{ \"type\": \"customItem\", \"provider\": \"itemsadder\", \"item\": \"myitems:ruby\", \"count\": 1 }";

  // Bukkit.setServer refuses to run outside a real Paper build (it resolves ServerBuildInfo through
  // a ServiceLoader), so the singleton is planted and restored directly.
  @BeforeClass
  public static void installBareServer() throws ReflectiveOperationException {
    serverField().set(null, server());
  }

  @AfterClass
  public static void removeBareServer() throws ReflectiveOperationException {
    serverField().set(null, null);
  }

  private static Field serverField() throws ReflectiveOperationException {
    Field field = Bukkit.class.getDeclaredField("server");
    field.setAccessible(true);
    return field;
  }

  @Test
  public void registryActivatesNothingWhenNoProviderPluginIsPresent() {
    ItemProviderRegistry registry = new ItemProviderRegistry(plugin());
    registry.activateAll();

    assertTrue(registry.active().isEmpty());
    assertTrue(registry.byId("itemsadder").isEmpty());
    assertTrue(registry.statusSummary().startsWith("0/"));

    List<ProviderStatus> statuses = registry.providerStatuses();
    assertEquals(ItemProviderRegistry.definitions().size(), statuses.size());
    for (ProviderStatus status : statuses) {
      assertFalse(status.pluginPresent());
      assertFalse(status.active());
      assertFalse(status.ready());
      assertEquals(0, status.itemCount());
    }

    registry.shutdown();
  }

  @Test
  public void resolveReturnsNullCleanlyWithoutProviders() {
    ItemProviderRegistry registry = new ItemProviderRegistry(plugin());
    registry.activateAll();

    assertNull(registry.resolve("itemsadder", "myitems:ruby"));
    assertNull(registry.resolve("nosuchprovider", "myitems:ruby"));
    assertNull(registry.resolve("ITEMSADDER", "myitems:ruby"));

    registry.shutdown();
  }

  @Test
  public void autoProviderPathIsNullSafe() {
    ItemProviderRegistry registry = new ItemProviderRegistry(plugin());
    registry.activateAll();

    assertNull(registry.resolve(ItemProviderRegistry.AUTO_PROVIDER, "myitems:ruby"));
    assertNull(registry.resolve(null, "myitems:ruby"));
    assertNull(registry.resolve("", "myitems:ruby"));
    assertNull(registry.resolve("  ", "myitems:ruby"));
    assertNull(registry.resolve(ItemProviderRegistry.AUTO_PROVIDER, null));
    assertNull(registry.resolve(ItemProviderRegistry.AUTO_PROVIDER, ""));
    assertNull(registry.resolve(null, null));

    registry.invalidate();
    assertNull(registry.resolve(null, "myitems:ruby"));

    registry.shutdown();
  }

  @Test
  public void everyProviderDefinitionNamesALoadableItemProvider() throws ReflectiveOperationException {
    ClassLoader loader = ItemProviderRegistry.class.getClassLoader();
    Set<String> seenPlugins = new LinkedHashSet<>();
    Set<String> seenClasses = new LinkedHashSet<>();

    for (ProviderDefinition definition : ItemProviderRegistry.definitions()) {
      assertTrue(definition.pluginName() + " is declared twice", seenPlugins.add(definition.pluginName()));
      assertTrue(definition.className() + " is declared twice", seenClasses.add(definition.className()));

      // initialize=false on purpose: linking an adapter would resolve its host plugin's API types,
      // and none of those jars are on the test runtime classpath. Loading still proves the class
      // exists under exactly this name and still implements the SPI.
      Class<?> loaded = Class.forName(definition.className(), false, loader);
      assertTrue(definition.className() + " does not implement ItemProvider",
          ItemProvider.class.isAssignableFrom(loaded));
      assertNotNull(loaded.asSubclass(ItemProvider.class));
      assertFalse(definition.className() + " is abstract", Modifier.isAbstract(loaded.getModifiers()));

      Constructor<?> constructor = loaded.getDeclaredConstructor();
      assertTrue(definition.className() + " has no public no-arg constructor",
          Modifier.isPublic(constructor.getModifiers()));
    }

    assertEquals(10, seenClasses.size());
  }

  @Test
  public void customItemIconDataRoundTripsThroughBukkitJson() {
    MenuIconData decoded = BukkitJson.GSON.fromJson(CANONICAL_JSON, MenuIconData.class);
    assertTrue(decoded instanceof CustomItemIconData);

    CustomItemIconData data = (CustomItemIconData) decoded;
    assertEquals(MenuIconType.CUSTOM_ITEM, data.getType());
    assertEquals("customItem", data.getType().getSerializedName());
    assertEquals("itemsadder", data.provider());
    assertEquals("myitems:ruby", data.item());
    assertEquals(1, data.count());

    JsonObject encoded = BukkitJson.GSON.toJsonTree(data, MenuIconData.class).getAsJsonObject();
    assertEquals(Set.of("type", "provider", "item", "count", "style"), encoded.keySet());
    assertTrue(encoded.get("style").isJsonNull());
    assertEquals("customItem", encoded.get("type").getAsString());
    assertEquals("itemsadder", encoded.get("provider").getAsString());
    assertEquals("myitems:ruby", encoded.get("item").getAsString());
    assertEquals(1, encoded.get("count").getAsInt());

    assertEquals(data, BukkitJson.GSON.fromJson(encoded, MenuIconData.class));
  }

  @Test
  public void customItemIconDataKeepsAuthoredIdsVerbatimAndToleratesOmittedKeys() {
    CustomItemIconData mixedCase = new CustomItemIconData("mmoitems", "SWORD:CUTLASS", 64, null);
    JsonObject encoded = BukkitJson.GSON.toJsonTree(mixedCase, MenuIconData.class).getAsJsonObject();
    assertEquals("SWORD:CUTLASS", encoded.get("item").getAsString());
    assertEquals(mixedCase, BukkitJson.GSON.fromJson(encoded, MenuIconData.class));

    MenuIconData sparse = BukkitJson.GSON.fromJson("{\"type\":\"customItem\",\"item\":\"ruby\"}", MenuIconData.class);
    CustomItemIconData data = (CustomItemIconData) sparse;
    assertNull(data.provider());
    assertEquals("ruby", data.item());
    assertEquals(0, data.count());
  }

  private static Plugin plugin() {
    return (Plugin) Proxy.newProxyInstance(CustomItemIntegrationTest.class.getClassLoader(),
        new Class<?>[]{Plugin.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getName" -> "HoloUI";
          case "isEnabled" -> true;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "plugin(HoloUI)";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

  private static Server server() {
    PluginManager pluginManager = (PluginManager) Proxy.newProxyInstance(
        CustomItemIntegrationTest.class.getClassLoader(),
        new Class<?>[]{PluginManager.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getPlugin" -> null;
          case "getPlugins" -> new Plugin[0];
          case "registerEvents" -> null;
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "pluginManager()";
          default -> throw new UnsupportedOperationException(method.getName());
        });

    return (Server) Proxy.newProxyInstance(CustomItemIntegrationTest.class.getClassLoader(),
        new Class<?>[]{Server.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getPluginManager" -> pluginManager;
          case "isPrimaryThread" -> true;
          case "getLogger" -> Logger.getAnonymousLogger();
          case "getName", "getVersion", "getBukkitVersion" -> "test";
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "server()";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }
}
