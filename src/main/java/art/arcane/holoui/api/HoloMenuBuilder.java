package art.arcane.holoui.api;

import java.util.ArrayList;
import java.util.List;

public final class HoloMenuBuilder {
  private String id = "";
  private double offsetX;
  private double offsetY;
  private double offsetZ = 2.0D;
  private boolean lockPosition;
  private boolean followPlayer;
  private double maxDistance = 8.0D;
  private boolean closeOnDeath = true;
  private boolean closeOnTeleport = true;
  private final List<HoloComponent> components = new ArrayList<>();

  public HoloMenuBuilder id(String id) {
    this.id = id;
    return this;
  }

  public HoloMenuBuilder offset(double x, double y, double z) {
    this.offsetX = x;
    this.offsetY = y;
    this.offsetZ = z;
    return this;
  }

  public HoloMenuBuilder lockPosition(boolean lockPosition) {
    this.lockPosition = lockPosition;
    return this;
  }

  public HoloMenuBuilder followPlayer(boolean followPlayer) {
    this.followPlayer = followPlayer;
    return this;
  }

  public HoloMenuBuilder maxDistance(double maxDistance) {
    this.maxDistance = maxDistance;
    return this;
  }

  public HoloMenuBuilder closeOnDeath(boolean closeOnDeath) {
    this.closeOnDeath = closeOnDeath;
    return this;
  }

  public HoloMenuBuilder closeOnTeleport(boolean closeOnTeleport) {
    this.closeOnTeleport = closeOnTeleport;
    return this;
  }

  public HoloMenuBuilder component(HoloComponent component) {
    this.components.add(component);
    return this;
  }

  public HoloMenu build() {
    return new HoloMenu(id, offsetX, offsetY, offsetZ, lockPosition, followPlayer, maxDistance,
        closeOnDeath, closeOnTeleport, components);
  }
}
