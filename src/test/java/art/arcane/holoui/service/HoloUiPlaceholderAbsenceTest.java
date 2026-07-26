package art.arcane.holoui.service;

import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;


public class HoloUiPlaceholderAbsenceTest {
  private static final String[] LOADS_WITHOUT_PLACEHOLDER_API = {
      "art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration"
  };

  private static final String[] REQUIRES_PLACEHOLDER_API = {
      "art.arcane.holoui.service.HoloUiPlaceholderInstaller",
      "art.arcane.holoui.service.HoloUiPlaceholderExpansion"
  };

  @Test
  public void everyEnablePathClassLoadsWhenPlaceholderApiIsAbsent() {
    ClassLoader hidden = new PlaceholderApiHidingLoader();

    for (String name : LOADS_WITHOUT_PLACEHOLDER_API) {
      try {
        Class.forName(name, true, hidden);
      } catch (Throwable failure) {
        throw new AssertionError(name + " must load when PlaceholderAPI is not installed", failure);
      }
    }
  }

  @Test
  public void theExpansionItselfStillDependsOnPlaceholderApi() {
    ClassLoader hidden = new PlaceholderApiHidingLoader();

    for (String name : REQUIRES_PLACEHOLDER_API) {
      boolean threw = false;

      try {
        Class.forName(name, true, hidden);
      } catch (Throwable failure) {
        threw = true;
      }

      if (!threw) {
        throw new AssertionError(name + " is expected to depend on PlaceholderAPI, so the split above is what keeps the plugin loadable");
      }
    }
  }

  private static final class PlaceholderApiHidingLoader extends URLClassLoader {
    private PlaceholderApiHidingLoader() {
      super(classpath(), ClassLoader.getPlatformClassLoader());
    }

    private static URL[] classpath() {
      String[] entries = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
      URL[] resolved = new URL[entries.length];

      for (int i = 0; i < entries.length; i++) {
        try {
          resolved[i] = new java.io.File(entries[i]).toURI().toURL();
        } catch (Throwable failure) {
          throw new IllegalStateException(entries[i], failure);
        }
      }

      return resolved;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (name.startsWith("me.clip.")) {
        throw new ClassNotFoundException(name);
      }

      return super.loadClass(name, resolve);
    }
  }
}
