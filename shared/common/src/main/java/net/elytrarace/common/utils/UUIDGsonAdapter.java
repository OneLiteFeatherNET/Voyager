package net.elytrarace.common.utils;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.UUID;

public class UUIDGsonAdapter extends TypeAdapter<UUID> {
    @Override
    public void write(JsonWriter jsonWriter, UUID uuid) throws IOException {
        if (uuid == null) {
            return;
        }
        jsonWriter.beginObject();
        jsonWriter.name("mostSigBits").value(uuid.getMostSignificantBits());
        jsonWriter.name("leastSigBits").value(uuid.getLeastSignificantBits());
        jsonWriter.endObject();
    }

    @Override
    public UUID read(JsonReader jsonReader) throws IOException {
        jsonReader.beginObject();
        if (!jsonReader.nextName().equals("mostSigBits")) {
            throw new IOException("Expected mostSigBits");
        }
        var mostSigBits = jsonReader.nextLong();
        if (!jsonReader.nextName().equals("leastSigBits")) {
            throw new IOException("Expected leastSigBits");
        }
        var leastSigBits = jsonReader.nextLong();
        jsonReader.endObject();
        return new UUID(mostSigBits, leastSigBits);
    }
}
