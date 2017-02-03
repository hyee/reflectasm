package com.esotericsoftware.reflectasm;

@SuppressWarnings("UnusedDeclaration")
public class FieldAccess {
    public final ClassAccess classAccess;
    public final ClassAccess.Accessor accessor;

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