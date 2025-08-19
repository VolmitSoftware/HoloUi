package com.volmit.holoui.utils.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.volmit.holoui.utils.registries.RegistryUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

import java.io.IOException;

@Data
@EqualsAndHashCode(callSuper = true)
final class RegistryTypeAdapter<T extends Keyed> extends TypeAdapter<T> {
    private final Class<T> type;

    @Override
    public void write(JsonWriter out, T value) throws IOException {
        out.value(value.getKey().toString());
    }

    @Override
    public T read(JsonReader in) throws IOException {
        var key = NamespacedKey.fromString(in.nextString());
        if (key == null) return null;
        return RegistryUtil.find(type, key);
    }

}
