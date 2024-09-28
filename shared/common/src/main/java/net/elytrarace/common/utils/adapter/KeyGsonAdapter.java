package net.elytrarace.common.utils.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.kyori.adventure.key.Key;

import java.io.IOException;

public class KeyGsonAdapter extends TypeAdapter<Key> {
    @Override
    public void write(JsonWriter out, Key value) throws IOException {
        if (value == null) {
            return;
        }
        out.beginObject();
        out.name("namespace").value(value.namespace());
        out.name("value").value(value.value());
        out.endObject();
    }

    @Override
    public Key read(JsonReader in) throws IOException {
        in.beginObject();
        if (!in.nextName().equals("namespace")) {
            throw new IOException("Expected namespace");
        }
        var namespace = in.nextString();
        if (!in.nextName().equals("value")) {
            throw new IOException("Expected value");
        }
        var value = in.nextString();
        in.endObject();
        return Key.key(namespace, value);
    }
}
