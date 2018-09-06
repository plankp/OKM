package com.ymcmp.okm;

public interface EntryNamingStrategy {

    public String name(Module.Entry entry, String name);
}