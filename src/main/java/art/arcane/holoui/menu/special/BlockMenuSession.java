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
package art.arcane.holoui.menu.special;

import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.menu.MenuSession;
import art.arcane.holoui.menu.components.MenuComponent;
import art.arcane.volmlib.util.math.MathHelper;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class BlockMenuSession extends MenuSession {

    private static final double[] HEIGHT_TEST_OFFSETS = new double[]{0.00D, 0.35D, -0.25D, 0.70D, -0.55D, 1.05D, -0.85D};
    private static final double[] PUSH_TEST_OFFSETS = new double[]{0.00D, 0.35D, 0.70D, 1.05D, 1.40D};

    private final Block block;
    private Location smoothedAnchor;

    public BlockMenuSession(MenuDefinitionData data, Player p, Block b) {
        super(data, p);
        this.block = b;
    }

    public boolean shouldRender(Block lookingAt) {
        return HuiSettings.PREVIEW_ENABLED.value() && lookingAt.equals(this.block);
    }

    public boolean hasPermission() {
        return !HuiSettings.PREVIEW_BY_PERMISSION.value() || getPlayer().hasPermission("holoui.preview." + block.getType().getKey().getKey());
    }

    @Override
    public void rotate(float yaw) {
        super.rotate(yaw);
        this.initialY = yaw;
    }

    @Override
    public void move(Location loc, boolean byPlayer) {
        if (!HuiSettings.PREVIEW_FOLLOW_PLAYER.value()) {
            Location resolvedAnchor = resolveAnchor();
            this.centerPoint = smoothAnchor(resolvedAnchor).clone();
            for (MenuComponent<?> component : getComponents()) {
                component.move(this.centerPoint.clone());
            }
        } else {
            this.smoothedAnchor = null;
            this.centerPoint = loc.clone().add(getOffset());
            rotateCenter();
        }
        this.centerPoint.setYaw(0F);
        this.centerPoint.setPitch(0F);
        adjustRotation(byPlayer);
    }

    public void open() {
        this.initialY = -(float) MathHelper.getRotationFromDirection(getPlayer().getEyeLocation().getDirection()).getY();
        getComponents().forEach(c -> c.open(false));
    }

    private Location resolveAnchor() {
        Location blockCenter = block.getLocation().clone().add(0.5D, 0.5D, 0.5D);
        Location eye = getPlayer().getEyeLocation();
        Vector pushDirection = eye.toVector().subtract(blockCenter.toVector());
        pushDirection.setY(0D);
        if (pushDirection.lengthSquared() <= 1.0E-6D) {
            pushDirection = eye.getDirection().clone().multiply(-1D);
            pushDirection.setY(0D);
        }
        if (pushDirection.lengthSquared() <= 1.0E-6D) {
            pushDirection = new Vector(0D, 0D, 1D);
        }
        pushDirection.normalize();

        double baseHeight = HuiSettings.previewAnchorHeight() + pitchHeightBias(eye.getPitch());
        double basePush = HuiSettings.previewAnchorPush();
        Location fallback = createAnchor(blockCenter, pushDirection, baseHeight, basePush);
        for (double heightOffset : HEIGHT_TEST_OFFSETS) {
            for (double pushOffset : PUSH_TEST_OFFSETS) {
                Location candidate = createAnchor(blockCenter, pushDirection, baseHeight + heightOffset, basePush + pushOffset);
                if (isVisibleAnchor(candidate, eye)) {
                    return candidate;
                }
            }
        }
        return fallback;
    }

    private Location createAnchor(Location blockCenter, Vector pushDirection, double height, double pushDistance) {
        Location anchor = blockCenter.clone().add(0D, height, 0D);
        anchor.add(pushDirection.clone().multiply(pushDistance));
        anchor.setYaw(0F);
        anchor.setPitch(0F);
        return anchor;
    }

    private boolean isVisibleAnchor(Location candidate, Location eye) {
        if (candidate.getWorld() == null || eye.getWorld() == null) {
            return false;
        }
        Block anchorBlock = candidate.getBlock();
        if (!anchorBlock.isPassable()) {
            return false;
        }

        Vector direction = candidate.toVector().subtract(eye.toVector());
        double distance = direction.length();
        if (distance <= 0.0D) {
            return true;
        }
        RayTraceResult traceResult = eye.getWorld().rayTraceBlocks(
                eye,
                direction.normalize(),
                distance,
                FluidCollisionMode.NEVER,
                true
        );
        return traceResult == null || traceResult.getHitBlock() == null;
    }

    private double pitchHeightBias(float pitch) {
        double normalized = Math.max(-1.0D, Math.min(1.0D, pitch / 90.0D));
        double curved = Math.sin(normalized * (Math.PI / 2D));
        return curved * 0.40D;
    }

    private Location smoothAnchor(Location target) {
        if (target == null) {
            return centerPoint == null ? block.getLocation().clone().add(0.5D, 0.5D, 0.5D) : centerPoint.clone();
        }
        if (smoothedAnchor == null || smoothedAnchor.getWorld() == null || target.getWorld() == null || !smoothedAnchor.getWorld().equals(target.getWorld())) {
            smoothedAnchor = target.clone();
            return smoothedAnchor;
        }
        if (smoothedAnchor.distanceSquared(target) > 2.25D) {
            smoothedAnchor = target.clone();
            return smoothedAnchor;
        }
        if (smoothedAnchor.distanceSquared(target) < 0.0004D) {
            return smoothedAnchor;
        }
        smoothedAnchor = blend(smoothedAnchor, target, 0.22D);
        return smoothedAnchor;
    }

    private Location blend(Location from, Location to, double alpha) {
        double clamped = Math.max(0.0D, Math.min(1.0D, alpha));
        double inv = 1.0D - clamped;
        Location blended = from.clone();
        blended.setX((from.getX() * inv) + (to.getX() * clamped));
        blended.setY((from.getY() * inv) + (to.getY() * clamped));
        blended.setZ((from.getZ() * inv) + (to.getZ() * clamped));
        return blended;
    }
}
