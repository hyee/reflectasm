package com.esotericsoftware.reflectasm;

interface Accessor {
    abstract public ClassInfo getInfo();

    abstract public void setInfo(ClassInfo info);

    abstract public Object newInstanceWithIndex(int constructorIndex, Object... args);

    abstract public Object newInstance();

    abstract public Object invoke(Object instance, int methodIndex, Object... args);

    abstract public void set(Object instance, int fieldIndex, Object value);

    abstract public Object get(Object instance, int fieldIndex);
}