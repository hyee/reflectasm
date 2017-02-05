package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.util.NumberUtils;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.Type;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.esotericsoftware.reflectasm.util.NumberUtils.*;
import static java.lang.reflect.Modifier.isStatic;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond", "ConstantConditions", "Unsafe"})
public class ClassAccess implements Accessor {
    public final static int MODIFIER_VARARGS = 262144;
    public final static String CONSTRUCTOR_ALIAS = "<new>";
    public final static String CONSTRUCTOR_ALIAS_ESCAPE = "\3\2\1" + CONSTRUCTOR_ALIAS + "\1\2\3";
    public static String ACCESS_CLASS_PREFIX = "asm.";
    public final Accessor accessor;
    private ClassInfo classInfo;

    public static boolean IS_CACHED = true;
    public static boolean IS_STRICT_CONVERT = false;
    public static boolean IS_DEBUG = false;
    static ConcurrentHashMap<Class, ClassAccess> cachedAccessors = new ConcurrentHashMap();
    static ConcurrentHashMap<Integer, Integer> methoIndexer = new ConcurrentHashMap();
    static final String thisPath = Type.getInternalName(ClassAccess.class);
    static final String accessorPath = Type.getInternalName(Accessor.class);
    static final String classInfoPath = Type.getInternalName(ClassInfo.class);

    static {
        if (System.getProperty("reflectasm.is_cache", "true").toLowerCase().equals("false")) IS_CACHED = false;
        if (System.getProperty("reflectasm.is_debug", "false").toLowerCase().equals("true")) IS_DEBUG = true;
        if (System.getProperty("reflectasm.is_strict_convert", "false").toLowerCase().equals("true"))
            IS_STRICT_CONVERT = true;
    }

    protected ClassAccess(ClassInfo info, Accessor accessor) {
        this.classInfo = info;
        this.accessor = accessor;
    }

    static <M extends Member> Map<M, Integer> dump(List<M> members) {
        Map<M, Integer> map = new HashMap<M, Integer>();
        int i = -1;
        for (M member : members) {
            map.put(member, ++i);
        }
        return Collections.unmodifiableMap(map);
    }

    public static boolean isVarArgs(int modifier) {
        return (modifier & MODIFIER_VARARGS) != 0;
    }

    public static int activeAccessClassLoaders() {return AccessClassLoader.activeAccessClassLoaders();}

    /**
     * Indexing the fields/methods/constructors for the underlying class
     *
     * @param info
     */
    public static void buildIndex(ClassInfo info) {
        if (info == null || info.attrIndex != null) return;
        info.attrIndex = new HashMap<>();
        String[] constructors = new String[info.constructorParamTypes.length];
        Arrays.fill(constructors, CONSTRUCTOR_ALIAS);
        String[][] attrs = new String[][]{info.fieldNames, info.methodNames, constructors};
        HashMap<String, ArrayList<Integer>> map = new HashMap<>();
        for (int i = 0; i < attrs.length; i++) {
            for (int j = 0; j < attrs[i].length; j++) {
                String attr = Character.toString((char) (i + 1)) + attrs[i][j];
                if (!map.containsKey(attr)) map.put(attr, new ArrayList<Integer>());
                map.get(attr).add(j);
            }
        }
        for (String key : map.keySet()) {
            Integer[] indexes = map.get(key).toArray(new Integer[]{});
            info.attrIndex.put(key, indexes);
        }
    }

    /**
     * @param type     Target class for reflection
     * @param dumpFile Optional to specify the path/directory to dump the reflection class over the target class
     * @return A dynamic object that wraps the target class
     */
    public static ClassAccess get(Class type, String... dumpFile) {
        String className = type.getName();
        final String accessClassName = ACCESS_CLASS_PREFIX + className;
        Class accessClass;
        Accessor accessor;
        ClassAccess self;
        final AccessClassLoader loader = AccessClassLoader.get(type);
        try {
            if (IS_CACHED && cachedAccessors.containsKey(type)) return cachedAccessors.get(type);
            try {
                accessClass = loader.loadClass(accessClassName);
            } catch (ClassNotFoundException e) {
                synchronized (loader) {
                    accessClass = loader.loadClass(accessClassName);
                }
            }
            accessor = (Accessor) accessClass.newInstance();
            self = new ClassAccess(accessor.getInfo(), accessor);
            if (IS_CACHED) cachedAccessors.put(type, self);
            return self;
        } catch (ClassNotFoundException e) {
        } catch (Exception ex) {
            throw new RuntimeException("Error constructing method access class: " + accessClassName, ex);
        }

        ArrayList<Method> methods = new ArrayList<Method>();
        ArrayList<Constructor<?>> constructors = new ArrayList<Constructor<?>>();
        ArrayList<Field> fields = new ArrayList<Field>();
        collectMembers(type, methods, fields, constructors);

        ClassInfo info = new ClassInfo();
        info.methods = dump(methods);
        info.fields = dump(fields);
        info.constructors = dump(constructors);
        int n = methods.size();
        info.methodModifiers = new Integer[n];
        info.methodParamTypes = new Class[n][];
        info.returnTypes = new Class[n];
        info.methodNames = new String[n];
        info.baseClass = type;
        for (int i = 0; i < n; i++) {
            Method m = methods.get(i);
            info.methodModifiers[i] = m.getModifiers();
            if (m.isVarArgs()) info.methodModifiers[i] += MODIFIER_VARARGS;
            info.methodParamTypes[i] = m.getParameterTypes();
            info.returnTypes[i] = m.getReturnType();
            info.methodNames[i] = m.getName();
        }
        n = constructors.size();
        info.constructorModifiers = new Integer[n];
        info.constructorParamTypes = new Class[n][];
        for (int i = 0; i < n; i++) {
            Constructor<?> c = constructors.get(i);
            info.constructorModifiers[i] = c.getModifiers();
            if (c.isVarArgs()) info.methodModifiers[i] += MODIFIER_VARARGS;
            info.constructorParamTypes[i] = c.getParameterTypes();
        }
        n = fields.size();
        info.fieldModifiers = new Integer[n];
        info.fieldNames = new String[n];
        info.fieldTypes = new Class[n];
        for (int i = 0; i < n; i++) {
            Field f = fields.get(i);
            info.fieldNames[i] = f.getName();
            info.fieldTypes[i] = f.getType();
            info.fieldModifiers[i] = f.getModifiers();
        }
        //Remove "type.getEnclosingClass()==null" due to may trigger error
        info.isNonStaticMemberClass = type.isMemberClass() && !isStatic(type.getModifiers());

        String accessClassNameInternal = accessClassName.replace('.', '/');
        String classNameInternal = className.replace('.', '/');
        final byte[] bytes = byteCode(info, methods, fields, accessClassNameInternal, classNameInternal);
        if (dumpFile.length > 0) try {
            File f = new File(dumpFile[0]);
            if (!f.exists()) {
                if (!dumpFile[0].endsWith(".class")) f.createNewFile();
                else f.mkdir();
            }
            if (f.isDirectory()) {
                f = new File(f.getCanonicalPath() + File.separator + accessClassName + ".class");
            }
            try (FileOutputStream writer = new FileOutputStream(f)) {
                writer.write(bytes);
                writer.flush();
                System.out.println("Class saved to " + f.getCanonicalPath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            accessClass = UnsafeHolder.theUnsafe.defineClass(accessClassName, bytes, 0, bytes.length, loader, type.getProtectionDomain());

        } catch (Throwable ignored1) {
            accessClass = loader.defineClass(accessClassName, bytes);
        }
        try {
            accessor = (Accessor) accessClass.newInstance();
            info.attrIndex = accessor.getInfo().attrIndex;
            accessor.setInfo(info);
            self = new ClassAccess(info, accessor);
            if (IS_CACHED) cachedAccessors.put(type, self);
            return new ClassAccess(info, accessor);
        } catch (Exception ex) {
            throw new RuntimeException("Error constructing method access class: " + accessClassName, ex);
        }

    }

    private static <E extends Member> void addNonPrivate(List<E> list, E[] arr) {
        Collections.addAll(list, arr);
    }

    private static void recursiveAddInterfaceMethodsToList(Class<?> interfaceType, List<Method> methods) {
        addNonPrivate(methods, interfaceType.getDeclaredMethods());
        for (Class nextInterface : interfaceType.getInterfaces()) {
            recursiveAddInterfaceMethodsToList(nextInterface, methods);
        }
    }

    private static void collectMembers(ClassInfo info, Class type) {
        ArrayList<Method> methods = new ArrayList<Method>();
        ArrayList<Constructor<?>> constructors = new ArrayList<Constructor<?>>();
        ArrayList<Field> fields = new ArrayList<Field>();
        collectMembers(type, methods, fields, constructors);
        int n = methods.size();
        info.methods = dump(methods);
        info.fields = dump(fields);
        info.constructors = dump(constructors);
    }

    private static void collectMembers(Class<?> type, List<Method> methods, List<Field> fields, List<Constructor<?>> constructors) {
        if (type.isInterface()) {
            recursiveAddInterfaceMethodsToList(type, methods);
            return;
        }
        boolean search = true;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            //if (isPrivate(constructor.getModifiers())) continue;
            int length = constructor.getParameterTypes().length;
            if (search) {
                switch (length) {
                    case 0:
                        constructors.add(0, constructor);
                        search = false;
                        break;
                    case 1:
                        constructors.add(0, constructor);
                        break;
                    default:
                        constructors.add(constructor);
                        break;
                }
            }
        }
        Class nextClass = type;
        while (nextClass != Object.class) {
            addNonPrivate(fields, nextClass.getDeclaredFields());
            addNonPrivate(methods, nextClass.getDeclaredMethods());
            nextClass = nextClass.getSuperclass();
        }
    }

    private static void insertArray(MethodVisitor mv, Object[] array, String attrName, String accessClassNameInternal) {
        if (attrName != null) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, accessClassNameInternal, "classInfo", Type.getDescriptor(ClassInfo.class));
        }
        mv.visitIntInsn(BIPUSH, array.length);
        mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(array.getClass().getComponentType()));
        for (int i = 0; i < array.length; i++) {
            mv.visitInsn(DUP);
            mv.visitIntInsn(BIPUSH, i);
            if (array[i].getClass().isArray()) insertArray(mv, (Object[]) array[i], null, null);
            else if (array[i] instanceof Class) {
                Class clz = (Class) array[i];
                if (clz.isPrimitive())
                    mv.visitFieldInsn(GETSTATIC, Type.getInternalName(namePrimitiveMap.get(clz.getName())), "TYPE", "Ljava/lang/Class;");
                else mv.visitLdcInsn(Type.getType(clz));
            } else if (array[i] instanceof String) {
                mv.visitLdcInsn((String) array[i]);
            } else {
                mv.visitIntInsn(SIPUSH, (Integer) array[i]);
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            }
            mv.visitInsn(AASTORE);
        }
        if (attrName != null)
            mv.visitFieldInsn(PUTFIELD, classInfoPath, attrName, Type.getInternalName(array.getClass()));
    }

    /**
     * Build ClassInfo of the underlying class while contructing
     *
     * @param info                    ClassInfo of the underlying class
     * @param cw                      ClassWriter
     * @param accessClassNameInternal The class name of the wrapper class
     * @param classNameInternal       The class name of the underlying class
     */
    private static void insertClassInfo(ClassInfo info, ClassWriter cw, String accessClassNameInternal, String classNameInternal) {
        final String baseName = "sun/reflect/MagicAccessorImpl";
        final String clzInfoDesc = Type.getDescriptor(ClassInfo.class);
        cw.visit(V1_1, ACC_PUBLIC + ACC_SUPER, accessClassNameInternal, null, baseName, new String[]{accessorPath});

        FieldVisitor fv = cw.visitField(0, "classInfo", clzInfoDesc, null, null);
        fv.visitEnd();

        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC, "getInfo", "()" + clzInfoDesc, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, accessClassNameInternal, "classInfo", clzInfoDesc);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "setInfo", "(" + clzInfoDesc + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitFieldInsn(PUTFIELD, accessClassNameInternal, "classInfo", clzInfoDesc);
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, baseName, "<init>", "()V");

        //Define class attributes(fields/methods/constructors)
        mv.visitVarInsn(ALOAD, 0);
        mv.visitTypeInsn(NEW, classInfoPath);
        mv.visitInsn(DUP);
        mv.visitMethodInsn(INVOKESPECIAL, classInfoPath, "<init>", "()V", false);
        mv.visitFieldInsn(PUTFIELD, accessClassNameInternal, "classInfo", clzInfoDesc);

        insertArray(mv, info.methodNames, "methodNames", accessClassNameInternal);
        insertArray(mv, info.methodParamTypes, "methodParamTypes", accessClassNameInternal);
        insertArray(mv, info.returnTypes, "returnTypes", accessClassNameInternal);
        insertArray(mv, info.methodModifiers, "methodModifiers", accessClassNameInternal);
        insertArray(mv, info.fieldNames, "fieldNames", accessClassNameInternal);
        insertArray(mv, info.fieldTypes, "fieldTypes", accessClassNameInternal);
        insertArray(mv, info.fieldModifiers, "fieldModifiers", accessClassNameInternal);
        insertArray(mv, info.constructorParamTypes, "constructorParamTypes", accessClassNameInternal);
        insertArray(mv, info.constructorModifiers, "constructorModifiers", accessClassNameInternal);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, accessClassNameInternal, "classInfo", clzInfoDesc);
        mv.visitLdcInsn(Type.getType(info.baseClass));
        mv.visitFieldInsn(PUTFIELD, classInfoPath, "baseClass", "Ljava/lang/Class;");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, accessClassNameInternal, "classInfo", clzInfoDesc);
        mv.visitInsn(info.isNonStaticMemberClass ? ICONST_0 : ICONST_1);
        mv.visitFieldInsn(PUTFIELD, classInfoPath, "isNonStaticMemberClass", "Z");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, accessClassNameInternal, "classInfo", clzInfoDesc);
        mv.visitMethodInsn(INVOKESTATIC, thisPath, "buildIndex", "(" + clzInfoDesc + ")V", false);

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static byte[] byteCode(ClassInfo info, List<Method> methods, List<Field> fields, String accessClassNameInternal, String classNameInternal) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        insertClassInfo(info, cw, accessClassNameInternal, classNameInternal);
        //***********************************************************************************************
        // constructor access
        insertNewInstance(cw, classNameInternal, info);
        insertNewRawInstance(cw, classNameInternal);

        //***********************************************************************************************
        // method access
        insertInvoke(cw, classNameInternal, methods);

        //***********************************************************************************************
        // field access
        insertGetObject(cw, classNameInternal, fields);
        insertSetObject(cw, classNameInternal, fields);

        cw.visitEnd();
        return cw.toByteArray();

    }

    private static void insertNewRawInstance(ClassWriter cw, String classNameInternal) {
        MethodVisitor mv;
        {
            mv = cw.visitMethod(ACC_PUBLIC, "newInstance", "()Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitTypeInsn(NEW, classNameInternal);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, classNameInternal, "<init>", "()V");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static void insertNewInstance(ClassWriter cw, String classNameInternal, ClassInfo info) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "newInstanceWithIndex", "(I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();

        int n = info.constructorModifiers.length;

        if (n != 0) {
            mv.visitVarInsn(ILOAD, 1);
            Label[] labels = new Label[n];
            for (int i = 0; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            StringBuilder buffer = new StringBuilder(128);
            for (int i = 0; i < n; i++) {
                mv.visitLabel(labels[i]);
                if (i == 0) mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{classNameInternal}, 0, null);
                else mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

                mv.visitTypeInsn(NEW, classNameInternal);
                mv.visitInsn(DUP);

                buffer.setLength(0);
                buffer.append('(');

                Class[] paramTypes = info.constructorParamTypes[i];
                for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitIntInsn(BIPUSH, paramIndex);
                    mv.visitInsn(AALOAD);
                    Type paramType = Type.getType(paramTypes[paramIndex]);
                    unbox(mv, paramType);
                    buffer.append(paramType.getDescriptor());
                }
                buffer.append(")V");
                mv.visitMethodInsn(INVOKESPECIAL, classNameInternal, "<init>", buffer.toString());
                mv.visitInsn(ARETURN);
            }
            mv.visitLabel(defaultLabel);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        }
        mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(DUP);
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Constructor not found: ");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        mv.visitVarInsn(ILOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
        mv.visitInsn(ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void insertInvoke(ClassWriter cw, String classNameInternal, List<Method> methods) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "invoke", "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();

        int n = methods.size();

        if (n != 0) {
            mv.visitVarInsn(ILOAD, 2);
            Label[] labels = new Label[n];
            for (int i = 0; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            StringBuilder buffer = new StringBuilder(128);
            for (int i = 0; i < n; i++) {
                Method method = methods.get(i);
                boolean isInterface = method.getDeclaringClass().isInterface();
                boolean isStatic = isStatic(method.getModifiers());

                mv.visitLabel(labels[i]);
                if (i == 0) mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{classNameInternal}, 0, null);
                else mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

                if (!isStatic) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, classNameInternal);
                }

                buffer.setLength(0);
                buffer.append('(');

                String methodName = method.getName();
                Class[] paramTypes = method.getParameterTypes();
                Class returnType = method.getReturnType();
                for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
                    mv.visitVarInsn(ALOAD, 3);
                    mv.visitIntInsn(BIPUSH, paramIndex);
                    mv.visitInsn(AALOAD);
                    Type paramType = Type.getType(paramTypes[paramIndex]);
                    unbox(mv, paramType);
                    buffer.append(paramType.getDescriptor());
                }

                buffer.append(')');
                buffer.append(Type.getDescriptor(returnType));
                final int inv = isInterface ? INVOKEINTERFACE : (isStatic ? INVOKESTATIC : INVOKEVIRTUAL);
                mv.visitMethodInsn(inv, classNameInternal, methodName, buffer.toString());

                final Type retType = Type.getType(returnType);
                box(mv, retType);
                mv.visitInsn(ARETURN);
            }

            mv.visitLabel(defaultLabel);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        }
        mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(DUP);
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Method not found: ");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
        mv.visitInsn(ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    static private void insertSetObject(ClassWriter cw, String classNameInternal, List<Field> fields) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "set", "(Ljava/lang/Object;ILjava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (!fields.isEmpty()) {
            maxStack--;
            Label[] labels = new Label[fields.size()];
            for (int i = 0, n = labels.length; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                Field field = fields.get(i);
                Type fieldType = Type.getType(field.getType());
                boolean st = isStatic(field.getModifiers());

                mv.visitLabel(labels[i]);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                if (!st) {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, classNameInternal);
                }
                mv.visitVarInsn(ALOAD, 3);

                unbox(mv, fieldType);

                mv.visitFieldInsn(st ? PUTSTATIC : PUTFIELD, classNameInternal, field.getName(), fieldType.getDescriptor());
                mv.visitInsn(RETURN);
            }

            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        mv = insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, 4);
        mv.visitEnd();
    }

    static private void insertGetObject(ClassWriter cw, String classNameInternal, List<Field> fields) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "get", "(Ljava/lang/Object;I)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (!fields.isEmpty()) {
            maxStack--;
            Label[] labels = new Label[fields.size()];
            for (int i = 0, n = labels.length; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                Field field = fields.get(i);
                mv.visitLabel(labels[i]);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                if (isStatic(field.getModifiers())) {
                    mv.visitFieldInsn(GETSTATIC, classNameInternal, field.getName(), Type.getDescriptor(field.getType()));
                } else {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, classNameInternal);
                    mv.visitFieldInsn(GETFIELD, classNameInternal, field.getName(), Type.getDescriptor(field.getType()));
                }
                Type fieldType = Type.getType(field.getType());
                box(mv, fieldType);
                mv.visitInsn(ARETURN);
            }
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, 3);
        mv.visitEnd();
    }

    static private MethodVisitor insertThrowExceptionForFieldNotFound(MethodVisitor mv) {
        mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(DUP);
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Field not found: ");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
        mv.visitInsn(ATHROW);
        return mv;
    }

    static private MethodVisitor insertThrowExceptionForFieldType(MethodVisitor mv, String fieldType) {
        mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(DUP);
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Field not declared as " + fieldType + ": ");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V");
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
        mv.visitInsn(ATHROW);
        return mv;
    }

    private static void box(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                mv.visitInsn(ACONST_NULL);
                return;
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
            case Type.FLOAT:
            case Type.LONG:
            case Type.DOUBLE:
                Type clz = Type.getType(namePrimitiveMap.get(type.getClassName()));
                mv.visitMethodInsn(INVOKESTATIC, clz.getInternalName(), "valueOf", "(" + type.getDescriptor() + ")" + clz.getDescriptor());
                break;
        }
    }

    private static void unbox(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
            case Type.FLOAT:
            case Type.LONG:
            case Type.DOUBLE:
                String name = Type.getInternalName(namePrimitiveMap.get(type.getClassName()));
                mv.visitTypeInsn(CHECKCAST, name);
                mv.visitMethodInsn(INVOKEVIRTUAL, name, type.getClassName() + "Value", "()" + type.getDescriptor());
                break;
            case Type.ARRAY:
                mv.visitTypeInsn(CHECKCAST, type.getDescriptor());
                break;
            case Type.OBJECT:
                mv.visitTypeInsn(CHECKCAST, type.getInternalName());
                break;
        }
    }

    @Override
    public String toString() {
        return accessor.toString();
    }

    public boolean isNonStaticMemberClass() {
        return classInfo.isNonStaticMemberClass;
    }

    public Class[][] getConstructorParameterTypes() {
        return classInfo.constructorParamTypes;
    }

    public Integer[] getMethodModifiers() {
        return classInfo.methodModifiers;
    }

    public Integer[] getFieldModifiers() {
        return classInfo.fieldModifiers;
    }

    public Integer[] getConstructorModifiers() {
        return classInfo.constructorModifiers;
    }

    public Integer[] indexesOf(String name, String type) {
        char index;
        if (name.equals(CONSTRUCTOR_ALIAS)) index = 3;
        else switch (type == null ? "" : type.toLowerCase()) {
            case "field":
                index = 1;
                break;
            case "method":
                index = 2;
                break;
            case CONSTRUCTOR_ALIAS:
            case "constructor":
                index = 3;
                break;
            default:
                throw new IllegalArgumentException("No such type " + type);
        }
        final String attr = Character.toString(index) + name;
        if (classInfo.attrIndex.containsKey(attr)) return classInfo.attrIndex.get(attr);

        throw new IllegalArgumentException("Unable to find " + type + ": " + name);
    }

    public Integer indexOf(String name, String type) {
        Integer[] indexes = indexesOf(name, type);
        return indexes.length == 1 ? indexes[0] : null;
    }

    public int indexOfField(String fieldName) {
        return indexesOf(fieldName, "field")[0];
    }

    public int indexOfField(Field field) {
        if (classInfo.fields == null) collectMembers(classInfo, classInfo.baseClass);
        final Integer integer = classInfo.fields.get(field);
        if (integer != null) return integer;
        throw new IllegalArgumentException("Unable to find field: " + field);
    }

    public int indexOfConstructor(Constructor<?> constructor) {
        if (classInfo.fields == null) collectMembers(classInfo, classInfo.baseClass);
        final Integer integer = classInfo.constructors.get(constructor);
        if (null != integer) return integer;
        throw new IllegalArgumentException("Unable to find constructor: " + constructor);
    }

    public int indexOfMethod(Method method) {
        if (classInfo.fields == null) collectMembers(classInfo, classInfo.baseClass);
        final Integer integer = classInfo.methods.get(method);
        if (null != integer) return integer;
        throw new IllegalArgumentException("Unable to find method: " + method);
    }

    /**
     * Returns the index of the first method with the specified name.
     */
    public int indexOfMethod(String methodName) {
        return indexesOf(methodName, "method")[0];
    }

    public Integer getSignature(String methodName, Class... paramTypes) {
        int signature = classInfo.baseClass.hashCode() * 65599 + methodName.hashCode();
        if (paramTypes != null) for (int i = 0; i < paramTypes.length; i++) {
            signature = signature * 65599 + (paramTypes[i] == null ? 0 : paramTypes[i].hashCode());
        }
        return signature;
    }

    private String typesToString(String methodName, Object... argTypes) {
        StringBuilder sb = new StringBuilder(classInfo.baseClass.getName());
        sb.append(".").append(methodName).append("(");
        for (int i = 0; i < argTypes.length; i++) {
            Class clz;
            if (argTypes[i] == null) clz = null;
            else if (argTypes[i] instanceof Class) clz = (Class) argTypes[i];
            else clz = argTypes[i].getClass();
            sb.append(clz == null ? "null" : clz.getCanonicalName().startsWith("java.lang.") ? clz.getSimpleName() : clz.getCanonicalName());
            sb.append(i == argTypes.length - 1 ? "" : ",");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Returns the index of the first method with the specified name and param types.
     *
     * @param methodName Method name or '<new>' for constructing
     * @param argTypes   Arguments class types
     * @return
     */
    public int indexOfMethod(String methodName, Class... argTypes) {
        Integer[] candidates = indexesOf(methodName, "method");
        //if(IS_STRICT_CONVERT) return candidates[0];
        int result = -1;
        Class[][] paramTypes;
        Integer[] modifiers;
        int signature = -1;
        int distance = 0;
        int minDistance = 10;
        final int stepSize = 100;
        if (methodName.equals(CONSTRUCTOR_ALIAS)) {
            for (int i = 0,n= candidates.length; i<n; i++) candidates[i] = i;
            paramTypes = classInfo.constructorParamTypes;
            modifiers = classInfo.constructorModifiers;
        } else {
            paramTypes = classInfo.methodParamTypes;
            modifiers = classInfo.methodModifiers;
        }
        if (IS_CACHED) {
            signature = getSignature(methodName, argTypes);
            if (methoIndexer.containsKey(signature)) {
                int targetIndex = methoIndexer.get(signature);
                minDistance = targetIndex / 10000;
                targetIndex = targetIndex % 10000;
                if (10000 - targetIndex == 1) result = -1;
                else for (int index : candidates)
                    if (index == targetIndex) result = index;
            }
        }
        final int argCount = argTypes.length;
        int[] distances = new int[0];
        if (result == -1) for (int index : candidates) {
            int min = 10;
            int[] val = new int[argCount + 1];
            if (Arrays.equals(argTypes, paramTypes[index])) {
                if (IS_CACHED) methoIndexer.put(signature, index + 50000);
                return index;
            }
            int thisDistance = 0;
            final int paramCount = paramTypes[index].length;
            final int last = paramCount -1;
            final boolean isVarArgs=isVarArgs(modifiers[index]);
            for (int i = 0,n= Math.min(argCount, paramCount); i<n;i++) {
                if (i == last && isVarArgs) break;
                val[i] = NumberUtils.getDistance(argTypes[i], paramTypes[index][i]);
                min = Math.min(val[i], min);
                thisDistance += stepSize + val[i];
                //System.out.println((argTypes[i]==null?"null":argTypes[i].getCanonicalName())+" <-> "+paramTypes[index][i].getCanonicalName()+": "+dis);
            }
            if (argCount > last && isVarArgs) {
                thisDistance += stepSize;
                int dis = 5;
                final Class arrayType = paramTypes[index][last].getComponentType();
                for (int i = last; i < argCount; i++) {
                    val[i] = Math.max(getDistance(argTypes[i], arrayType), getDistance(argTypes[i], paramTypes[index][last]));
                    dis = Math.min(dis, val[i]);
                }
                min = Math.min(dis, min);
                thisDistance += dis;
            } else {
                thisDistance -= Math.abs(paramCount - argCount) * stepSize;
            }
            if (thisDistance > distance) {
                distance = thisDistance;
                distances = val;
                result = index;
                minDistance = min;
            }
        }
        if (IS_CACHED) methoIndexer.put(signature, minDistance * 10000 + result);
        if (result != -1 && argCount == 0 && paramTypes[result].length == 0) return result;
        if (result == -1 || minDistance == 0 //
                || (argCount != paramTypes[result].length && !isVarArgs(modifiers[result])) //
                || (isVarArgs(modifiers[result]) && argCount < paramTypes[result].length - 1)) {
            String str = "Unable to apply method:\n    " + typesToString(methodName, argTypes) //
                    + (result == -1 ? "" : "\n => " + typesToString(methodName, paramTypes[result])) + "\n";
            if (IS_DEBUG) {
                System.out.println(String.format("Method=%s, Index=%d, isVarArgs=%s, MinDistance=%d%s", methodName, result, isVarArgs(modifiers[result]) + "(" + modifiers[result] + ")", minDistance, Arrays.toString(distances)));
                for (int i = 0; i < Math.max(argCount, paramTypes[result].length); i++) {
                    int flag = i >= argCount ? 1 : i >= paramTypes[result].length ? 2 : 0;
                    System.out.println(String.format("Parameter#%2d: %20s -> %-20s : %2d",//
                            i, flag == 1 ? "N/A" : argTypes[i] == null ? "null" : argTypes[i].getSimpleName(),//
                            flag == 2 ? "N/A" : paramTypes[result][i] == null ? "null" : paramTypes[result][i].getSimpleName(),//
                            flag > 0 ? -1 : distances[i]));
                }
            }
            throw new IllegalArgumentException(str);
        }
        return result;
    }

    /**
     * Returns the index of the first method with the specified name and the specified number of arguments.
     */
    public int indexOfMethod(String methodName, int paramsCount) {
        for (int index : indexesOf(methodName, "method")) {
            final int modifier = (methodName == CONSTRUCTOR_ALIAS) ? classInfo.constructorModifiers[index] : classInfo.methodModifiers[index];
            final int len = (methodName == CONSTRUCTOR_ALIAS) ? classInfo.constructorParamTypes[index].length : classInfo.methodParamTypes[index].length;
            if (len == paramsCount || isVarArgs(modifier) && paramsCount >= len - 1) return index;
        }
        throw new IllegalArgumentException("Unable to find method: " + methodName + " with " + paramsCount + " params.");
    }

    public String[] getMethodNames() {
        return classInfo.methodNames;
    }

    public Class[][] getParameterTypes() {
        return classInfo.methodParamTypes;
    }

    public Class[] getReturnTypes() {
        return classInfo.returnTypes;
    }

    public String[] getFieldNames() {
        return classInfo.fieldNames;
    }

    public Class[] getFieldTypes() {
        return classInfo.fieldTypes;
    }

    public int getFieldCount() {
        return classInfo.fieldTypes.length;
    }

    private String getMethodNameByParamTypes(Class[] paramTypes) {
        String methodName = CONSTRUCTOR_ALIAS;
        for (int i = 0; i < classInfo.methodParamTypes.length; i++) {
            if (classInfo.methodParamTypes[i] == paramTypes) {
                methodName = classInfo.methodNames[i];
            }
        }
        return methodName;
    }

    private Object[] reArgs(final int modifier, final Class[] paramTypes, final Object[] args) {
        //if(IS_STRICT_CONVERT) return args;
        final int paramCount = paramTypes.length;
        if (paramCount == 0) return args;
        final int argCount = args.length;
        final int last = paramCount - 1;
        final boolean isVarArgs=isVarArgs(modifier);
        Object[] arg = args;
        if ((argCount != paramCount && !isVarArgs) //
                || (isVarArgs && argCount < last)) {
            String methodName = getMethodNameByParamTypes(paramTypes);
            throw new IllegalArgumentException("Unable to invoke method: "//
                    + "\n    " + typesToString(methodName, args) //
                    + "\n    =>" + typesToString(methodName, paramTypes));
        }
        if (isVarArgs) {
            final Class varArgsType = paramTypes[last];
            if (argCount > paramCount) {
                arg = Arrays.copyOf(args, paramCount);
                arg[last] = Arrays.copyOfRange(args, last, argCount);
            } else if (argCount == paramCount) {
                if(arg[last]==null) arg[last]=Array.newInstance(varArgsType.getComponentType(),1);
                else if(getDistance(arg[last].getClass(), varArgsType) <= getDistance(arg[last].getClass(), varArgsType.getComponentType()))
                    arg[last] = new Object[]{arg[last]};
            } else if (argCount == last) {
                arg = Arrays.copyOf(arg, paramCount);
                arg[last] = Array.newInstance(varArgsType, 0);
            }
        }
        if (!IS_STRICT_CONVERT) try {
            for (int i = 0, n= Math.min(arg.length, paramCount);i < n; i++) arg[i] = convert(arg[i], paramTypes[i]);
        } catch (Exception e) {
            String methodName = getMethodNameByParamTypes(paramTypes);
            throw new IllegalArgumentException("Data conversion error when invoking method: " + e.getMessage()//
                    + "\n    " + typesToString(methodName, args) //
                    + "\n    =>" + typesToString(methodName, paramTypes));
        }
        return arg;
    }

    public Object invoke(Object instance, int methodIndex, Object... args) {
        boolean isNewInstance = (instance instanceof String) && (instance.equals(CONSTRUCTOR_ALIAS_ESCAPE));
        Object[] arg;
        try {
            arg = reArgs(isNewInstance ? classInfo.constructorModifiers[methodIndex] : classInfo.methodModifiers[methodIndex], //
                    isNewInstance ? classInfo.constructorParamTypes[methodIndex] : classInfo.methodParamTypes[methodIndex], args);
        } catch (ArrayIndexOutOfBoundsException e) {
            if (IS_DEBUG) e.printStackTrace();
            throw new IllegalArgumentException(String.format("No such %s index in class \"%s\": %s",//
                    isNewInstance ? "constructor" : "method", classInfo.baseClass.getName(), e.getMessage()));
        }
        return isNewInstance ? accessor.newInstanceWithIndex(methodIndex, arg) : accessor.invoke(instance, methodIndex, arg);
    }

    public Object invoke(Object instance, String methodName, Object... args) {
        Integer index = indexOf(methodName, "method");
        if (index == null) {
            Class[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) paramTypes[i] = args[i] == null ? null : args[i].getClass();
            index = indexOfMethod(methodName, paramTypes);
        }
        return invoke(methodName.equals(CONSTRUCTOR_ALIAS) ? CONSTRUCTOR_ALIAS_ESCAPE : instance, index, args);
    }

    public Object invokeWithTypes(Object object, String methodName, Class[] paramTypes, Object... args) {
        return invoke(object, indexOfMethod(methodName, paramTypes), args);
    }

    @Override
    public ClassInfo getInfo() {
        return classInfo;
    }

    @Override
    public void setInfo(ClassInfo info) {
        classInfo = info;
    }

    public Object newInstance() {
        return accessor.newInstance();
    }

    public Object newInstanceWithIndex(int constructorIndex, Object... args) {
        return invoke(CONSTRUCTOR_ALIAS_ESCAPE, constructorIndex, args);
    }

    public Object newInstanceWithTypes(Class[] paramTypes, Object... args) {
        return invokeWithTypes(CONSTRUCTOR_ALIAS_ESCAPE, ClassAccess.CONSTRUCTOR_ALIAS, paramTypes, args);
    }

    public Object newInstance(Object... args) {
        return invoke(CONSTRUCTOR_ALIAS_ESCAPE, CONSTRUCTOR_ALIAS, args);
    }

    public void set(Object instance, int fieldIndex, Object value) {
        if (IS_STRICT_CONVERT) try {
            value = convert(value, classInfo.fieldTypes[fieldIndex]);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to set field '%s.%s' as '%s': %s ",  //
                    classInfo.baseClass.getName(), classInfo.fieldNames[fieldIndex], value == null ? "null" : value.getClass().getCanonicalName(), e.getMessage()));
        }
        accessor.set(instance, fieldIndex, value);
    }

    public void set(Object instance, String fieldName, Object value) {
        set(instance, indexOfField(fieldName), value);
    }

    public void setBoolean(Object instance, int fieldIndex, boolean value) {
        set(instance, fieldIndex, value);
    }

    public void setByte(Object instance, int fieldIndex, byte value) {
        set(instance, fieldIndex, value);
    }

    public void setShort(Object instance, int fieldIndex, short value) {
        set(instance, fieldIndex, value);
    }

    public void setInt(Object instance, int fieldIndex, int value) {
        set(instance, fieldIndex, value);
    }

    public void setLong(Object instance, int fieldIndex, long value) {
        set(instance, fieldIndex, value);
    }

    public void setDouble(Object instance, int fieldIndex, double value) {
        set(instance, fieldIndex, value);
    }

    public void setFloat(Object instance, int fieldIndex, float value) {
        set(instance, fieldIndex, value);
    }

    public void setChar(Object instance, int fieldIndex, char value) {
        set(instance, fieldIndex, value);
    }

    public Object get(Object instance, int fieldIndex) {
        return accessor.get(instance, fieldIndex);
    }

    public Object get(Object instance, String fieldName) {
        return accessor.get(instance, indexOfField(fieldName));
    }

    public <T> T get(Object instance, int fieldIndex, Class<T> clz) {
        Object value = accessor.get(instance, fieldIndex);
        if (IS_STRICT_CONVERT) try {
            value = convert(value, clz);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to set field '%s' as '%s': %s ", classInfo.fieldNames[fieldIndex], value == null ? "null" : value.getClass().getCanonicalName(), e.getMessage()));
        }
        return (T) value;
    }

    public <T> T get(Object instance, String fieldName, Class<T> clz) {
        return get(instance, indexOfField(fieldName), clz);
    }

    public char getChar(Object instance, int fieldIndex) {
        return get(instance, fieldIndex, char.class);
    }

    public boolean getBoolean(Object instance, int fieldIndex) {
        return get(instance, fieldIndex, boolean.class);
    }

    public byte getByte(Object instance, int fieldIndex) {
        return get(instance, fieldIndex, byte.class);
    }

    public short getShort(Object instance, int fieldIndex) {
        return get(instance, fieldIndex, short.class);
    }

    public int getInt(Object instance, int fieldIndex) {
        return get(instance, fieldIndex, int.class);
    }

    public long getLong(Object instance, int fieldIndex) {
        return get(instance, fieldIndex, long.class);
    }

    public double getDouble(Object instance, int fieldIndex) {
        return get(instance, fieldIndex, double.class);
    }

    public float getFloat(Object instance, int fieldIndex) {
        return get(instance, fieldIndex, float.class);
    }

    public static class UnsafeHolder {
        public static Unsafe theUnsafe = null;

        static {
            try {
                Field uf = Unsafe.class.getDeclaredField("theUnsafe");
                uf.setAccessible(true);
                theUnsafe = (Unsafe) uf.get(null);
            } catch (Exception e) {
                //throw new AssertionError(e);
            }
        }
    }

    public Map<Field, Integer> fields() {
        return Collections.unmodifiableMap(classInfo.fields);
    }
}