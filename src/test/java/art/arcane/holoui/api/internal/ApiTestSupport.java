package art.arcane.holoui.api.internal;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

final class ApiTestSupport {

  private ApiTestSupport() {
  }

  static Player player(UUID id) {
    return player(id, new AtomicBoolean(true), new AtomicBoolean(true));
  }

  static Player player(UUID id, AtomicBoolean online, AtomicBoolean permitted) {
    return (Player) Proxy.newProxyInstance(ApiTestSupport.class.getClassLoader(),
        new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getUniqueId" -> id;
          case "getName" -> "tester-" + id;
          case "isOnline" -> online.get();
          case "hasPermission" -> permitted.get();
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "player(" + id + ")";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

  static Plugin plugin(String name, BooleanSupplier enabled) {
    return (Plugin) Proxy.newProxyInstance(ApiTestSupport.class.getClassLoader(),
        new Class<?>[]{Plugin.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getName" -> name;
          case "isEnabled" -> enabled.getAsBoolean();
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "plugin(" + name + ")";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

  static CapturingLogger logger() {
    return new CapturingLogger();
  }

  static final class CapturingLogger {
    private final List<LogRecord> records = new CopyOnWriteArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();

    CapturingLogger() {
      logger.setUseParentHandlers(false);
      logger.setLevel(Level.ALL);
      logger.addHandler(new Handler() {
        @Override
        public void publish(LogRecord record) {
          records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
      });
    }

    Logger logger() {
      return logger;
    }

    List<String> messages() {
      return records.stream().map(LogRecord::getMessage).toList();
    }

    List<String> messagesAt(Level level) {
      return records.stream().filter(record -> record.getLevel().equals(level)).map(LogRecord::getMessage).toList();
    }
  }
}
