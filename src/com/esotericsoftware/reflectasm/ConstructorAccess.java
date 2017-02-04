package com.esotericsoftware.reflectasm;

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond"})
public class ConstructorAccess<T> {
    public final ClassAccess classAccess;
    public final ClassAccess.Accessor accessor;

    @Override
    public String toString() {
        return classAccess.toString();
    }

    protected ConstructorAccess(ClassAccess classAccess) {
        this.classAccess = classAccess;
        accessor = classAccess.accessor;
    }

    public boolean isNonStaticMemberClass() {
        return classAccess.isNonStaticMemberClass();
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

    public int getIndex(Class... paramTypes) {
        return classAccess.indexOfMethod(ClassAccess.CONSTRUCTOR_ALIAS, paramTypes);
    }

    public T newInstance(int constructorIndex,Object ... args) {
        return (T) classAccess.newInstance(constructorIndex,args);
    }

    public T newInstance(Object ... args) {
        return (T) classAccess.newInstance(args);
    }

    /**
     * Constructor for inner classes (non-static nested classes).
     *
     * @param enclosingInstance The instance of the enclosing type to which this inner instance is related to (assigned to its
     *                          synthetic this$0 field).
     */
    @SuppressWarnings("unchecked")
    public T newInstance(Object enclosingInstance) {
        return (T) accessor.newInstance(0, enclosingInstance);
    }

    static public <T> ConstructorAccess<T> get(Class<T> type, String... dumpFile) {
        return new ConstructorAccess<T>(ClassAccess.get(type, dumpFile));
    }

}