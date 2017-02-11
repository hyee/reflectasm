package com.esotericsoftware.reflectasm;

import java.lang.reflect.Constructor;

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond"})
public class ConstructorAccess<ANY> {
    public final ClassAccess<ANY> console;
    public final ClassInfo<ANY> classInfo;

    protected ConstructorAccess(ClassAccess<ANY> console) {
        this.console = console;
        this.classInfo = console.getInfo();
    }

    static public <ANY> ConstructorAccess access(Class<ANY> type, String... dumpFile) {
        return new ConstructorAccess<ANY>(ClassAccess.access(type, dumpFile));
    }

    @Override
    public String toString() {
        return console.toString();
    }

    public boolean isNonStaticMemberClass() {
        return console.isNonStaticMemberClass();
    }

    public int getIndex(Class... paramTypes) {
        return console.indexOfMethod(null, ClassAccess.NEW, paramTypes);
    }

    public int getIndex(int paramCount) {
        return console.indexOfMethod(ClassAccess.NEW, paramCount);
    }

    public int getIndex(Constructor<ANY> constructor) {
        return console.indexOfConstructor(constructor);
    }

    /**
     * Constructor for top-level classes and static nested classes.
     * <p/>
     * If the underlying class is a inner (non-static nested) class, a new instance will be created using <code>null</code> as the
     * this$0 synthetic reference. The instantiated object will work as long as it actually don't use any member variable or method
     * fron the enclosing instance.
     */
    @SuppressWarnings("unchecked")
    public ANY newInstance() {
        return console.newInstance();
    }

    public <V> ANY newInstanceWithIndex(int constructorIndex, V... args) {
        return console.newInstanceWithIndex(constructorIndex, args);
    }

    public <V> ANY newInstanceWithTypes(Class[] paramTypes, V... args) {
        return console.newInstanceWithTypes(paramTypes, args);
    }

    public <V> ANY newInstance(V... args) {
        return console.newInstance(args);
    }

    public Class[][] getParameterTypes() {
        return classInfo.constructorParamTypes;
    }

    public Integer[] getModifiers() {return classInfo.constructorModifiers;}

    public int getConstructorCount() {
        return classInfo.constructorCount;
    }
}