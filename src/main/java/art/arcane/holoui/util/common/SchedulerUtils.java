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

import art.arcane.holoui.HoloUI;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SchedulerUtils {

    public interface TaskHandle {
        void cancel();

        boolean isCancelled();
    }

    public static TaskHandle scheduleSyncTimer(Plugin p, long period, long repetitions, Consumer<Long> onIteration, Runnable onFinish) {
        if (!isPluginActive(p)) {
            return new NoopTaskHandle(true);
        }

        if (repetitions <= 0) {
            scheduleSyncTask(p, Math.max(1L, period), onFinish, false);
            return new NoopTaskHandle(true);
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);
        long safePeriod = Math.max(1L, period);
        long[] currentIterations = {0L};
        TaskHandle[] ref = new TaskHandle[1];

        ref[0] = scheduleSyncTask(p, safePeriod, () -> {
            if (cancelled.get()) {
                return;
            }

            if (currentIterations[0] >= repetitions) {
                onFinish.run();
                cancelled.set(true);
                if (ref[0] != null) {
                    ref[0].cancel();
                }
                return;
            }

            onIteration.accept(currentIterations[0]);
            currentIterations[0]++;
        }, false);

        return new AtomicTaskHandle(cancelled, ref[0]);
    }

    public static TaskHandle scheduleSyncTask(Plugin p, long period, Runnable onIteration, boolean delayStart) {
        if (!isPluginActive(p) || onIteration == null) {
            return new NoopTaskHandle(true);
        }

        long safePeriod = Math.max(1L, period);
        long initialDelay = delayStart ? safePeriod : 0L;

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean started = new AtomicBoolean(false);
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (cancelled.get() || !isPluginActive(p)) {
                cancelled.set(true);
                return;
            }

            if (started.get() || !delayStart) {
                onIteration.run();
            }
            started.set(true);

            if (!cancelled.get()) {
                scheduleSyncDelayed(p, loop[0], safePeriod, cancelled);
            }
        };

        scheduleSyncDelayed(p, loop[0], initialDelay, cancelled);
        return new AtomicTaskHandle(cancelled, null);
    }

    public static TaskHandle runAsync(Plugin p, Runnable r) {
        if (!isPluginActive(p) || r == null) {
            return new NoopTaskHandle(true);
        }

        if (!FoliaScheduler.runAsync(p, r)) {
            try {
                BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(p, r);
                return new BukkitTaskHandle(task);
            } catch (IllegalPluginAccessException e) {
                if (!isPluginActive(p)) {
                    return new NoopTaskHandle(true);
                }

                throw new IllegalStateException("Failed to schedule async task while plugin is enabled.", e);
            }
        }

        return new NoopTaskHandle(false);
    }

    public static boolean runGlobal(Plugin plugin, Runnable runnable) {
        if (plugin == null || runnable == null || !isPluginActive(plugin)) {
            return false;
        }

        if (FoliaScheduler.runGlobal(plugin, runnable)) {
            return true;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return true;
        } catch (IllegalPluginAccessException ex) {
            HoloUI.logExceptionStack(false, ex, "Failed to run global task for plugin %s.", plugin.getName());
            return false;
        } catch (UnsupportedOperationException ex) {
            HoloUI.logExceptionStack(false, ex, "Global scheduler rejected task for plugin %s.", plugin.getName());
            return false;
        }
    }

    public static boolean runEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (plugin == null || entity == null || runnable == null || !isPluginActive(plugin)) {
            return false;
        }

        if (FoliaScheduler.runEntity(plugin, entity, runnable)) {
            return true;
        }

        if (FoliaScheduler.isFolia(plugin.getServer())) {
            plugin.getLogger().warning("Failed to run entity task on Folia for plugin " + plugin.getName()
                    + "; refusing unsafe global fallback.");
            return false;
        }

        return runGlobal(plugin, runnable);
    }

    private static void scheduleSyncDelayed(Plugin plugin, Runnable runnable, long delayTicks, AtomicBoolean cancelled) {
        if (cancelled.get() || !isPluginActive(plugin) || runnable == null) {
            cancelled.set(true);
            return;
        }

        long safeDelay = Math.max(0L, delayTicks);
        boolean scheduled = FoliaScheduler.runGlobal(plugin, runnable, safeDelay);

        if (scheduled) {
            return;
        }

        try {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, safeDelay);
        } catch (IllegalPluginAccessException e) {
            cancelled.set(true);
            if (!isPluginActive(plugin)) {
                return;
            }

            throw new IllegalStateException("Failed to schedule sync task while plugin is enabled.", e);
        } catch (UnsupportedOperationException e) {
            throw new IllegalStateException("Failed to schedule sync task on Folia-safe scheduler.", e);
        }
    }

    public static void cancelPluginTasks(Plugin plugin) {
        if (plugin == null) {
            return;
        }

        FoliaScheduler.cancelTasks(plugin);

        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (UnsupportedOperationException | IllegalPluginAccessException ex) {
            // Folia blocks BukkitScheduler usage.
            HoloUI.logExceptionStack(false, ex, "Skipping BukkitScheduler#cancelTasks for plugin %s.", plugin.getName());
        }
    }

    private static boolean isPluginActive(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }

    private static class AtomicTaskHandle implements TaskHandle {
        private final AtomicBoolean cancelled;
        private final TaskHandle delegate;

        private AtomicTaskHandle(AtomicBoolean cancelled, TaskHandle delegate) {
            this.cancelled = cancelled;
            this.delegate = delegate;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            if (delegate != null) {
                delegate.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() || (delegate != null && delegate.isCancelled());
        }
    }

    private static class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask delegate;

        private BukkitTaskHandle(BukkitTask delegate) {
            this.delegate = delegate;
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }
    }

    private static class NoopTaskHandle implements TaskHandle {
        private final AtomicBoolean cancelled;

        private NoopTaskHandle(boolean initiallyCancelled) {
            this.cancelled = new AtomicBoolean(initiallyCancelled);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
