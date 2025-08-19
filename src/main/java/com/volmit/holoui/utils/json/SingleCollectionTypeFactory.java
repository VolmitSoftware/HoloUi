package com.volmit.holoui.utils.json;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collection;

class SingleCollectionTypeFactory implements TypeAdapterFactory {
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!Collection.class.isAssignableFrom(type.getRawType()))
            return null;

        var delegate = gson.getDelegateAdapter(this, type);
        var element = gson.getAdapter(JsonElement.class);

        return new TypeAdapter<T>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                delegate.write(out, value);
            }

            @Override
            public T read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.BEGIN_ARRAY)
                    return delegate.read(in);

                var array = new JsonArray();
                array.add(element.read(in));
                return delegate.fromJsonTree(array);
            }
        }.nullSafe();
    }
}
