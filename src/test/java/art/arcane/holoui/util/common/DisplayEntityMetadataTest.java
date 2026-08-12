/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.holoui.util.common;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class DisplayEntityMetadataTest {

  @BeforeClass
  public static void installPacketEventsApi() {
    PacketEvents.setAPI(new TestPacketEventsApi());
  }

  @AfterClass
  public static void clearPacketEventsApi() {
    PacketEvents.setAPI(null);
  }

  @Test
  public void rawEntitiesReceiveOnlyBaseMetadata() {
    EntityType entityType = entityType();
    DisplayEntity entity = DisplayEntity.Builder.entity(
        entityType,
        new Location(null, 1D, 2D, 3D, 35F, -10F)
    );
    WrapperPlayServerEntityMetadata metadata = (WrapperPlayServerEntityMetadata) entity.dataPacket();
    List<Integer> indexes = metadata.getEntityMetadata().stream().map(EntityData::getIndex).toList();

    assertEquals(List.of(0, 5), indexes);
  }

  @Test
  public void rawEntitySpawnCarriesBodyAndHeadOrientation() {
    EntityType entityType = entityType();
    DisplayEntity entity = DisplayEntity.Builder.entity(
        entityType,
        new Location(null, 1D, 2D, 3D, 35F, -10F)
    );
    List<PacketWrapper<?>> packets = entity.spawn();
    WrapperPlayServerSpawnEntity spawn = (WrapperPlayServerSpawnEntity) packets.getFirst();

    assertEquals(35F, spawn.getYaw(), 0F);
    assertEquals(35F, spawn.getHeadYaw(), 0F);
    assertEquals(-10F, spawn.getPitch(), 0F);
    assertSame(entityType, spawn.getEntityType());
  }

  @Test
  public void rawEntityMovementOrientationAndDespawnStayPacketOnly() {
    DisplayEntity entity = DisplayEntity.Builder.entity(
        entityType(),
        new Location(null, 1D, 2D, 3D)
    );
    WrapperPlayServerEntityTeleport moved = (WrapperPlayServerEntityTeleport) entity.move(new Vector(2D, -1D, 4D));
    WrapperPlayServerEntityTeleport rotated = (WrapperPlayServerEntityTeleport) entity.rotate(70F, 15F);
    WrapperPlayServerEntityHeadLook headLook = (WrapperPlayServerEntityHeadLook) entity.headLook();
    WrapperPlayServerDestroyEntities removed = (WrapperPlayServerDestroyEntities) entity.remove();

    assertEquals(3D, moved.getPosition().getX(), 0D);
    assertEquals(1D, moved.getPosition().getY(), 0D);
    assertEquals(7D, moved.getPosition().getZ(), 0D);
    assertEquals(70F, rotated.getYaw(), 0F);
    assertEquals(15F, rotated.getPitch(), 0F);
    assertEquals(70F, headLook.getHeadYaw(), 0F);
    assertArrayEquals(new int[]{entity.id()}, removed.getEntityIds());
  }

  @Test
  public void displayEntitiesRetainDisplayMetadata() {
    DisplayEntity entity = new DisplayEntity(1, UUID.randomUUID(), entityType())
        .displayKind(DisplayEntity.DisplayKind.TEXT)
        .text(Component.text("A"));
    WrapperPlayServerEntityMetadata metadata = (WrapperPlayServerEntityMetadata) entity.dataPacket();
    List<Integer> indexes = metadata.getEntityMetadata().stream().map(EntityData::getIndex).toList();

    assertEquals(List.of(0, 5, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27), indexes);
  }

  @Test
  public void blockDisplaysCarryTheirGlobalBlockStateAtIndexTwentyThree() {
    DisplayEntity entity = new DisplayEntity(1, UUID.randomUUID(), entityType())
        .displayKind(DisplayEntity.DisplayKind.BLOCK)
        .blockState(9812);
    WrapperPlayServerEntityMetadata metadata = (WrapperPlayServerEntityMetadata) entity.dataPacket();
    List<EntityData<?>> values = metadata.getEntityMetadata();

    assertEquals(List.of(0, 5, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23),
        values.stream().map(EntityData::getIndex).toList());
    assertEquals(9812, values.getLast().getValue());
  }

  private static EntityType entityType() {
    return (EntityType) Proxy.newProxyInstance(
        EntityType.class.getClassLoader(),
        new Class<?>[]{EntityType.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "EntityType[test]";
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
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
