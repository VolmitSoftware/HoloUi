package art.arcane.holoui.menu.icon;

import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.config.icon.EntityIconData;
import art.arcane.holoui.exceptions.MenuIconException;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.MenuSessionOptions;
import art.arcane.holoui.menu.MenuTransform;
import art.arcane.holoui.util.common.math.CollisionPlane;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class EntityMenuIconGeometryTest {

  @BeforeClass
  public static void installPacketEventsApi() {
    PacketEvents.setAPI(new TestPacketEventsApi());
  }

  @AfterClass
  public static void clearPacketEventsApi() {
    PacketEvents.setAPI(null);
  }

  @Test
  public void clickPlaneUsesFeetAnchorAndAuthoredDimensions() throws MenuIconException {
    MenuTransform transform = new MenuTransform(
        new Location(null, 0D, 0D, 0D),
        new Vector(),
        90F,
        0F,
        0F,
        2F
    );
    MenuDefinitionData definition = new MenuDefinitionData(
        new Vector(),
        false,
        false,
        null,
        false,
        false,
        List.of()
    );
    MenuSession session = new MenuSession(
        definition,
        null,
        MenuSessionOptions.positioned(transform, request -> null, 1F)
    );
    EntityMenuIcon icon = new EntityMenuIcon(
        session,
        new Location(null, 3D, 4D, 5D),
        new EntityIconData(EntityType.PARROT, 0.5F, 0.9F)
    );

    CollisionPlane plane = icon.createBoundingBox(new Location(null, 3D, 4D, 5D));

    assertEquals(3D, plane.getCenter().getX(), 0D);
    assertEquals(4.9D, plane.getCenter().getY(), 0.000001D);
    assertEquals(5D, plane.getCenter().getZ(), 0D);
    assertEquals(1F, plane.getWidth(), 0F);
    assertEquals(1.8F, plane.getHeight(), 0F);
  }

  private static final class TestPacketEventsApi extends PacketEventsAPI<Object> {
    @Override
    public boolean isLoaded() {
      return true;
    }

    @Override
    public void init() {
    }

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public Object getPlugin() {
      return this;
    }

    @Override
    public ServerManager getServerManager() {
      return () -> ServerVersion.V_26_1_2;
    }

    @Override
    public ProtocolManager getProtocolManager() {
      return null;
    }

    @Override
    public PlayerManager getPlayerManager() {
      return null;
    }

    @Override
    public NettyManager getNettyManager() {
      return null;
    }

    @Override
    public ChannelInjector getInjector() {
      return null;
    }
  }
}
