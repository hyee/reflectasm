package com.esotericsoftware.reflectasm;

@SuppressWarnings("UnusedDeclaration")
public class FieldAccess<ANY> {
    public final ClassAccess<ANY> accessor;
    public final ClassInfo classInfo;

    @Override
    public String toString() {
        return accessor.toString();
    }

    protected FieldAccess(ClassAccess<ANY> accessor) {
        this.accessor = accessor;
        this.classInfo = accessor.getInfo();
    }

    public int getIndex(String fieldName) {
        return accessor.indexOfField(fieldName);
    }

    public void set(ANY instance, String fieldName, Object value) {
        accessor.set(instance, fieldName, value);
    }

    public Object get(ANY instance, String fieldName) {
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

    public void set(ANY instance, int fieldIndex, Object value) {
        accessor.set(instance, fieldIndex, value);
    }

    public void setBoolean(ANY instance, int fieldIndex, boolean value) {
        accessor.setBoolean(instance, fieldIndex, value);
    }

    public void setByte(ANY instance, int fieldIndex, byte value) {
        accessor.setByte(instance, fieldIndex, value);
    }

    public void setShort(ANY instance, int fieldIndex, short value) {
        accessor.setShort(instance, fieldIndex, value);
    }

    public void setInt(ANY instance, int fieldIndex, int value) {
        accessor.setInt(instance, fieldIndex, value);
    }

    public void setLong(ANY instance, int fieldIndex, long value) {
        accessor.setLong(instance, fieldIndex, value);
    }

    public void setDouble(ANY instance, int fieldIndex, double value) {
        accessor.setDouble(instance, fieldIndex, value);
    }

    public void setFloat(ANY instance, int fieldIndex, float value) {
        accessor.setFloat(instance, fieldIndex, value);
    }

    public void setChar(ANY instance, int fieldIndex, char value) {
        accessor.setChar(instance, fieldIndex, value);
    }

    public Object get(ANY instance, int fieldIndex) {
        return accessor.get(instance, fieldIndex);
    }

    public <T> T get(ANY instance, int fieldIndex, Class<T> clz) {
        return (T) (accessor.get(instance, fieldIndex, clz));
    }

    public <T> T get(ANY instance, String fieldName, Class<T> clz) {
        return (T) (accessor.get(instance, fieldName, clz));
    }

    public char getChar(ANY instance, int fieldIndex) {
        return accessor.getChar(instance, fieldIndex);
    }

    public boolean getBoolean(ANY instance, int fieldIndex) {
        return accessor.getBoolean(instance, fieldIndex);
    }

    public byte getByte(ANY instance, int fieldIndex) {
        return accessor.getByte(instance, fieldIndex);
    }

    public short getShort(ANY instance, int fieldIndex) {
        return accessor.getShort(instance, fieldIndex);
    }

    public int getInt(ANY instance, int fieldIndex) {
        return accessor.getInt(instance, fieldIndex);
    }

    public long getLong(ANY instance, int fieldIndex) {
        return accessor.getLong(instance, fieldIndex);
    }

    public double getDouble(ANY instance, int fieldIndex) {
        return accessor.getDouble(instance, fieldIndex);
    }

    public float getFloat(ANY instance, int fieldIndex) {
        return accessor.getFloat(instance, fieldIndex);
    }

    public String getString(ANY instance, int fieldIndex) {
        return (String) get(instance, fieldIndex);
    }

    static public <ANY> FieldAccess get(Class<ANY> type, String... dumpFile) {
        return new FieldAccess(ClassAccess.get(type, dumpFile));
    }
}