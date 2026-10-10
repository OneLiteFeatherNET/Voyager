package net.elytrarace.common.utils.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.IOException;

public class ComponentGsonAdapter extends TypeAdapter<Component> {

    @Override
    public void write(JsonWriter out, Component value) throws IOException {
        if (value == null) {
            return;
        }
        out.beginObject();
        out.name("component").value(MiniMessage.miniMessage().serialize(value));
        out.endObject();
    }

    @Override
    public Component read(JsonReader in) throws IOException {
        in.beginObject();
        if (!in.nextName().equals("component")) {
            throw new IOException("Expected component");
        }
        var component = MiniMessage.miniMessage().deserialize(in.nextString());
        in.endObject();
        return component;
    }
}
