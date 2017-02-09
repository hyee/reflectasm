package com.esotericsoftware.reflectasm;

@SuppressWarnings("UnusedDeclaration")
public class FieldAccess<ANY> {
    public final ClassAccess<ANY> console;
    public final ClassInfo<ANY> classInfo;

    protected FieldAccess(ClassAccess<ANY> console) {
        this.console = console;
        this.classInfo = console.getInfo();
    }

    static public <ANY> FieldAccess access(Class<ANY> type, String... dumpFile) {
        return new FieldAccess(ClassAccess.access(type, dumpFile));
    }

    @Override
    public String toString() {
        return console.toString();
    }

    public int getIndex(String fieldName) {
        return console.indexOfField(fieldName);
    }

    public <T, V> void set(ANY instance, String fieldName, V value) {
        console.set(instance, fieldName, value);
    }

    public <T> T get(ANY instance, String fieldName) {
        return console.get(instance, fieldName);
    }

    public <T, V> void set(ANY instance, int fieldIndex, V value) {
        console.set(instance, fieldIndex, value);
    }

    public void setBoolean(ANY instance, int fieldIndex, boolean value) {
        console.setBoolean(instance, fieldIndex, value);
    }

    public void setByte(ANY instance, int fieldIndex, byte value) {
        console.setByte(instance, fieldIndex, value);
    }

    public void setShort(ANY instance, int fieldIndex, short value) {
        console.setShort(instance, fieldIndex, value);
    }

    public void setInt(ANY instance, int fieldIndex, int value) {
        console.setInt(instance, fieldIndex, value);
    }

    public void setLong(ANY instance, int fieldIndex, long value) {
        console.setLong(instance, fieldIndex, value);
    }

    public void setDouble(ANY instance, int fieldIndex, double value) {
        console.setDouble(instance, fieldIndex, value);
    }

    public void setFloat(ANY instance, int fieldIndex, float value) {
        console.setFloat(instance, fieldIndex, value);
    }

    public void setChar(ANY instance, int fieldIndex, char value) {
        console.setChar(instance, fieldIndex, value);
    }

    public <T> T get(ANY instance, int fieldIndex) {
        return console.get(instance, fieldIndex);
    }

    public <T> T get(ANY instance, int fieldIndex, Class<T> clz) {
        return console.get(instance, fieldIndex, clz);
    }

    public <T> T get(ANY instance, String fieldName, Class<T> clz) {
        return console.get(instance, fieldName, clz);
    }

    public char getChar(ANY instance, int fieldIndex) {
        return console.getChar(instance, fieldIndex);
    }

    public boolean getBoolean(ANY instance, int fieldIndex) {
        return console.getBoolean(instance, fieldIndex);
    }

    public byte getByte(ANY instance, int fieldIndex) {
        return console.getByte(instance, fieldIndex);
    }

    public short getShort(ANY instance, int fieldIndex) {
        return console.getShort(instance, fieldIndex);
    }

    public int getInt(ANY instance, int fieldIndex) {
        return console.getInt(instance, fieldIndex);
    }

    public long getLong(ANY instance, int fieldIndex) {
        return console.getLong(instance, fieldIndex);
    }

    public double getDouble(ANY instance, int fieldIndex) {
        return console.getDouble(instance, fieldIndex);
    }

    public float getFloat(ANY instance, int fieldIndex) {
        return console.getFloat(instance, fieldIndex);
    }

    public String getString(ANY instance, int fieldIndex) {
        return (String) get(instance, fieldIndex);
    }

    public String[] getFieldNames() {return classInfo.fieldNames;}

    public Class[] getFieldTypes() {
        return classInfo.fieldTypes;
    }

    public int getFieldCount() {
        return classInfo.fieldCount;
    }
}