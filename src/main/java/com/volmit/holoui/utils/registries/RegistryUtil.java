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
package com.volmit.holoui.utils.registries;

import com.mojang.datafixers.util.Pair;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;


@SuppressWarnings("unchecked")
public class RegistryUtil {
    private static final AtomicReference<RegistryLookup> registryLookup = new AtomicReference<>();
    private static final Map<Class<?>, Map<NamespacedKey, Keyed>> KEYED_REGISTRY = new HashMap<>();
    private static final Map<Class<?>, Map<NamespacedKey, Object>> ENUM_REGISTRY = new HashMap<>();
    private static final Map<Class<?>, Registry<Keyed>> REGISTRY = new HashMap<>();

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @NonNull String... keys) {
        return find(typeClass, defaultLookup(), keys);
    }

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @Nullable Lookup<T> lookup, @NonNull String... keys) {
        return find(typeClass, lookup, Arrays.stream(keys).map(NamespacedKey::minecraft).toArray(NamespacedKey[]::new));
    }

    public static <T> T find(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        return find(typeClass, defaultLookup(), keys);
    }

    @NonNull
    public static <T> T find(@NonNull Class<T> typeClass, @Nullable Lookup<T> lookup, @NonNull NamespacedKey... keys) {
        if (keys.length == 0) throw new IllegalArgumentException("Need at least one key");
        Registry<Keyed> registry = null;
        if (Keyed.class.isAssignableFrom(typeClass)) {
            registry = getRegistry(typeClass.asSubclass(Keyed.class));
        }
        if (registry == null) {
            registry = REGISTRY.computeIfAbsent(typeClass, t -> Arrays.stream(Registry.class.getDeclaredFields())
                    .filter(field -> Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()))
                    .filter(field -> Registry.class.isAssignableFrom(field.getType()))
                    .filter(field -> ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(t))
                    .map(field -> {
                        try {
                            return (Registry<Keyed>) field.get(null);
                        } catch (IllegalAccessException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }

        if (registry != null) {
            for (NamespacedKey key : keys) {
                Keyed value = registry.get(key);
                if (value != null)
                    return (T) value;
            }
        }

        if (lookup != null)
            return lookup.find(typeClass, keys);
        throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
    }

    @NonNull
    public static <T> T findByField(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        var values = KEYED_REGISTRY.computeIfAbsent(typeClass, RegistryUtil::getKeyedValues);
        for (NamespacedKey key : keys) {
            var value = values.get(key);
            if (value != null)
                return (T) value;
        }
        throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
    }

    @NonNull
    public static <T> T findByEnum(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys) {
        var values = ENUM_REGISTRY.computeIfAbsent(typeClass, RegistryUtil::getEnumValues);
        for (NamespacedKey key : keys) {
            var value = values.get(key);
            if (value != null)
                return (T) value;
        }
        throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
    }

    @NonNull
    public static <T> Lookup<T> defaultLookup() {
        return Lookup.combine(RegistryUtil::findByField, RegistryUtil::findByEnum);
    }

    private static Map<NamespacedKey, Keyed> getKeyedValues(@NonNull Class<?> typeClass) {
        return Arrays.stream(typeClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> Keyed.class.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        return (Keyed) field.get(null);
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Keyed::getKey, Function.identity()));
    }

    private static Map<NamespacedKey, Object> getEnumValues(@NonNull Class<?> typeClass) {
        return Arrays.stream(typeClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> typeClass.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        return new Pair<>(NamespacedKey.minecraft(field.getName().toLowerCase()), field.get(null));
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

    }

    @FunctionalInterface
    public interface Lookup<T> {
        @NonNull
        T find(@NonNull Class<T> typeClass, @NonNull NamespacedKey... keys);

        static <T> Lookup<T> combine(@NonNull Lookup<T>... lookups) {
            if (lookups.length == 0) throw new IllegalArgumentException("Need at least one lookup");
            return (typeClass, keys) -> {
                for (Lookup<T> lookup : lookups) {
                    try {
                        return lookup.find(typeClass, keys);
                    } catch (IllegalArgumentException ignored) {}
                }
                throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
            };
        }
    }

    @Nullable
    private static <T extends Keyed> Registry<T> getRegistry(@NotNull Class<T> tClass) {
        RegistryLookup lookup = registryLookup.updateAndGet(old -> {
            if (old != null) return old;

            RegistryLookup bukkit;
            try {
                bukkit = Bukkit::getRegistry;
            } catch (Throwable ignored) {
                bukkit = null;
            }
            return new DefaultRegistryLookup(bukkit);
        });
        return lookup.find(tClass);
    }

    private interface RegistryLookup {
        @Nullable
        <T extends Keyed> Registry<T> find(@NonNull Class<T> type);
    }

    private static class DefaultRegistryLookup implements RegistryLookup {
        private final RegistryLookup bukkit;
        private final Map<Type, Object> registries;

        private DefaultRegistryLookup(RegistryLookup bukkit) {
            this.bukkit = bukkit;
            registries = Arrays.stream(Registry.class.getDeclaredFields())
                    .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                    .filter(field -> Registry.class.isAssignableFrom(field.getType()))
                    .map(field -> {
                        var type = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                        try {
                            return Pair.of(type, field.get(null));
                        } catch (Throwable e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (a, b) -> a));
        }

        @Nullable
        @Override
        public <T extends Keyed> Registry<T> find(@NonNull Class<T> type) {
            if (bukkit == null) return (Registry<T>) registries.get(type);
            try {
                return bukkit.find(type);
            } catch (Throwable e) {
                return (Registry<T>) registries.get(type);
            }
        }
    }
}
