package com.esotericsoftware.reflectasm;

@SuppressWarnings("UnusedDeclaration")
public class FieldAccess {
    public final ClassAccess accessor;
    public final ClassInfo classInfo;

    @Override
    public String toString() {
        return accessor.toString();
    }

    protected FieldAccess(ClassAccess accessor) {
        this.accessor = accessor;
        this.classInfo = accessor.getInfo();
    }

    public int getIndex(String fieldName) {
        return accessor.indexOfField(fieldName);
    }

    public void set(Object instance, String fieldName, Object value) {
        accessor.set(instance, fieldName, value);
    }

    public Object get(Object instance, String fieldName) {
        return accessor.get(instance, fieldName);
    }

    public String[] getFieldNames() {
        return accessor.getFieldNames();
    }

    public Class[] getFieldTypes() {
        return accessor.getFieldTypes();
    }

    public int getFieldCount() {
        return accessor.getFieldCount();
    }

    public void set(Object instance, int fieldIndex, Object value) {
        accessor.set(instance, fieldIndex, value);
    }

    public void setBoolean(Object instance, int fieldIndex, boolean value) {
        accessor.setBoolean(instance, fieldIndex, value);
    }

    public void setByte(Object instance, int fieldIndex, byte value) {
        accessor.setByte(instance, fieldIndex, value);
    }

    public void setShort(Object instance, int fieldIndex, short value) {
        accessor.setShort(instance, fieldIndex, value);
    }

    public void setInt(Object instance, int fieldIndex, int value) {
        accessor.setInt(instance, fieldIndex, value);
    }

    public void setLong(Object instance, int fieldIndex, long value) {
        accessor.setLong(instance, fieldIndex, value);
    }

    public void setDouble(Object instance, int fieldIndex, double value) {
        accessor.setDouble(instance, fieldIndex, value);
    }

    public void setFloat(Object instance, int fieldIndex, float value) {
        accessor.setFloat(instance, fieldIndex, value);
    }

    public void setChar(Object instance, int fieldIndex, char value) {
        accessor.setChar(instance, fieldIndex, value);
    }

    public Object get(Object instance, int fieldIndex) {
        return accessor.get(instance, fieldIndex);
    }

    public <T> T get(Object instance, int fieldIndex, Class<T> clz) {
        return (T) (accessor.get(instance, fieldIndex, clz));
    }

    public <T> T get(Object instance, String fieldName, Class<T> clz) {
        return (T) (accessor.get(instance, fieldName, clz));
    }

    public char getChar(Object instance, int fieldIndex) {
        return accessor.getChar(instance, fieldIndex);
    }

    public boolean getBoolean(Object instance, int fieldIndex) {
        return accessor.getBoolean(instance, fieldIndex);
    }

    public byte getByte(Object instance, int fieldIndex) {
        return accessor.getByte(instance, fieldIndex);
    }

    public short getShort(Object instance, int fieldIndex) {
        return accessor.getShort(instance, fieldIndex);
    }

    public int getInt(Object instance, int fieldIndex) {
        return accessor.getInt(instance, fieldIndex);
    }

    public long getLong(Object instance, int fieldIndex) {
        return accessor.getLong(instance, fieldIndex);
    }

    public double getDouble(Object instance, int fieldIndex) {
        return accessor.getDouble(instance, fieldIndex);
    }

    public float getFloat(Object instance, int fieldIndex) {
        return accessor.getFloat(instance, fieldIndex);
    }

    public String getString(Object instance, int fieldIndex) {
        return (String) get(instance, fieldIndex);
    }

    static public FieldAccess get(Class type, String... dumpFile) {
        return new FieldAccess(ClassAccess.get(type, dumpFile));
    }
}