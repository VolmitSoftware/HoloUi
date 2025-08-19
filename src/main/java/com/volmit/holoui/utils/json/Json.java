package com.volmit.holoui.utils.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

import java.io.Reader;

import static com.volmit.holoui.utils.json.Adapters.*;

public class Json {
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .setLenient()
            .serializeNulls()
            .registerTypeAdapter(Vector.class, VECTOR)
            .registerTypeHierarchyAdapter(NamespacedKey.class, NAMESPACED_KEY)
            .registerTypeHierarchyAdapter(Sound.class, SOUND)
            .registerTypeAdapter(Material.class, MATERIAL)
            .registerTypeAdapterFactory(new SingleCollectionTypeFactory())
            .create();

    public static <T> T parse(Reader reader, Class<T> clazz) {
        return GSON.fromJson(reader, clazz);
    }
}
