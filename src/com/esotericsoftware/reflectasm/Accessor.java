package com.esotericsoftware.reflectasm;

interface Accessor<T> {
    abstract public ClassInfo getInfo();

    abstract public void setInfo(ClassInfo info);

    abstract public T newInstanceWithIndex(int constructorIndex, Object... args);

    abstract public T newInstance();

    abstract public Object invoke(T instance, int methodIndex, Object... args);

    abstract public void set(T instance, int fieldIndex, Object value);

    abstract public Object get(T instance, int fieldIndex);
}