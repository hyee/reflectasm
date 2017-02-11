package com.esotericsoftware.reflectasm;

import java.lang.invoke.MethodHandle;

public interface Accessor<ANY> {
    abstract public ClassInfo getInfo();

    abstract public MethodHandle[][] getMethodHandles();

    abstract public <V> ANY newInstanceWithIndex(int constructorIndex, V... args);

    abstract public ANY newInstance();

    abstract public <T, V> T invokeWithIndex(ANY instance, int methodIndex, V... args);

    abstract public <T, V> void set(ANY instance, int fieldIndex, V value);

    abstract public <T> T get(ANY instance, int fieldIndex);
}