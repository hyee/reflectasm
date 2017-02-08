package com.esotericsoftware.reflectasm;

public interface Accessor<ANY> {
    abstract public ClassInfo getInfo();

    abstract public ANY newInstanceWithIndex(int constructorIndex, Object... args);

    abstract public ANY newInstance();

    abstract public <T> T invokeWithIndex(ANY instance, int methodIndex, Object... args);

    abstract public void set(ANY instance, int fieldIndex, Object value);

    abstract public <T> T get(ANY instance, int fieldIndex);
}