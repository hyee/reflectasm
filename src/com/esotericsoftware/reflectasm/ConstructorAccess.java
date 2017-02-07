package com.esotericsoftware.reflectasm;

import java.lang.reflect.Constructor;

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond"})
public class ConstructorAccess<ANY> {
    public final ClassAccess<ANY> console;
    public final ClassInfo classInfo;

    @Override
    public String toString() {
        return console.toString();
    }

    protected ConstructorAccess(ClassAccess<ANY> console) {
        this.console = console;
        this.classInfo = console.getInfo();
    }

    public boolean isNonStaticMemberClass() {
        return console.isNonStaticMemberClass();
    }

    public int getIndex(Class... paramTypes) {
        return console.indexOfMethod(ClassAccess.CONSTRUCTOR_ALIAS, paramTypes);
    }

    public int getIndex(int paramCount) {
        return console.indexOfMethod(ClassAccess.CONSTRUCTOR_ALIAS, paramCount);
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

    public ANY newInstanceWithIndex(int constructorIndex, Object... args) {
        return console.newInstanceWithIndex(constructorIndex, args);
    }

    public ANY newInstanceWithTypes(Class[] paramTypes, Object... args) {
        return console.newInstanceWithTypes(paramTypes, args);
    }

    public ANY newInstance(Object... args) {
        return console.newInstance(args);
    }

    static public <ANY> ConstructorAccess access(Class<ANY> type, String... dumpFile) {
        return new ConstructorAccess<ANY>(ClassAccess.access(type, dumpFile));
    }
}