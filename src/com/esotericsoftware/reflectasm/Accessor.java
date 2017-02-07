package com.esotericsoftware.reflectasm;

public interface Accessor<T> {
    abstract public ClassInfo getInfo();

    abstract public T newInstanceWithIndex(int constructorIndex, Object... args);

    abstract public T newInstance();

    abstract public Object invokeWithIndex(T instance, int methodIndex, Object... args);

    abstract public void set(T instance, int fieldIndex, Object value);

    abstract public Object get(T instance, int fieldIndex);
}