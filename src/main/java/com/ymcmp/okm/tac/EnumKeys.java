package com.ymcmp.okm.tac;

import java.util.Arrays;

public final class EnumKeys implements Value {

    public final String[] keys;

    public EnumKeys(String... keys) {
        this.keys = keys;
    }

    public int getIndexOfKey(String key) {
        for (int i = 0; i < keys.length; ++i) {
            if (keys[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public String toString() {
        return Arrays.toString(keys);
    }
}