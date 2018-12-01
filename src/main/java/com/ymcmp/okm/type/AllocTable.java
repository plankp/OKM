package com.ymcmp.okm.type;

import java.util.Map;
import java.util.LinkedHashMap;

import com.ymcmp.okm.except.DuplicateSymbolException;

public abstract class AllocTable implements Type {

    protected final LinkedHashMap<String, Type> fields;

    public AllocTable() {
        fields = new LinkedHashMap<>();
    }

    protected AllocTable(LinkedHashMap<String, Type> fields) {
        this.fields = fields;
    }

    @Override
    public abstract AllocTable allocate();

    public int getSize() {
        return fields.values().stream().mapToInt(Type::getSize).sum();
    }

    public void putField(String name, Type type) {
        if (fields.containsKey(name)) {
            throw new DuplicateSymbolException(name);
        }
        fields.put(name, type);
    }

    public int getOffsetOfField(String attr) {
        int offset = 0;
        for (final Map.Entry<String, Type> pair : fields.entrySet()) {
            if (pair.getKey().equals(attr)) {
                break;
            }
            offset += pair.getValue().getSize();
        }
        return offset;
    }

    public Type accessAttribute(String attr) {
        return fields.get(attr);
    }
}