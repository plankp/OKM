package com.ymcmp.okm;

import java.io.Serializable;

import java.nio.file.Path;

import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.HashMap;

import com.ymcmp.okm.type.Type;
import com.ymcmp.okm.type.UnaryType;

import com.ymcmp.okm.except.DuplicateSymbolException;

public final class Module implements Serializable {

    private static final long serialVersionUID = 34237523587L;

    public static final class Entry {

        public final Visibility visibility;
        public final Type type;
        public final Path source;

        public Entry(Visibility vis, Type type, Path source) {
            this.visibility = vis;
            this.type = type;
            this.source = source;
        }

        public Entry changeVisibility(Visibility newVis) {
            return new Entry(newVis, this.type, this.source);
        }

        @Override
        public int hashCode() {
            return Objects.hash(visibility, type, source);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null) return false;

            // test type with getClass is safe: type is final
            if (obj.getClass() == Entry.class) {
                final Entry other = (Entry) obj;
                return visibility == other.visibility
                        && type.isSameType(other.type)
                        && Objects.equals(source, other.source);
            }
            return false;
        }
    }

    private static final HashMap<String, Entry> PREDEF_TYPES = new HashMap<>();

    static {
        PREDEF_TYPES.put("byte", new Entry(Visibility.PUBLIC, UnaryType.getType("byte"), null));
        PREDEF_TYPES.put("char", new Entry(Visibility.PUBLIC, UnaryType.getType("char"), null));
        PREDEF_TYPES.put("short", new Entry(Visibility.PUBLIC, UnaryType.getType("short"), null));
        PREDEF_TYPES.put("int", new Entry(Visibility.PUBLIC, UnaryType.getType("int"), null));
        PREDEF_TYPES.put("long", new Entry(Visibility.PUBLIC, UnaryType.getType("long"), null));
        PREDEF_TYPES.put("float", new Entry(Visibility.PUBLIC, UnaryType.getType("float"), null));
        PREDEF_TYPES.put("double", new Entry(Visibility.PUBLIC, UnaryType.getType("double"), null));
        PREDEF_TYPES.put("unit", new Entry(Visibility.PUBLIC, UnaryType.getType("unit"), null));
        PREDEF_TYPES.put("bool", new Entry(Visibility.PUBLIC, UnaryType.getType("bool"), null));
    }

    // No NULL entries allowed!
    private final HashMap<String, Entry> map = new HashMap<>();
    private final HashMap<String, Entry> type = new HashMap<>(PREDEF_TYPES);

    public Set<Map.Entry<String, Module.Entry>> entrySet() {
        return map.entrySet();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public void putAll(final Module mod) {
        for (Map.Entry<String, Module.Entry> ent : mod.entrySet()) {
            this.put(ent.getKey(), ent.getValue());
        }
    }

    public void put(String name, Entry value) {
        final Module.Entry ent = get(name);
        if (ent == null) {
            // Do not overwrite old definition.
            map.put(name, value);
        } else if (!value.source.equals(ent.source)) {
            // Check if the two symbols are from the same place.
            // If they are not, crash due to ambiguity
            throw new DuplicateSymbolException(name);
        }
    }

    public Entry get(String name) {
        return map.get(name);
    }

    public void putType(String name, Entry value) {
        final Module.Entry ent = getType(name);
        if (ent == null) {
            // Do not overwrite old definition.
            type.put(name, value);
        } else if (!value.source.equals(ent.source)) {
            // Check if the two symbols are from the same place.
            // If they are not, crash due to ambiguity
            throw new DuplicateSymbolException(name);
        }
    }

    public Entry getType(String name) {
        return type.get(name);
    }

    public static String makeFuncName(final String name, final String... params) {
        final StringBuilder sb = new StringBuilder();
        sb.append(name).append(':');
        for (final String param : params) {
            sb.append(param).append(':');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return map.keySet().toString();
    }
}