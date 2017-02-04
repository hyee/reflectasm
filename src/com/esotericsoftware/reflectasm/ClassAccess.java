package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.util.NumberUtils;
import org.objectweb.asm.*;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.esotericsoftware.reflectasm.util.NumberUtils.*;
import static java.lang.reflect.Modifier.isStatic;
import static org.objectweb.asm.Opcodes.*;

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond", "ConstantConditions", "Unsafe"})
public class ClassAccess {
    public final static Object[] NO_ARGUMENTS = new Object[0];
    public final static Object STATIC_INSTANCE = null;
    public final static int MODIFIER_VARARGS = 2 ^ 18;
    public final static String CONSTRUCTOR_ALIAS="<new>";
    public final static String CONSTRUCTOR_ALIAS_ESCAPE="\3\2\1"+CONSTRUCTOR_ALIAS+"\1\2\3";
    public static String classPrefix = "asm.";
    public final Accessor accessor;
    public ClassInfo info;

    public static boolean IS_CACHED = true;
    public static boolean IS_STRICT_CONVET = false;
    static HashMap<Class, ClassAccess> cachedAccessors = new HashMap();
    static ConcurrentHashMap<Integer, Integer> methoIndexer = new ConcurrentHashMap();
    static final String thisPath = Type.getInternalName(ClassAccess.class);
    static final String accessorPath = Type.getInternalName(Accessor.class);
    static final String classInfoPath = Type.getInternalName(ClassInfo.class);

    protected ClassAccess(ClassInfo info, Accessor accessor) {
        this.info = info;
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

    public static void buildIndex(ClassInfo info) {
        if (info == null || info.attrIndex != null) return;
        info.attrIndex = new HashMap<>();
        String[] constructors = new String[info.constructorParameterTypes.length];
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
            info.attrIndex.put(key, map.get(key).toArray(new Integer[]{}));
        }
    }

    public static ClassAccess get(Class type, String... dumpFile) {
        String className = type.getName();
        final String accessClassName = classPrefix + className;
        Class accessClass;
        Accessor access;
        ClassAccess self;
        final AccessClassLoader loader = AccessClassLoader.get(type);
        try {
            synchronized (cachedAccessors) {
                if (IS_CACHED && cachedAccessors.containsKey(type)) return cachedAccessors.get(type);
                accessClass = loader.loadClass(accessClassName);
                access = (Accessor) accessClass.newInstance();
                self = new ClassAccess(access.getInfo(), access);
                if (IS_CACHED) cachedAccessors.put(type, self);
                return self;
            }
        } catch (ClassNotFoundException e) {
        } catch (Exception ex) {
            throw new RuntimeException("Error constructing method access class: " + accessClassName, ex);
        }

        ArrayList<Method> methods = new ArrayList<Method>();
        ArrayList<Constructor<?>> constructors = new ArrayList<Constructor<?>>();
        ArrayList<Field> fields = new ArrayList<Field>();
        collectMembers(type, methods, fields, constructors);

        ClassInfo classInfo = new ClassInfo();
        classInfo.methods = dump(methods);
        classInfo.fields = dump(fields);
        classInfo.constructors = dump(constructors);
        int n = methods.size();
        classInfo.methodModifiers = new Integer[n];
        classInfo.parameterTypes = new Class[n][];
        classInfo.returnTypes = new Class[n];
        classInfo.methodNames = new String[n];
        classInfo.baseClass = type;
        for (int i = 0; i < n; i++) {
            Method m = methods.get(i);
            classInfo.methodModifiers[i] = m.getModifiers();
            if (m.isVarArgs()) classInfo.methodModifiers[i] += MODIFIER_VARARGS;
            classInfo.parameterTypes[i] = m.getParameterTypes();
            classInfo.returnTypes[i] = m.getReturnType();
            classInfo.methodNames[i] = m.getName();
        }
        n = constructors.size();
        classInfo.constructorModifiers = new Integer[n];
        classInfo.constructorParameterTypes = new Class[n][];
        for (int i = 0; i < n; i++) {
            Constructor<?> c = constructors.get(i);
            classInfo.constructorModifiers[i] = c.getModifiers();
            if (c.isVarArgs()) classInfo.methodModifiers[i] += MODIFIER_VARARGS;
            classInfo.constructorParameterTypes[i] = c.getParameterTypes();
        }
        n = fields.size();
        classInfo.fieldModifiers = new Integer[n];
        classInfo.fieldNames = new String[n];
        classInfo.fieldTypes = new Class[n];
        for (int i = 0; i < n; i++) {
            Field f = fields.get(i);
            classInfo.fieldNames[i] = f.getName();
            classInfo.fieldTypes[i] = f.getType();
            classInfo.fieldModifiers[i] = f.getModifiers();
        }
        classInfo.isNonStaticMemberClass = type.getEnclosingClass() != null && type.isMemberClass() && !isStatic(type.getModifiers());

        //String accessClassName = "reflectasm." + className + "__ClassAccess__";
        //if (accessClassName.startsWith("java.")) accessClassName = "reflectasm." + accessClassName;

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (cachedAccessors) {
            String accessClassNameInternal = accessClassName.replace('.', '/');
            String classNameInternal = className.replace('.', '/');
            final byte[] bytes = byteCode(classInfo, methods, fields, accessClassNameInternal, classNameInternal);
            if (dumpFile.length > 0) try {
                File f = new File(dumpFile[0]);

                if (!f.exists()) {
                    if (!dumpFile[0].endsWith(".class")) f.createNewFile();
                    else f.mkdir();
                }
                if (f.isDirectory()) {
                    f = new File(f.getCanonicalPath() + File.separator + accessClassName + ".class");
                }
                FileOutputStream f1 = new FileOutputStream(f);
                f1.write(bytes);
                f1.flush();
                f1.close();
                System.out.println("Class saved to " + f.getCanonicalPath());
            } catch (Exception e) {
                e.printStackTrace();
            }

            try {
                accessClass = UnsafeHolder.theUnsafe.defineClass(accessClassName, bytes, 0, bytes.length, loader, type.getProtectionDomain());
            } catch (Throwable ignored1) {
                accessClass = loader.defineClass(accessClassName, bytes);
            }
            try {
                access = (Accessor) accessClass.newInstance();
                classInfo.attrIndex = access.getInfo().attrIndex;
                access.setInfo(classInfo);
                self = new ClassAccess(classInfo, access);
                if (IS_CACHED) cachedAccessors.put(type, self);
                return new ClassAccess(classInfo, access);
            } catch (Exception ex) {
                throw new RuntimeException("Error constructing method access class: " + accessClassName, ex);
            }
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

    private static void insertClassInfo(ClassInfo info, ClassWriter cw, String accessClassNameInternal, String classNameInternal) {
        final String baseName = "sun/reflect/MagicAccessorImpl";
        final String clzInfoDesc=Type.getDescriptor(ClassInfo.class);
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
        insertArray(mv, info.parameterTypes, "parameterTypes", accessClassNameInternal);
        insertArray(mv, info.returnTypes, "returnTypes", accessClassNameInternal);
        insertArray(mv, info.methodModifiers, "methodModifiers", accessClassNameInternal);
        insertArray(mv, info.fieldNames, "fieldNames", accessClassNameInternal);
        insertArray(mv, info.fieldTypes, "fieldTypes", accessClassNameInternal);
        insertArray(mv, info.fieldModifiers, "fieldModifiers", accessClassNameInternal);
        insertArray(mv, info.constructorParameterTypes, "constructorParameterTypes", accessClassNameInternal);
        insertArray(mv, info.constructorModifiers, "constructorModifiers", accessClassNameInternal);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, accessClassNameInternal, "classInfo", clzInfoDesc);
        mv.visitLdcInsn(Type.getType("L" + classNameInternal + ";"));
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

    ;

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
        insertGetPrimitive(cw, classNameInternal, fields, Type.BOOLEAN_TYPE, "getBoolean", IRETURN);
        insertSetPrimitive(cw, classNameInternal, fields, Type.BOOLEAN_TYPE, "setBoolean", ILOAD);
        insertGetPrimitive(cw, classNameInternal, fields, Type.BYTE_TYPE, "getByte", IRETURN);
        insertSetPrimitive(cw, classNameInternal, fields, Type.BYTE_TYPE, "setByte", ILOAD);
        insertGetPrimitive(cw, classNameInternal, fields, Type.SHORT_TYPE, "getShort", IRETURN);
        insertSetPrimitive(cw, classNameInternal, fields, Type.SHORT_TYPE, "setShort", ILOAD);
        insertGetPrimitive(cw, classNameInternal, fields, Type.INT_TYPE, "getInt", IRETURN);
        insertSetPrimitive(cw, classNameInternal, fields, Type.INT_TYPE, "setInt", ILOAD);
        insertGetPrimitive(cw, classNameInternal, fields, Type.LONG_TYPE, "getLong", LRETURN);
        insertSetPrimitive(cw, classNameInternal, fields, Type.LONG_TYPE, "setLong", LLOAD);
        insertGetPrimitive(cw, classNameInternal, fields, Type.DOUBLE_TYPE, "getDouble", DRETURN);
        insertSetPrimitive(cw, classNameInternal, fields, Type.DOUBLE_TYPE, "setDouble", DLOAD);
        insertGetPrimitive(cw, classNameInternal, fields, Type.FLOAT_TYPE, "getFloat", FRETURN);
        insertSetPrimitive(cw, classNameInternal, fields, Type.FLOAT_TYPE, "setFloat", FLOAD);
        insertGetPrimitive(cw, classNameInternal, fields, Type.CHAR_TYPE, "getChar", IRETURN);
        insertSetPrimitive(cw, classNameInternal, fields, Type.CHAR_TYPE, "setChar", ILOAD);

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
        mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "newInstance", "(I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
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

                Class[] paramTypes = info.constructorParameterTypes[i];
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

    static private void insertSetPrimitive(ClassWriter cw, String classNameInternal, List<Field> fields, Type primitiveType, String setterMethodName, int loadValueInstruction) {
        int maxStack = 6;
        int maxLocals = 5;
        final String typeNameInternal = primitiveType.getDescriptor();
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, setterMethodName, "(Ljava/lang/Object;I" + typeNameInternal + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (!fields.isEmpty()) {
            maxStack--;
            Label[] labels = new Label[fields.size()];
            Label labelForInvalidTypes = new Label();
            boolean hasAnyBadTypeLabel = false;
            for (int i = 0, n = labels.length; i < n; i++) {
                if (Type.getType(fields.get(i).getType()).equals(primitiveType)) labels[i] = new Label();
                else {
                    labels[i] = labelForInvalidTypes;
                    hasAnyBadTypeLabel = true;
                }
            }
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                if (!labels[i].equals(labelForInvalidTypes)) {
                    Field field = fields.get(i);
                    mv.visitLabel(labels[i]);
                    mv.visitFrame(F_SAME, 0, null, 0, null);
                    if (isStatic(field.getModifiers())) {
                        mv.visitVarInsn(loadValueInstruction, 3);
                        mv.visitFieldInsn(PUTSTATIC, classNameInternal, field.getName(), typeNameInternal);
                    } else {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitTypeInsn(CHECKCAST, classNameInternal);
                        mv.visitVarInsn(loadValueInstruction, 3);
                        mv.visitFieldInsn(PUTFIELD, classNameInternal, field.getName(), typeNameInternal);
                    }
                    mv.visitInsn(RETURN);
                }
            }
            // Rest of fields: different type
            if (hasAnyBadTypeLabel) {
                mv.visitLabel(labelForInvalidTypes);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                insertThrowExceptionForFieldType(mv, primitiveType.getClassName());
            }
            // Default: field not found
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        mv = insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, maxLocals);
        mv.visitEnd();
    }

    static private void insertGetPrimitive(ClassWriter cw, String classNameInternal, List<Field> fields, Type primitiveType, String getterMethodName, int returnValueInstruction) {
        int maxStack = 6;
        final String typeNameInternal = primitiveType.getDescriptor();
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, getterMethodName, "(Ljava/lang/Object;I)" + typeNameInternal, null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (!fields.isEmpty()) {
            maxStack--;
            Label[] labels = new Label[fields.size()];
            Label labelForInvalidTypes = new Label();
            boolean hasAnyBadTypeLabel = false;
            for (int i = 0, n = labels.length; i < n; i++) {
                if (Type.getType(fields.get(i).getType()).equals(primitiveType)) labels[i] = new Label();
                else {
                    labels[i] = labelForInvalidTypes;
                    hasAnyBadTypeLabel = true;
                }
            }
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                Field field = fields.get(i);
                if (!labels[i].equals(labelForInvalidTypes)) {
                    mv.visitLabel(labels[i]);
                    mv.visitFrame(F_SAME, 0, null, 0, null);
                    if (isStatic(field.getModifiers())) {
                        mv.visitFieldInsn(GETSTATIC, classNameInternal, field.getName(), typeNameInternal);
                    } else {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitTypeInsn(CHECKCAST, classNameInternal);
                        mv.visitFieldInsn(GETFIELD, classNameInternal, field.getName(), typeNameInternal);
                    }
                    mv.visitInsn(returnValueInstruction);
                }
            }
            // Rest of fields: different type
            if (hasAnyBadTypeLabel) {
                mv.visitLabel(labelForInvalidTypes);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                insertThrowExceptionForFieldType(mv, primitiveType.getClassName());
            }
            // Default: field not found
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        mv = insertThrowExceptionForFieldNotFound(mv);
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
                Type clz=Type.getType(namePrimitiveMap.get(type.getClassName()));
                mv.visitMethodInsn(INVOKESTATIC, clz.getInternalName(), "valueOf", "("+type.getDescriptor()+")"+clz.getDescriptor());
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
                String name=Type.getInternalName(namePrimitiveMap.get(type.getClassName()));
                mv.visitTypeInsn(CHECKCAST, name);
                mv.visitMethodInsn(INVOKEVIRTUAL, name, type.getClassName()+"Value", "()"+type.getDescriptor());
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
        return info.isNonStaticMemberClass;
    }

    public Class[][] getConstructorParameterTypes() {
        return info.constructorParameterTypes;
    }

    public Integer[] getMethodModifiers() {
        return info.methodModifiers;
    }

    public Integer[] getFieldModifiers() {
        return info.fieldModifiers;
    }

    public Integer[] getConstructorModifiers() {
        return info.constructorModifiers;
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
        if (info.attrIndex.containsKey(attr)) return info.attrIndex.get(attr);
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
        if (info.fields == null) collectMembers(info, info.baseClass);
        final Integer integer = info.fields.get(field);
        if (integer != null) return integer;
        throw new IllegalArgumentException("Unable to find field: " + field);
    }

    public int indexOfConstructor(Constructor<?> constructor) {
        if (info.fields == null) collectMembers(info, info.baseClass);
        final Integer integer = info.constructors.get(constructor);
        if (null != integer) return integer;
        throw new IllegalArgumentException("Unable to find constructor: " + constructor);
    }

    public int indexOfMethod(Method method) {
        if (info.fields == null) collectMembers(info, info.baseClass);
        final Integer integer = info.methods.get(method);
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
        int signature = info.baseClass.hashCode() * 65599 + methodName.hashCode();
        for (int i = 0; i < paramTypes.length; i++) {
            signature = signature * 65599 + (paramTypes[i] == null ? 0 : paramTypes[i].hashCode());
        }
        return signature;
    }

    /**
     * Returns the index of the first method with the specified name and param types.
     */
    public int indexOfMethod(String methodName, Class... paramTypes) {
        Integer[] candidates = indexesOf(methodName, "method");
        if (candidates.length == 1) return candidates[0];
        int result = -1;
        int signature = -1;
        int distance = 0;
        final int stepSize = 100;
        Class[][] srcTypes;
        Integer[] modifiers;
        if (methodName.equals(CONSTRUCTOR_ALIAS)) {
            for (int i = 0; i < candidates.length; i++) candidates[i] = i;
            srcTypes = info.constructorParameterTypes;
            modifiers = info.constructorModifiers;
        } else {
            srcTypes = info.parameterTypes;
            modifiers = info.methodModifiers;
        }

        if (IS_CACHED) {
            signature = getSignature(methodName, paramTypes);
            if (methoIndexer.containsKey(signature)) {
                int targetIndex = methoIndexer.get(signature);
                for (int index : candidates) if (index == targetIndex) return index;
            }
        }
        for (int index : candidates) {
            if (Arrays.equals(paramTypes, srcTypes[index])) return index;
            int thisDistance = 0;
            int argCount = paramTypes.length;
            int parameterCount = srcTypes[index].length;
            Object[] arguments = new Object[parameterCount];
            for (int i = 0; i < Math.min(argCount, parameterCount); i++) {
                if (i == parameterCount - 1 && isVarArgs(modifiers[index])) break;
                thisDistance += stepSize + NumberUtils.getDistance(paramTypes[i], srcTypes[index][i]);
            }
            if (argCount >= parameterCount - 1 && isVarArgs(modifiers[index])) {
                thisDistance += stepSize;
                int dis = 5;
                Class arrayType = srcTypes[index][parameterCount - 1].getComponentType();
                for (int i = parameterCount - 1; i < argCount; i++) {
                    dis = Math.min(dis, getDistance(paramTypes[i], arrayType));
                }
                if (argCount == parameterCount - 1) dis = getDistance(null, arrayType);
                thisDistance += dis;
            } else {
                thisDistance -= Math.abs(parameterCount - argCount) * stepSize;
            }
            if (thisDistance > distance) {
                distance = thisDistance;
                result = index;
            }
        }
        if (result == -1)
            throw new IllegalArgumentException("Unable to find method: " + methodName + " " + Arrays.toString(paramTypes));
        if (IS_CACHED) methoIndexer.put(signature, result);
        return result;
    }

    /**
     * Returns the index of the first method with the specified name and the specified number of arguments.
     */
    public int indexOfMethod(String methodName, int paramsCount) {
        final Class[][] parameterTypes = info.parameterTypes;
        for (int index : indexesOf(methodName, "method")) {
            if (parameterTypes[index].length == paramsCount) return index;
        }
        throw new IllegalArgumentException("Unable to find method: " + methodName + " with " + paramsCount + " params.");
    }

    public String[] getMethodNames() {
        return info.methodNames;
    }

    public Class[][] getParameterTypes() {
        return info.parameterTypes;
    }

    public Class[] getReturnTypes() {
        return info.returnTypes;
    }

    public String[] getFieldNames() {
        return info.fieldNames;
    }

    public Class[] getFieldTypes() {
        return info.fieldTypes;
    }

    public int getFieldCount() {
        return info.fieldTypes.length;
    }

    private Object[] reArgs(int modifier, Class[] paramTypes, Object... args) {
        if(args.length==0) return args;
        Object[] arg = args;
        if (isVarArgs(modifier)) {
            int len = paramTypes.length;
            if (args.length > len) {
                arg = new Object[len];
                arg[len - 1] = new Object[args.length - len];
                for (int i = 0; i < args.length; i++) {
                    if (i < len) arg[i] = args[i];
                    else ((Object[]) arg[len])[i - len] = args[i];
                }
            }
        }
        if (!IS_STRICT_CONVET)
            for (int i = 0; i < Math.min(arg.length, paramTypes.length); i++) arg[i]=convert(arg[i], paramTypes[i]);
        return arg;
    }

    public Object invoke(Object instance, int methodIndex, Object... args) {
        boolean isContruct=(instance instanceof String)&&((String)instance).equals(CONSTRUCTOR_ALIAS_ESCAPE);
        Object[] arg=reArgs(
                isContruct?info.constructorModifiers[methodIndex]:info.methodModifiers[methodIndex],
                isContruct?info.constructorParameterTypes[methodIndex]:info.parameterTypes[methodIndex], args);
        return isContruct?accessor.newInstance(methodIndex, arg):accessor.invoke(instance, methodIndex, arg);
    }

    public Object invoke(Object instance, String methodName, Object... args) {
        Integer index = indexOf(methodName, "method");
        if (index == null) {
            Class[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) paramTypes[i] = args[i] == null ? null : args[i].getClass();
            index = indexOfMethod(methodName, paramTypes);
        }
        return invoke(methodName.equals(CONSTRUCTOR_ALIAS)?CONSTRUCTOR_ALIAS_ESCAPE:instance, index, args);
    }


    public Object newInstance(int constructorIndex, Object... args) {
        return invoke(CONSTRUCTOR_ALIAS,constructorIndex, args);
    }

    public Object newInstance(Object... args) {
        return invoke(null,CONSTRUCTOR_ALIAS,args);
    }

    public Object newInstance() {
        return accessor.newInstance();
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

    public static interface Accessor {
        abstract public ClassInfo getInfo();

        abstract public void setInfo(ClassInfo info);

        abstract public Object newInstance(int constructorIndex, Object... args);

        abstract public Object newInstance();

        abstract public Object invoke(Object instance, int methodIndex, Object... args);

        abstract public void set(Object instance, int fieldIndex, Object value);

        abstract public void setBoolean(Object instance, int fieldIndex, boolean value);

        abstract public void setByte(Object instance, int fieldIndex, byte value);

        abstract public void setShort(Object instance, int fieldIndex, short value);

        abstract public void setInt(Object instance, int fieldIndex, int value);

        abstract public void setLong(Object instance, int fieldIndex, long value);

        abstract public void setDouble(Object instance, int fieldIndex, double value);

        abstract public void setFloat(Object instance, int fieldIndex, float value);

        abstract public void setChar(Object instance, int fieldIndex, char value);

        abstract public Object get(Object instance, int fieldIndex);

        abstract public char getChar(Object instance, int fieldIndex);

        abstract public boolean getBoolean(Object instance, int fieldIndex);

        abstract public byte getByte(Object instance, int fieldIndex);

        abstract public short getShort(Object instance, int fieldIndex);

        abstract public int getInt(Object instance, int fieldIndex);

        abstract public long getLong(Object instance, int fieldIndex);

        abstract public double getDouble(Object instance, int fieldIndex);

        abstract public float getFloat(Object instance, int fieldIndex);
    }

    public static class UnsafeHolder {

        public final static Unsafe theUnsafe;

        static {
            try {
                Field uf = Unsafe.class.getDeclaredField("theUnsafe");
                uf.setAccessible(true);
                theUnsafe = (Unsafe) uf.get(null);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    public Map<Field, Integer> fields() {
        return Collections.unmodifiableMap(info.fields);
    }
}