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
package com.volmit.holoui.utils;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

public class SchedulerUtils {

    public static BukkitTask scheduleSyncTimer(Plugin p, long period, long repetitions, Consumer<Long> onIteration, Runnable onFinish) {
        return new TimerRunnable(onIteration, onFinish, repetitions).runTaskTimer(p, 0L, period);
    }

    public static BukkitTask scheduleSyncTask(Plugin p, long period, Runnable onIteration, boolean delayStart) {
        return Bukkit.getScheduler().runTaskTimer(p, onIteration, period, delayStart ? period : 0);
    }

    public static BukkitTask runAsync(Plugin p, Runnable r) {
        return Bukkit.getScheduler().runTaskAsynchronously(p, r);
    }

    @RequiredArgsConstructor
    private static class TimerRunnable extends BukkitRunnable {

        private final Consumer<Long> onIteration;
        private final Runnable onFinish;
        private final long iterations;

        private long currentIterations;

        @Override
        public void run() {
            if (currentIterations >= iterations) {
                onFinish.run();
                cancel();
                return;
            }
            onIteration.accept(currentIterations);
            currentIterations++;
        }
    }
}
