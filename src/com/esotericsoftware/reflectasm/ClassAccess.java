package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.util.NumberUtils;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.util.TraceClassVisitor;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.esotericsoftware.reflectasm.util.NumberUtils.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond", "ConstantConditions", "Unsafe"})
public class ClassAccess<ANY> implements Accessor<ANY> {
    public final static int HASH_BUCKETS = 8;
    public final static int MODIFIER_VARARGS = 262144;
    public final static String CONSTRUCTOR_ALIAS = "<new>";
    public final static String CONSTRUCTOR_ALIAS_ESCAPE = "\3\2\1" + CONSTRUCTOR_ALIAS + "\1\2\3";
    public static String ACCESS_CLASS_PREFIX = "asm.";
    public final Accessor<ANY> accessor;
    private final ClassInfo classInfo;
    public static boolean IS_CACHED = true;
    public static boolean IS_STRICT_CONVERT = false;
    public static boolean IS_DEBUG = false;
    static HashMap[] caches = null;
    static ReentrantReadWriteLock[] locks = new ReentrantReadWriteLock[HASH_BUCKETS];
    static final String thisPath = Type.getInternalName(ClassAccess.class);
    static final String accessorPath = Type.getInternalName(Accessor.class);
    static final String classInfoPath = Type.getInternalName(ClassInfo.class);

    static {
        if (System.getProperty("reflectasm.is_cache", "true").toLowerCase().equals("false")) IS_CACHED = false;
        else {
            caches = new HashMap[HASH_BUCKETS];
            for (int i = 0; i < HASH_BUCKETS; i++) caches[i] = new HashMap(HASH_BUCKETS);
        }
        for (int i = 0; i < HASH_BUCKETS; i++) locks[i] = new ReentrantReadWriteLock();
        if (System.getProperty("reflectasm.is_debug", "false").toLowerCase().equals("true")) IS_DEBUG = true;
        if (System.getProperty("reflectasm.is_strict_convert", "false").toLowerCase().equals("true"))
            IS_STRICT_CONVERT = true;
    }

    protected ClassAccess(ClassInfo info, Accessor accessor) {
        this.classInfo = info;
        this.accessor = accessor;
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
        info.methodCount = info.methodNames.length;
        info.fieldCount = info.fieldNames.length;
        info.constructorCount = info.constructorModifiers.length;
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
    public static <ANY> ClassAccess access(Class<ANY> type, String... dumpFile) {
        String className = type.getName();
        final String accessClassName = ACCESS_CLASS_PREFIX + className;
        Class accessClass;
        Accessor accessor;
        ClassAccess self;
        int bucket = type.hashCode() % HASH_BUCKETS;
        ReentrantReadWriteLock lock = locks[bucket];
        final AccessClassLoader loader = AccessClassLoader.get(type);
        int lockFlag = 0;
        try {
            try {
                lock.readLock().lock();
                lockFlag |= 1;
                if (IS_CACHED && caches[bucket].containsKey(type))
                    if (caches[bucket].containsKey(type)) return (ClassAccess<ANY>) caches[bucket].get(type);
                lock.readLock().unlock();
                lockFlag ^= 1;
                try {
                    accessClass = loader.loadClass(accessClassName);
                } catch (ClassNotFoundException e) {
                    lock.writeLock().lock();
                    lockFlag |= 2;
                    accessClass = loader.loadClass(accessClassName);
                    lock.writeLock().unlock();
                    lockFlag ^= 2;
                }
                accessor = (Accessor) accessClass.newInstance();
                self = new ClassAccess(accessor.getInfo(), accessor);
                if (IS_CACHED) {
                    lock.writeLock().lock();
                    lockFlag |= 2;
                    caches[bucket].put(type, self);
                }
                return self;
            } catch (ClassNotFoundException e) {
            } catch (Exception ex) {
                throw new RuntimeException("Error constructing method access class: " + accessClassName, ex);
            }

            if ((lockFlag & 2) == 0) {
                lock.writeLock().lock();
                lockFlag |= 2;
            }

            ArrayList<Method> methods = new ArrayList<Method>();
            ArrayList<Constructor<?>> constructors = new ArrayList<Constructor<?>>();
            ArrayList<Field> fields = new ArrayList<Field>();
            collectMembers(type, methods, fields, constructors);

            ClassInfo info = new ClassInfo();
            info.bucket = bucket;

            int n = methods.size();
            info.methodDescs = new String[n];
            info.methodModifiers = new Integer[n];
            info.methodParamTypes = new Class[n][];
            info.returnTypes = new Class[n];
            info.methodNames = new String[n];
            info.baseClass = type;
            info.methodCount = n;
            for (int i = 0; i < n; i++) {
                Method m = methods.get(i);
                info.methodModifiers[i] = m.getModifiers();
                if (m.isVarArgs()) info.methodModifiers[i] += MODIFIER_VARARGS;
                info.methodModifiers[i] += m.getDeclaringClass().isInterface() ? Modifier.INTERFACE : 0;
                info.methodParamTypes[i] = m.getParameterTypes();
                info.returnTypes[i] = m.getReturnType();
                info.methodNames[i] = m.getName();
                info.methodDescs[i] = Type.getMethodDescriptor(m);
            }

            n = constructors.size();
            info.constructorModifiers = new Integer[n];
            info.constructorParamTypes = new Class[n][];
            info.constructorDescs = new String[n];
            info.constructorCount = n;
            for (int i = 0; i < n; i++) {
                Constructor<?> c = constructors.get(i);
                info.constructorModifiers[i] = c.getModifiers();
                if (c.isVarArgs()) info.methodModifiers[i] += MODIFIER_VARARGS;
                info.constructorParamTypes[i] = c.getParameterTypes();
                info.constructorDescs[i] = Type.getConstructorDescriptor(c);
            }

            n = fields.size();
            info.fieldModifiers = new Integer[n];
            info.fieldNames = new String[n];
            info.fieldTypes = new Class[n];
            info.fieldDescs = new String[n];
            info.fieldCount = n;
            for (int i = 0; i < n; i++) {
                Field f = fields.get(i);
                info.fieldNames[i] = f.getName();
                info.fieldTypes[i] = f.getType();
                info.fieldModifiers[i] = f.getModifiers();
                info.fieldDescs[i] = Type.getDescriptor(f.getType());
                info.fieldModifiers[i] += f.getDeclaringClass().isInterface() ? Modifier.INTERFACE : 0;
            }
            //Remove "type.getEnclosingClass()==null" due to may trigger error
            info.isNonStaticMemberClass = type.isMemberClass() && !Modifier.isStatic(type.getModifiers());

            String accessClassNameInternal = accessClassName.replace('.', '/');
            String classNameInternal = className.replace('.', '/');
            final byte[] bytes = byteCode(info, accessClassNameInternal, classNameInternal);
            if (dumpFile.length > 0) try {
                File f = new File(dumpFile[0]);
                if (!f.exists()) {
                    if (!dumpFile[0].endsWith(".class")) f.createNewFile();
                    else f.mkdir();
                }
                if (f.isDirectory()) f = new File(f.getCanonicalPath() + File.separator + accessClassName + ".class");
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
                self = new ClassAccess(accessor.getInfo(), accessor);
                if (IS_CACHED) caches[bucket].put(type, self);
                return self;
            } catch (Exception ex) {
                throw new RuntimeException("Error constructing method access class: " + accessClassName, ex);
            }
        } finally {
            if ((lockFlag & 2) > 0) lock.writeLock().unlock();
            if ((lockFlag & 1) > 0) lock.readLock().unlock();
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
            constructors.add(constructor);
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
            mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "classInfo", Type.getDescriptor(ClassInfo.class));
        }
        mv.visitLdcInsn(array.length);
        mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(array.getClass().getComponentType()));
        for (int i = 0; i < array.length; i++) {
            mv.visitInsn(DUP);
            mv.visitLdcInsn(i);
            if (array[i].getClass().isArray()) insertArray(mv, (Object[]) array[i], null, null);
            else if (array[i] instanceof Class) {
                Class clz = (Class) array[i];
                if (clz.isPrimitive())
                    mv.visitFieldInsn(GETSTATIC, Type.getInternalName(namePrimitiveMap.get(clz.getName())), "TYPE", "Ljava/lang/Class;");
                else mv.visitLdcInsn(Type.getType(clz));
            } else {
                mv.visitLdcInsn(array[i]);
                if (array[i] instanceof Integer)
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
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
    private static void insertClassInfo(ClassVisitor cw, ClassInfo info, String accessClassNameInternal, String classNameInternal) {
        final String baseName = "sun/reflect/MagicAccessorImpl";
        final String clzInfoDesc = Type.getDescriptor(ClassInfo.class);
        cw.visit(V1_1, ACC_PUBLIC + ACC_SUPER, accessClassNameInternal, "L" + baseName + ";L" + accessorPath + "<L" + classNameInternal + ";>;", baseName, new String[]{accessorPath});

        if (info.baseClass.getEnclosingClass() != null)
            cw.visitInnerClass(classNameInternal, Type.getInternalName(info.baseClass.getEnclosingClass()), info.baseClass.getSimpleName(), info.baseClass.getModifiers());
        FieldVisitor fv = cw.visitField(ACC_FINAL + ACC_STATIC, "classInfo", clzInfoDesc, null, null);
        fv.visitEnd();

        MethodVisitor mv;

        mv = cw.visitMethod(ACC_PUBLIC, "getInfo", "()" + clzInfoDesc, null, null);
        mv.visitCode();
        mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "classInfo", clzInfoDesc);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        //Constructor
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, baseName, "<init>", "()V");
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        //Static block
        {
            mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();
            mv.visitTypeInsn(NEW, classInfoPath);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, classInfoPath, "<init>", "()V");
            mv.visitFieldInsn(PUTSTATIC, accessClassNameInternal, "classInfo", clzInfoDesc);

            insertArray(mv, info.methodNames, "methodNames", accessClassNameInternal);
            insertArray(mv, info.methodParamTypes, "methodParamTypes", accessClassNameInternal);
            insertArray(mv, info.returnTypes, "returnTypes", accessClassNameInternal);
            insertArray(mv, info.methodModifiers, "methodModifiers", accessClassNameInternal);
            insertArray(mv, info.methodDescs, "methodDescs", accessClassNameInternal);
            insertArray(mv, info.fieldNames, "fieldNames", accessClassNameInternal);
            insertArray(mv, info.fieldTypes, "fieldTypes", accessClassNameInternal);
            insertArray(mv, info.fieldModifiers, "fieldModifiers", accessClassNameInternal);
            insertArray(mv, info.fieldDescs, "fieldDescs", accessClassNameInternal);
            insertArray(mv, info.constructorParamTypes, "constructorParamTypes", accessClassNameInternal);
            insertArray(mv, info.constructorModifiers, "constructorModifiers", accessClassNameInternal);
            insertArray(mv, info.constructorDescs, "constructorDescs", accessClassNameInternal);

            mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "classInfo", clzInfoDesc);
            mv.visitLdcInsn(Type.getType(info.baseClass));
            mv.visitFieldInsn(PUTFIELD, classInfoPath, "baseClass", "Ljava/lang/Class;");

            mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "classInfo", clzInfoDesc);
            mv.visitInsn(info.isNonStaticMemberClass ? ICONST_1 : ICONST_0);
            mv.visitFieldInsn(PUTFIELD, classInfoPath, "isNonStaticMemberClass", "Z");

            mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "classInfo", clzInfoDesc);
            mv.visitIntInsn(BIPUSH, info.bucket);
            mv.visitFieldInsn(PUTFIELD, classInfoPath, "bucket", "I");

            mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "classInfo", clzInfoDesc);
            mv.visitMethodInsn(INVOKESTATIC, thisPath, "buildIndex", "(" + clzInfoDesc + ")V", false);

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static byte[] byteCode(ClassInfo info, String accessClassNameInternal, String classNameInternal) {
        ClassWriter cv = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor cw;
        if (IS_DEBUG) cw = new TraceClassVisitor(cv, new PrintWriter(System.out));
        else cw = cv;

        insertClassInfo(cw, info, accessClassNameInternal, classNameInternal);

        //***********************************************************************************************
        // method access
        insertInvoke(cw, classNameInternal, info, accessClassNameInternal);

        //***********************************************************************************************
        // field access
        insertGetObject(cw, classNameInternal, info, accessClassNameInternal);
        insertSetObject(cw, classNameInternal, info, accessClassNameInternal);

        //***********************************************************************************************
        // constructor access
        insertNewInstance(cw, classNameInternal, info, accessClassNameInternal);
        insertNewRawInstance(cw, classNameInternal, accessClassNameInternal);

        cw.visitEnd();

        return cv.toByteArray();
    }

    private static void insertNewRawInstance(ClassVisitor cw, String classNameInternal, String accessClassNameInternal) {
        MethodVisitor mv;
        {
            mv = cw.visitMethod(ACC_PUBLIC, "newInstance", "()L" + classNameInternal + ";", null, null);
            mv.visitCode();
            mv.visitTypeInsn(NEW, classNameInternal);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, classNameInternal, "<init>", "()V");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "newInstance", "()Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, accessClassNameInternal, "newInstance", "()L" + classNameInternal + ";");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
    }

    private static void insertNewInstance(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "newInstanceWithIndex", "(I[Ljava/lang/Object;)L" + classNameInternal + ";", null, null);
        mv.visitCode();

        int n = info.constructorCount;

        if (n != 0) {
            mv.visitVarInsn(ILOAD, 1);
            Label[] labels = new Label[n];
            for (int i = 0; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0; i < n; i++) {
                mv.visitLabel(labels[i]);
                if (i == 0) mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{classNameInternal}, 0, null);
                else mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

                mv.visitTypeInsn(NEW, classNameInternal);
                mv.visitInsn(DUP);

                Class[] paramTypes = info.constructorParamTypes[i];
                for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
                    Type paramType = Type.getType(paramTypes[paramIndex]);
                    mv.visitVarInsn(ALOAD, 2);
                    mv.visitIntInsn(BIPUSH, paramIndex);
                    mv.visitInsn(AALOAD);
                    unbox(mv, paramType);
                }
                mv.visitMethodInsn(INVOKESPECIAL, classNameInternal, "<init>", info.constructorDescs[i]);
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

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "newInstanceWithIndex", "(I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, accessClassNameInternal, "newInstanceWithIndex", "(I[Ljava/lang/Object;)L" + classNameInternal + ";");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    private static void insertInvoke(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "invokeWithIndex", "(L" + classNameInternal + ";I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();

        int n = info.methodCount;

        if (n != 0) {
            mv.visitVarInsn(ILOAD, 2);
            Label[] labels = new Label[n];
            for (int i = 0; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0; i < n; i++) {
                boolean isInterface = Modifier.isInterface(info.methodModifiers[i]);
                boolean isStatic = Modifier.isStatic(info.methodModifiers[i]);

                mv.visitLabel(labels[i]);
                if (i == 0) mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{classNameInternal}, 0, null);
                else mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);

                if (!isStatic) {
                    mv.visitVarInsn(ALOAD, 1);
                }

                String methodName = info.methodNames[i];
                Class[] paramTypes = info.methodParamTypes[i];
                Class returnType = info.returnTypes[i];
                for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
                    mv.visitVarInsn(ALOAD, 3);
                    mv.visitIntInsn(BIPUSH, paramIndex);
                    mv.visitInsn(AALOAD);
                    Type paramType = Type.getType(paramTypes[paramIndex]);
                    unbox(mv, paramType);
                }

                final int inv = isInterface ? INVOKEINTERFACE : (isStatic ? INVOKESTATIC : INVOKEVIRTUAL);
                mv.visitMethodInsn(inv, classNameInternal, methodName, info.methodDescs[i]);
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

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "invokeWithIndex", "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, classNameInternal);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKEVIRTUAL, accessClassNameInternal, "invokeWithIndex", "(L" + classNameInternal + ";I[Ljava/lang/Object;)Ljava/lang/Object;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(4, 4);
        mv.visitEnd();
    }

    static private void insertSetObject(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "set", "(L" + classNameInternal + ";ILjava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (info.fieldCount > 0) {
            maxStack--;
            Label[] labels = new Label[info.fieldCount];
            for (int i = 0, n = labels.length; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                Type fieldType = Type.getType(info.fieldTypes[i]);
                boolean st = Modifier.isStatic(info.fieldModifiers[i]);

                mv.visitLabel(labels[i]);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                if (!st) {
                    mv.visitVarInsn(ALOAD, 1);
                }
                mv.visitVarInsn(ALOAD, 3);

                unbox(mv, fieldType);
                mv.visitFieldInsn(st ? PUTSTATIC : PUTFIELD, classNameInternal, info.fieldNames[i], info.fieldDescs[i]);
                mv.visitInsn(RETURN);
            }

            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        mv = insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, 4);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "set", "(Ljava/lang/Object;ILjava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, classNameInternal);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKEVIRTUAL, accessClassNameInternal, "set", "(L" + classNameInternal + ";ILjava/lang/Object;)V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(4, 4);
        mv.visitEnd();
    }

    static private void insertGetObject(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "get", "(L" + classNameInternal + ";I)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (info.fieldCount > 0) {
            maxStack--;
            Label[] labels = new Label[info.fieldCount];
            for (int i = 0, n = labels.length; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                mv.visitLabel(labels[i]);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                if (Modifier.isStatic(info.fieldModifiers[i])) {
                    mv.visitFieldInsn(GETSTATIC, classNameInternal, info.fieldNames[i], info.fieldDescs[i]);
                } else {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitFieldInsn(GETFIELD, classNameInternal, info.fieldNames[i], info.fieldDescs[i]);
                }
                Type fieldType = Type.getType(info.fieldTypes[i]);
                box(mv, fieldType);
                mv.visitInsn(ARETURN);
            }
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, 3);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "get", "(Ljava/lang/Object;I)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, classNameInternal);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, accessClassNameInternal, "get", "(L" + classNameInternal + ";I)Ljava/lang/Object;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 3);
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
        return indexOfField(field.getName());
    }

    public int indexOfConstructor(Constructor<?> constructor) {
        return indexOfMethod(CONSTRUCTOR_ALIAS, constructor.getParameterTypes());
    }

    public int indexOfConstructor(Class[] parameterTypes) {
        return indexOfMethod(CONSTRUCTOR_ALIAS, parameterTypes);
    }

    public int indexOfMethod(Method method) {
        return indexOfMethod(method.getName(), method.getParameterTypes());
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
            for (int i = 0, n = candidates.length; i < n; i++) candidates[i] = i;
            paramTypes = classInfo.constructorParamTypes;
            modifiers = classInfo.constructorModifiers;
        } else {
            paramTypes = classInfo.methodParamTypes;
            modifiers = classInfo.methodModifiers;
        }
        final int bucket = classInfo.bucket;
        ReentrantReadWriteLock lock = locks[bucket];
        int lockFlag = 0;
        try {
            if (IS_CACHED) {
                signature = getSignature(methodName, argTypes);
                lock.readLock().lock();
                lockFlag |= 1;
                Integer targetIndex = (Integer) caches[bucket].get(signature);
                lock.readLock().unlock();
                lockFlag ^= 1;
                if (targetIndex != null) {
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
                    if (IS_CACHED) {
                        lock.writeLock().lock();
                        lockFlag |= 2;
                        caches[bucket].put(signature, Integer.valueOf(index + 50000));
                    }
                    return index;
                }
                int thisDistance = 0;
                final int paramCount = paramTypes[index].length;
                final int last = paramCount - 1;
                final boolean isVarArgs = isVarArgs(modifiers[index]);
                for (int i = 0, n = Math.min(argCount, paramCount); i < n; i++) {
                    if (i == last && isVarArgs) break;
                    val[i] = IS_STRICT_CONVERT ? 10 : NumberUtils.getDistance(argTypes[i], paramTypes[index][i]);
                    min = Math.min(val[i], min);
                    thisDistance += stepSize + val[i];
                    //System.out.println((argTypes[i]==null?"null":argTypes[i].getCanonicalName())+" <-> "+paramTypes[index][i].getCanonicalName()+": "+dis);
                }
                if (argCount > last && isVarArgs) {
                    thisDistance += stepSize;
                    if (IS_STRICT_CONVERT) {
                        int dis = 5;
                        final Class arrayType = paramTypes[index][last].getComponentType();
                        for (int i = last; i < argCount; i++) {
                            val[i] = Math.max(getDistance(argTypes[i], arrayType), getDistance(argTypes[i], paramTypes[index][last]));
                            dis = Math.min(dis, val[i]);
                        }
                        min = Math.min(dis, min);
                        thisDistance += dis;
                    }
                } else if (paramCount - argCount > 0) {
                    thisDistance -= (paramCount - argCount) * stepSize;
                }
                if (thisDistance > distance) {
                    distance = thisDistance;
                    distances = val;
                    result = index;
                    minDistance = min;
                }
            }
            if (IS_CACHED) {
                lock.writeLock().lock();
                lockFlag |= 2;
                caches[bucket].put(signature, Integer.valueOf(minDistance * 10000 + result));
            }
            if (result != -1 && argCount == 0 && paramTypes[result].length == 0) return result;
            if (result == -1 || minDistance == 0 //
                    || (argCount < paramTypes[result].length && !isVarArgs(modifiers[result])) //
                    || (isVarArgs(modifiers[result]) && argCount < paramTypes[result].length - 1)) {
                String str = "Unable to apply " + (methodName.equals(CONSTRUCTOR_ALIAS) ? "constructor" : "method") + ":\n    " + typesToString(methodName, argTypes) //
                        + (result == -1 ? "" : "\n => " + typesToString(methodName, paramTypes[result])) + "\n";
                if (IS_DEBUG && result >= 0) {
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
        } finally {
            if ((lockFlag & 2) > 0) lock.writeLock().unlock();
            if ((lockFlag & 1) > 0) lock.readLock().unlock();
        }
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
        final int paramCount = paramTypes.length;
        if (paramCount == 0) return args;
        final int argCount = args.length;
        final int last = paramCount - 1;
        final boolean isVarArgs = isVarArgs(modifier);
        Object[] arg = args;
        if ((argCount != paramCount && !isVarArgs) //
                || (isVarArgs && argCount < last)) {
            String methodName = getMethodNameByParamTypes(paramTypes);
            throw new IllegalArgumentException("Unable to invokeWithIndex method: "//
                    + "\n    " + typesToString(methodName, args) //
                    + "\n    =>" + typesToString(methodName, paramTypes));
        }
        if (isVarArgs) {
            final Class varArgsType = paramTypes[last];
            if (argCount > paramCount) {
                arg = Arrays.copyOf(args, paramCount);
                arg[last] = Arrays.copyOfRange(args, last, argCount);
            } else if (argCount == paramCount) {
                if (arg[last] == null) arg[last] = Array.newInstance(varArgsType.getComponentType(), 1);
                else if (getDistance(arg[last].getClass(), varArgsType) <= getDistance(arg[last].getClass(), varArgsType.getComponentType()))
                    arg[last] = new Object[]{arg[last]};
            } else if (argCount == last) {
                arg = Arrays.copyOf(arg, paramCount);
                arg[last] = Array.newInstance(varArgsType, 0);
            }
        }
        if (!IS_STRICT_CONVERT) try {
            for (int i = 0, n = Math.min(arg.length, paramCount); i < n; i++) {
                arg[i] = convert(arg[i], paramTypes[i]);
            }
        } catch (Exception e) {
            if (isNonStaticMemberClass() && (args[0] == null || args[0].getClass() != classInfo.baseClass.getEnclosingClass()))
                throw new IllegalArgumentException("Cannot initialize a non-static inner class " + classInfo.baseClass.getCanonicalName() + " without specifing the enclosing instance!");
            String methodName = getMethodNameByParamTypes(paramTypes);
            throw new IllegalArgumentException("Data conversion error when invoking method: " + e.getMessage()//
                    + "\n    " + typesToString(methodName, args) //
                    + "\n    =>" + typesToString(methodName, paramTypes));
        }
        return arg;
    }

    public Object invokeWithIndex(Object instance, final int index, Object... args) {
        boolean isNewInstance = (instance instanceof String) && (instance.equals(CONSTRUCTOR_ALIAS_ESCAPE));
        Object[] arg = args;
        if (!IS_STRICT_CONVERT) {
            if (index >= (isNewInstance ? classInfo.constructorCount : classInfo.methodCount))
                throw new IllegalArgumentException(String.format("No such %s index in class \"%s\": %d",//
                        isNewInstance ? "constructor" : "method", classInfo.baseClass.getName(), index));
            arg = reArgs(isNewInstance ? classInfo.constructorModifiers[index] : classInfo.methodModifiers[index], //
                    isNewInstance ? classInfo.constructorParamTypes[index] : classInfo.methodParamTypes[index], arg);
        }
        return isNewInstance ? accessor.newInstanceWithIndex(index, arg) : accessor.invokeWithIndex((ANY) instance, index, arg);
    }

    public Object invoke(Object instance, String methodName, Object... args) {
        Integer index = indexOf(methodName, "method");
        if (index == null) {
            Class[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) paramTypes[i] = args[i] == null ? null : args[i].getClass();
            index = indexOfMethod(methodName, paramTypes);
        }
        return invokeWithIndex(methodName.equals(CONSTRUCTOR_ALIAS) ? CONSTRUCTOR_ALIAS_ESCAPE : instance, index, args);
    }

    public Object invokeWithTypes(Object object, String methodName, Class[] paramTypes, Object... args) {
        return invokeWithIndex(object, indexOfMethod(methodName, paramTypes), args);
    }

    @Override
    public ClassInfo getInfo() {
        return classInfo;
    }

    public ANY newInstance() {
        if (isNonStaticMemberClass())
            throw new IllegalArgumentException("Cannot initialize a non-static inner class " + classInfo.baseClass.getCanonicalName() + " without specifing the enclosing instance!");
        return accessor.newInstance();
    }

    public ANY newInstanceWithIndex(int constructorIndex, Object... args) {
        return (ANY) invokeWithIndex(CONSTRUCTOR_ALIAS_ESCAPE, constructorIndex, args);
    }

    public ANY newInstanceWithTypes(Class[] paramTypes, Object... args) {
        return (ANY) invokeWithTypes(CONSTRUCTOR_ALIAS_ESCAPE, ClassAccess.CONSTRUCTOR_ALIAS, paramTypes, args);
    }

    public ANY newInstance(Object... args) {
        return (ANY) invoke(CONSTRUCTOR_ALIAS_ESCAPE, CONSTRUCTOR_ALIAS, args);
    }

    public void set(ANY instance, int fieldIndex, Object value) {
        if (IS_STRICT_CONVERT) try {
            value = convert(value, classInfo.fieldTypes[fieldIndex]);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to set field '%s.%s' as '%s': %s ",  //
                    classInfo.baseClass.getName(), classInfo.fieldNames[fieldIndex], value == null ? "null" : value.getClass().getCanonicalName(), e.getMessage()));
        }
        accessor.set(instance, fieldIndex, value);
    }

    public void set(ANY instance, String fieldName, Object value) {
        set(instance, indexOfField(fieldName), value);
    }

    public void setBoolean(ANY instance, int fieldIndex, boolean value) {
        set(instance, fieldIndex, value);
    }

    public void setByte(ANY instance, int fieldIndex, byte value) {
        set(instance, fieldIndex, value);
    }

    public void setShort(ANY instance, int fieldIndex, short value) {
        set(instance, fieldIndex, value);
    }

    public void setInt(ANY instance, int fieldIndex, int value) {
        set(instance, fieldIndex, value);
    }

    public void setLong(ANY instance, int fieldIndex, long value) {
        set(instance, fieldIndex, value);
    }

    public void setDouble(ANY instance, int fieldIndex, double value) {
        set(instance, fieldIndex, value);
    }

    public void setFloat(ANY instance, int fieldIndex, float value) {
        set(instance, fieldIndex, value);
    }

    public void setChar(ANY instance, int fieldIndex, char value) {
        set(instance, fieldIndex, value);
    }

    public Object get(ANY instance, int fieldIndex) {
        return accessor.get(instance, fieldIndex);
    }

    public Object get(ANY instance, String fieldName) {
        return accessor.get(instance, indexOfField(fieldName));
    }

    public <T> T get(ANY instance, int fieldIndex, Class<T> clz) {
        Object value = accessor.get(instance, fieldIndex);
        if (IS_STRICT_CONVERT) try {
            value = convert(value, clz);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to set field '%s' as '%s': %s ", classInfo.fieldNames[fieldIndex], value == null ? "null" : value.getClass().getCanonicalName(), e.getMessage()));
        }
        return (T) value;
    }

    public <T> T get(ANY instance, String fieldName, Class<T> clz) {
        return get(instance, indexOfField(fieldName), clz);
    }

    public char getChar(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, char.class);
    }

    public boolean getBoolean(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, boolean.class);
    }

    public byte getByte(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, byte.class);
    }

    public short getShort(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, short.class);
    }

    public int getInt(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, int.class);
    }

    public long getLong(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, long.class);
    }

    public double getDouble(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, double.class);
    }

    public float getFloat(ANY instance, int fieldIndex) {
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
}