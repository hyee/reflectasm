package com.esotericsoftware.reflectasm;

import java.lang.reflect.Constructor;

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond"})
public class ConstructorAccess<T> {
    public final ClassAccess accessor;
    public final ClassInfo classInfo;

    @Override
    public String toString() {
        return accessor.toString();
    }

    protected ConstructorAccess(ClassAccess accessor) {
        this.accessor = accessor;
        this.classInfo = accessor.getInfo();
    }

    public boolean isNonStaticMemberClass() {
        return accessor.isNonStaticMemberClass();
    }

    public int getIndex(Class... paramTypes) {
        return accessor.indexOfMethod(ClassAccess.CONSTRUCTOR_ALIAS, paramTypes);
    }

    public int getIndex(int paramCount) {
        return accessor.indexOfMethod(ClassAccess.CONSTRUCTOR_ALIAS, paramCount);
    }

    public int getIndex(Constructor<?> constructor) {
        return accessor.indexOfConstructor(constructor);
    }

    /**
     * Constructor for top-level classes and static nested classes.
     * <p/>
     * If the underlying class is a inner (non-static nested) class, a new instance will be created using <code>null</code> as the
     * this$0 synthetic reference. The instantiated object will work as long as it actually don't use any member variable or method
     * fron the enclosing instance.
     */
    @SuppressWarnings("unchecked")
    public T newInstance() {
        return (T) accessor.newInstance();
    }

    public T newInstanceWithIndex(int constructorIndex, Object... args) {
        return (T) accessor.newInstanceWithIndex(constructorIndex, args);
    }

    public T newInstanceWithTypes(Class[] paramTypes, Object... args) {
        return (T) accessor.newInstanceWithTypes(paramTypes, args);
    }

    public T newInstance(Object... args) {
        return (T) accessor.newInstance(args);
    }

    static public <T> ConstructorAccess<T> get(Class<T> type, String... dumpFile) {
        return new ConstructorAccess<T>(ClassAccess.get(type, dumpFile));
    }

}