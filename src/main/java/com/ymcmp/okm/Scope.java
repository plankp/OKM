package com.ymcmp.okm;

import java.io.Serializable;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;

import com.ymcmp.okm.type.Type;

import com.ymcmp.okm.except.DuplicateSymbolException;

public final class Scope implements Serializable {

    private static final long serialVersionUID = 1209357245L;

    private final ArrayDeque<HashMap<String, Type>> locals = new ArrayDeque<>();

    private final Module environment;

    public final String functionName;

    public Scope(String functionName, Module env) {
        this.functionName = functionName;
        this.environment = env;
    }

    public void shift() {
        // HashMap is added to the front of the deque
        this.locals.push(new LinkedHashMap<>());
    }

    public void unshift() {
        this.locals.pop();
    }

    public Set<String> getCurrentLocals() {
        return locals.peek().keySet();
    }

    public void put(String name, Type type) {
        final HashMap<String, Type> map = this.locals.peek();
        if (map.get(type) != null) {
            throw new DuplicateSymbolException(name);
        }
        map.put(name, type);
    }

    public Type get(String name) {
        // Search for the inner-most scope level first
        for (final HashMap<String, Type> map : locals) {
            final Type t = map.get(name);
            if (t != null) {
                return t;
            }
        }

        // Search from the environment if not found in scope
        final Module.Entry fromEnv = environment.get(name);
        return fromEnv == null ? null : fromEnv.type;
    }

    public int getAssociatedDepth(String name) {
        int depth = locals.size();
        for (final HashMap<String, Type> map : locals) {
            final Type t = map.get(name);
            if (t != null) {
                return depth;
            }

            // Decrease depth
            --depth;
        }

        // Search from the environment if not found in scope
        return environment.get(name) == null ? -1 : 0;
    }

    public String getProcessedName(EntryNamingStrategy strat, String name) {
        final int depth = getAssociatedDepth(name);
        if (depth < 0) {
            // Error!
            return null;
        }

        if (depth == 0) {
            // This means it is module level
            // prefix with @ and add source module info
            if (strat == null) {
                return "@" + name;
            }
            return strat.name(environment.get(name), name);
        } else {
            // This means it is local
            // Unshift one depth and prefix with $
            return "$" + name + "_" + (depth - 1);
        }
    }
}