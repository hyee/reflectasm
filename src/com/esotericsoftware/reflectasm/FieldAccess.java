package com.esotericsoftware.reflectasm;

@SuppressWarnings("UnusedDeclaration")
public class FieldAccess {
    public final ClassAccess classAccess;
    public final Accessor accessor;

    @Override
    public String toString() {
        return classAccess.toString();
    }

    protected FieldAccess(ClassAccess classAccess) {
        this.classAccess = classAccess;
        accessor = classAccess.accessor;
    }

    public int getIndex(String fieldName) {
        return classAccess.indexOfField(fieldName);
    }

    public void set(Object instance, String fieldName, Object value) {
        set(instance, getIndex(fieldName), value);
    }

    public Object get(Object instance, String fieldName) {
        return get(instance, getIndex(fieldName));
    }

    public String[] getFieldNames() {
        return classAccess.getFieldNames();
    }

    public Class[] getFieldTypes() {
        return classAccess.getFieldTypes();
    }

    public int getFieldCount() {
        return classAccess.getFieldCount();
    }

    public void set(Object instance, int fieldIndex, Object value) {
        accessor.set(instance, fieldIndex, value);
    }

    public void setBoolean(Object instance, int fieldIndex, boolean value) {
        classAccess.setBoolean(instance, fieldIndex, value);
    }

    public void setByte(Object instance, int fieldIndex, byte value) {
        classAccess.setByte(instance, fieldIndex, value);
    }

    public void setShort(Object instance, int fieldIndex, short value) {
        classAccess.setShort(instance, fieldIndex, value);
    }

    public void setInt(Object instance, int fieldIndex, int value) {
        classAccess.setInt(instance, fieldIndex, value);
    }

    public void setLong(Object instance, int fieldIndex, long value) {
        classAccess.setLong(instance, fieldIndex, value);
    }

    public void setDouble(Object instance, int fieldIndex, double value) {
        classAccess.setDouble(instance, fieldIndex, value);
    }

    public void setFloat(Object instance, int fieldIndex, float value) {
        classAccess.setFloat(instance, fieldIndex, value);
    }

    public void setChar(Object instance, int fieldIndex, char value) {
        classAccess.setChar(instance, fieldIndex, value);
    }

    public Object get(Object instance, int fieldIndex) {
        return accessor.get(instance, fieldIndex);
    }

    public <T> T get(Object instance, int fieldIndex, Class<T> clz) {
        return (T) (classAccess.get(instance, fieldIndex, clz));
    }

    public char getChar(Object instance, int fieldIndex) {
        return classAccess.getChar(instance, fieldIndex);
    }

    public boolean getBoolean(Object instance, int fieldIndex) {
        return classAccess.getBoolean(instance, fieldIndex);
    }

    public byte getByte(Object instance, int fieldIndex) {
        return classAccess.getByte(instance, fieldIndex);
    }

    public short getShort(Object instance, int fieldIndex) {
        return classAccess.getShort(instance, fieldIndex);
    }

    public int getInt(Object instance, int fieldIndex) {
        return classAccess.getInt(instance, fieldIndex);
    }

    public long getLong(Object instance, int fieldIndex) {
        return classAccess.getLong(instance, fieldIndex);
    }

    public double getDouble(Object instance, int fieldIndex) {
        return classAccess.getDouble(instance, fieldIndex);
    }

    public float getFloat(Object instance, int fieldIndex) {
        return classAccess.getFloat(instance, fieldIndex);
    }

    public String getString(Object instance, int fieldIndex) {
        return (String) get(instance, fieldIndex);
    }

    static public FieldAccess get(Class type, String... dumpFile) {
        return new FieldAccess(ClassAccess.get(type, dumpFile));
    }
}