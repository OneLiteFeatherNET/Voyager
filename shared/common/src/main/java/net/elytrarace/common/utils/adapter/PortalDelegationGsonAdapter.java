package net.elytrarace.common.utils.adapter;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.elytrarace.common.map.model.FilePortalDTO;
import net.elytrarace.common.map.model.PortalDTO;

import java.io.IOException;

public class PortalDelegationGsonAdapter extends TypeAdapter<PortalDTO> {

    private final TypeAdapter<FilePortalDTO> delegate;

    public PortalDelegationGsonAdapter() {
        this.delegate = new Gson().getAdapter(FilePortalDTO.class);
    }

    @Override
    public void write(JsonWriter out, PortalDTO value) throws IOException {
           delegate.write(out, (FilePortalDTO) value);
    }

    @Override
    public PortalDTO read(JsonReader in) throws IOException {
        return delegate.read(in);
    }
}
