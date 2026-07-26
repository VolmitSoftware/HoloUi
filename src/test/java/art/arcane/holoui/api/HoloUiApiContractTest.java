package art.arcane.holoui.api;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HoloUiApiContractTest {

  @Test
  public void everyPublicApiTypeIsBuiltFromJavaBukkitAndItsOwnPackageOnly() throws Exception {
    List<Class<?>> surface = apiPackage();
    assertFalse("no api classes were discovered; the scan is broken", surface.isEmpty());

    for (Class<?> type : surface) {
      if (!Modifier.isPublic(type.getModifiers())) {
        continue;
      }

      assertAllowed(type.getSuperclass(), type, "superclass");
      for (Class<?> implemented : type.getInterfaces()) {
        assertAllowed(implemented, type, "interface");
      }

      for (Method method : type.getDeclaredMethods()) {
        if (!Modifier.isPublic(method.getModifiers())) {
          continue;
        }

        assertAllowed(method.getReturnType(), type, method.getName());
        for (Class<?> parameter : method.getParameterTypes()) {
          assertAllowed(parameter, type, method.getName());
        }
        for (Class<?> thrown : method.getExceptionTypes()) {
          assertAllowed(thrown, type, method.getName());
        }
      }

      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        if (!Modifier.isPublic(constructor.getModifiers())) {
          continue;
        }

        for (Class<?> parameter : constructor.getParameterTypes()) {
          assertAllowed(parameter, type, "<init>");
        }
      }

      for (Field field : type.getDeclaredFields()) {
        if (!Modifier.isPublic(field.getModifiers())) {
          continue;
        }

        assertAllowed(field.getType(), type, field.getName());
      }
    }
  }

  @Test
  public void theTextSanitiserIsNotPartOfThePublishedSurface() {
    assertFalse("HoloText must stay package-private, exactly like TraversalText in the Wormholes API",
        Modifier.isPublic(HoloText.class.getModifiers()));
  }

  @Test
  public void theClickAndIconDescriptorHierarchiesAreSealedSoThirdPartiesCannotSmuggleInTheirOwn() {
    assertTrue(HoloIcon.class.isSealed());
    assertTrue(HoloComponent.class.isSealed());
    assertFalse(HoloUiService.class.isSealed());
    assertFalse(HoloMenuHandle.class.isSealed());
  }

  private static void assertAllowed(Class<?> type, Class<?> owner, String member) {
    if (type == null) {
      return;
    }

    Class<?> component = type;
    while (component.isArray()) {
      component = component.getComponentType();
    }

    if (component.isPrimitive()) {
      return;
    }

    String name = component.getName();
    boolean allowed = name.startsWith("java.")
        || name.startsWith("org.bukkit.")
        || name.startsWith("art.arcane.holoui.api.");

    assertTrue(owner.getName() + "#" + member + " leaks " + name + " into the public API", allowed);
    assertFalse(owner.getName() + "#" + member + " leaks the internal package type " + name,
        name.startsWith("art.arcane.holoui.api.internal."));
  }

  private static List<Class<?>> apiPackage() throws URISyntaxException, ClassNotFoundException {
    File root = new File(HoloUiService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    File directory = new File(root, "art/arcane/holoui/api");
    File[] files = directory.listFiles();
    List<Class<?>> types = new ArrayList<>();

    if (files == null) {
      return types;
    }

    for (File file : files) {
      String fileName = file.getName();
      if (!file.isFile() || !fileName.endsWith(".class")) {
        continue;
      }

      types.add(Class.forName("art.arcane.holoui.api." + fileName.substring(0, fileName.length() - ".class".length())));
    }

    return types;
  }
}
