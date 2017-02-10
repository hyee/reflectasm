package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.util.NumberUtils;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.esotericsoftware.reflectasm.util.NumberUtils.*;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/*import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import static org.objectweb.asm.Opcodes.*;
import org.objectweb.asm.util.CheckClassAdapter;*/

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond", "ConstantConditions", "Unsafe", "deprecation"})
public class ClassAccess<ANY> implements Accessor<ANY> {
    public final static int HASH_BUCKETS = Integer.valueOf(System.getProperty("reflectasm.hash_buckets", "16"));
    public final static int MODIFIER_VARARGS = 262144;
    public final static String CONSTRUCTOR_ALIAS = "<new>";
    static final String thisPath = Type.getInternalName(ClassAccess.class);
    static final String accessorPath = Type.getInternalName(Accessor.class);
    static final String classInfoPath = Type.getInternalName(ClassInfo.class);
    public static String ACCESS_CLASS_PREFIX = "asm.";
    public static boolean IS_CACHED = true;
    public static boolean IS_STRICT_CONVERT = false;
    public static boolean IS_DEBUG = false;
    public static int totalAccesses = 0;
    public static int cacheHits = 0;
    public static int loaderHits = 0;
    static HashMap[] caches = new HashMap[HASH_BUCKETS];
    static ReentrantReadWriteLock[] locks = new ReentrantReadWriteLock[HASH_BUCKETS];

    static {
        if (System.getProperty("reflectasm.is_cache", "true").toLowerCase().equals("false")) IS_CACHED = false;
        else for (int i = 0; i < HASH_BUCKETS; i++) caches[i] = new HashMap(HASH_BUCKETS);
        if (System.getProperty("reflectasm.is_debug", "false").toLowerCase().equals("true")) IS_DEBUG = true;
        if (System.getProperty("reflectasm.is_strict_convert", "false").toLowerCase().equals("true"))
            IS_STRICT_CONVERT = true;
        for (int i = 0; i < HASH_BUCKETS; i++) locks[i] = new ReentrantReadWriteLock();
    }

    public final Accessor<ANY> accessor;
    public final ClassInfo classInfo;

    protected ClassAccess(Accessor accessor) {
        this.classInfo = accessor.getInfo();
        this.accessor = accessor;
    }

    public static boolean isVarArgs(int modifier) {
        return (modifier & MODIFIER_VARARGS) != 0;
    }

    public static int activeAccessClassLoaders() {return AccessClassLoader.activeAccessClassLoaders();}

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
        for (String key : map.keySet())
            info.attrIndex.put(key, map.get(key).toArray(new Integer[]{}));
    }

    /**
     * @param type     Target class for reflection
     * @param dumpFile Optional to specify the path/directory to dump the reflection class over the target class
     * @return A dynamic object that wraps the target class
     */
    public static <ANY> ClassAccess access(Class<ANY> type, String... dumpFile) {
        String className = type.getName();
        final String accessClassName = (ACCESS_CLASS_PREFIX + className).replace("$", "");
        final String source = String.valueOf(type.getResource(""));
        Class<ANY> accessClass = null;
        Accessor<ANY> accessor;
        byte[] bytes = null;
        ClassInfo<ANY> info = null;
        ClassAccess<ANY> self;
        int bucket = Math.abs(className.hashCode()) % HASH_BUCKETS;
        ReentrantReadWriteLock lock = locks[bucket];
        AccessClassLoader loader = null;
        ++totalAccesses;
        int lockFlag = 0;
        try {
            lock.writeLock().lock();
            lockFlag |= 2;

            /*Cache: className={Class,classResourcePath,ClassAccess(),byte[]}*/
            if (IS_CACHED) {
                Object cachedObject = caches[bucket].get(className);
                if (cachedObject != null) {
                    Object[] cache = (Object[]) cachedObject;
                    //Class equals then directly return from cache
                    if (type == cache[0]) {
                        ++cacheHits;
                        return (ClassAccess<ANY>) cache[2];
                    }
                    //Else if resources are equal then load from pre-built bytes
                    if (cache[3] != null) {
                        if (cache[1] == null && source == null || cache[1].equals(source)) {
                            bytes = (byte[]) cache[3];
                            ++loaderHits;
                        }
                    }
                }
            } else {
                loader = AccessClassLoader.get(type);
                try {
                    accessClass = (Class<ANY>) loader.loadClass(accessClassName);
                } catch (ClassNotFoundException ignore1) {
                    synchronized (loader) {
                        try {
                            accessClass = (Class<ANY>) loader.loadClass(accessClassName);
                        } catch (ClassNotFoundException ignore2) {}
                    }
                }
                if (accessClass != null) {
                    ++loaderHits;
                    return new ClassAccess((Accessor) accessClass.newInstance());
                }
            }

            if (bytes == null) {//Otherwise rebuild the bytes
                ArrayList<Method> methods = new ArrayList<Method>();
                ArrayList<Constructor<?>> constructors = new ArrayList<Constructor<?>>();
                ArrayList<Field> fields = new ArrayList<Field>();
                collectMembers(type, methods, fields, constructors);
                info = new ClassInfo();
                info.bucket = bucket;

                int n = constructors.size();
                info.constructorModifiers = new Integer[n];
                info.constructorParamTypes = new Class[n][];
                info.constructorDescs = new String[n];
                info.constructorCount = n;
                for (int i = 0; i < n; i++) {
                    Constructor<?> c = constructors.get(i);
                    info.constructorModifiers[i] = c.getModifiers();
                    if (c.isVarArgs()) info.constructorModifiers[i] |= MODIFIER_VARARGS;
                    info.constructorParamTypes[i] = c.getParameterTypes();
                    info.constructorDescs[i] = Type.getConstructorDescriptor(c);
                }

                n = methods.size();
                info.methodDescs = new String[n][2];
                info.methodModifiers = new Integer[n];
                info.methodParamTypes = new Class[n][];
                info.returnTypes = new Class[n];
                info.methodNames = new String[n];
                info.baseClass = type;
                info.methodCount = n;
                for (int i = 0; i < n; i++) {
                    Method m = methods.get(i);
                    info.methodModifiers[i] = m.getModifiers();
                    if (m.isVarArgs()) info.methodModifiers[i] |= MODIFIER_VARARGS;
                    info.methodModifiers[i] += m.getDeclaringClass().isInterface() ? Modifier.INTERFACE : 0;
                    info.methodParamTypes[i] = m.getParameterTypes();
                    info.returnTypes[i] = m.getReturnType();
                    info.methodNames[i] = m.getName();
                    info.methodDescs[i] = new String[]{m.getName(), Type.getMethodDescriptor(m), Type.getInternalName(m.getDeclaringClass())};
                }

                n = fields.size();
                info.fieldModifiers = new Integer[n];
                info.fieldNames = new String[n];
                info.fieldTypes = new Class[n];
                info.fieldDescs = new String[n][2];
                info.fieldAddrs = new Long[n];
                info.fieldCount = n;
                for (int i = 0; i < n; i++) {
                    Field f = fields.get(i);
                    info.fieldNames[i] = f.getName();
                    info.fieldTypes[i] = f.getType();
                    info.fieldModifiers[i] = f.getModifiers();
                    info.fieldDescs[i] = new String[]{f.getName(), Type.getDescriptor(f.getType()), Type.getInternalName(f.getDeclaringClass())};
                    info.fieldModifiers[i] += f.getDeclaringClass().isInterface() ? Modifier.INTERFACE : 0;
                    //if (UnsafeHolder.theUnsafe != null) info.fieldAddrs[i] = UnsafeHolder.theUnsafe.objectFieldOffset(f);
                }

                String accessClassNameInternal = accessClassName.replace('.', '/');
                String classNameInternal = className.replace('.', '/');
                //Remove "type.getEnclosingClass()==null" due to may trigger error
                int position = className.lastIndexOf("$");
                info.isNonStaticMemberClass = position > 0 && classNameInternal.substring(position).indexOf("/") == -1 && !Modifier.isStatic(type.getModifiers());
                bytes = byteCode(info, accessClassNameInternal, classNameInternal);
            }
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

            if (loader == null) loader = AccessClassLoader.get(type);
            if (IS_DEBUG) CheckClassAdapter.verify(new ClassReader(bytes), loader, false, new PrintWriter(System.out));
            try {
                accessClass = (Class<ANY>) UnsafeHolder.theUnsafe.defineClass(accessClassName, bytes, 0, bytes.length, loader, type.getProtectionDomain());
            } catch (Throwable ignored1) {
                accessClass = (Class<ANY>) loader.defineClass(accessClassName, bytes);
            }
            accessor = (Accessor) accessClass.newInstance();
            self = new ClassAccess(accessor);
            if (IS_CACHED) caches[bucket].put(className, new Object[]{type, source, self, bytes});
            return self;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("Error constructing method access class: " + accessClassName + ": " + ex.getMessage(), ex);
        } finally {
            if ((lockFlag & 2) > 0) lock.writeLock().unlock();
            if ((lockFlag & 1) > 0) lock.readLock().unlock();
        }
    }

    private static void collectClasses(Class clz, ArrayList classes) {
        if (clz == Object.class && classes.size() > 0) return;
        classes.add(clz);
        Class superClass = clz.getSuperclass();
        if (superClass != null && superClass != clz) collectClasses(superClass, classes);
        for (Class c : clz.getInterfaces()) collectClasses(c, classes);
    }

    private static void collectMembers(Class<?> type, List<Method> methods, List<Field> fields, List<Constructor<?>> constructors) {
        boolean search = true;

        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            //if (isPrivate(constructor.getModifiers())) continue;
            constructors.add(constructor);
        }

        ArrayList<Class> classes = new ArrayList<>();
        collectClasses(type, classes);
        LinkedHashMap<String, Object> map = new LinkedHashMap();
        LinkedHashMap<String, Object> candidates = new LinkedHashMap();
        for (Class clz : classes) {
            for (Method m : clz.getDeclaredMethods()) {
                String desc = m.getName() + Type.getMethodDescriptor(m);
                Method m0 = (Method) map.get(desc);
                int md1 = m.getModifiers();
                int md0 = m0 == null ? 0 : m0.getModifiers();
                if (m0 == null) map.put(desc, m);
                else if (((Modifier.isPublic(md1) && !Modifier.isPublic(md0))//
                        || (Modifier.isStatic(md1) && !Modifier.isStatic(md0))//
                        || (Modifier.isAbstract(md0) && !Modifier.isAbstract(md1))) && clz != type) {
                    map.put(desc, m);
                    candidates.put(desc, m0);
                } else candidates.put(desc, m);
            }
            for (Field f : clz.getDeclaredFields()) {
                String desc = f.getName();
                Field f0 = (Field) map.get(desc);
                int md1 = f.getModifiers();
                int md0 = f0 == null ? 0 : f0.getModifiers();
                if (!map.containsKey(desc)) map.put(desc, f);
                else if (((Modifier.isPublic(md1) && !Modifier.isPublic(md0))//
                        || (Modifier.isStatic(md1) && !Modifier.isStatic(md0))//
                        || (Modifier.isAbstract(md0) && !Modifier.isAbstract(md1))) && clz != type) {
                    map.put(desc, f);
                    candidates.put(desc, f0);
                } else candidates.put(desc, f);
            }
        }

        for (String desc : map.keySet()) {
            Object value = map.get(desc);
            if (value.getClass() == Method.class) methods.add((Method) value);
            else fields.add((Field) value);
        }

        for (String desc : candidates.keySet()) {
            Object value = candidates.get(desc);
            if (value.getClass() == Method.class) methods.add((Method) value);
            else fields.add((Field) value);
        }
    }

    private static void insertArray(MethodVisitor mv, Object[] array, String attrName, String accessClassNameInternal) {
        if (attrName != null)
            mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "classInfo", Type.getDescriptor(ClassInfo.class));
        int len = array.length;
        mv.visitIntInsn(SIPUSH, len);
        mv.visitTypeInsn(ANEWARRAY, Type.getInternalName(array.getClass().getComponentType()));
        for (int i = 0; i < len; i++) {
            mv.visitInsn(DUP);
            mv.visitIntInsn(SIPUSH, i);
            Object item = array[i];
            if (item == null) mv.visitInsn(ACONST_NULL);
            else if (item.getClass().isArray()) insertArray(mv, (Object[]) item, null, null);
            else if (item instanceof Class) {
                Class clz = (Class) Array.get(array, i);
                if (clz.isPrimitive())
                    mv.visitFieldInsn(GETSTATIC, Type.getInternalName(namePrimitiveMap.get(clz.getName())), "TYPE", "Ljava/lang/Class;");
                else mv.visitLdcInsn(Type.getType(clz));
            } else {
                mv.visitLdcInsn(item);
                if (item instanceof Integer)
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
                if (item instanceof Long)
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;");
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
        //final String baseName = "java/lang/Object";
        final String clzInfoDesc = Type.getDescriptor(ClassInfo.class);
        final String genericName = "<L" + classNameInternal + ";>;";
        final String clzInfoGenericDesc = "L" + Type.getInternalName(ClassInfo.class) + genericName;
        cw.visit(V1_7, ACC_PUBLIC + ACC_SUPER + ACC_FINAL, accessClassNameInternal, "L" + baseName + ";L" + accessorPath + genericName, baseName, new String[]{accessorPath});
        String className = classNameInternal;
        try {
            int position = className.lastIndexOf("$");
            if (position >= 0 && classNameInternal.substring(position).indexOf("/") == -1) {
                String outerClass = classNameInternal.substring(0, position);
                cw.visitOuterClass(outerClass, null, null);
                cw.visitInnerClass(classNameInternal, outerClass, info.baseClass.getSimpleName(), info.baseClass.getModifiers());
            }
        } catch (NoClassDefFoundError e) {}
        MethodVisitor mv;
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
            FieldVisitor fv = cw.visitField(ACC_FINAL + ACC_STATIC, "classInfo", clzInfoDesc, clzInfoDesc.substring(0, clzInfoDesc.length() - 1) + "<L" + classNameInternal + ";>;", null);
            fv.visitEnd();

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

        mv = cw.visitMethod(ACC_PUBLIC, "getInfo", "()" + clzInfoDesc, "()" + clzInfoGenericDesc, null);
        mv.visitCode();
        mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "classInfo", clzInfoDesc);
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static byte[] byteCode(ClassInfo info, String accessClassNameInternal, String classNameInternal) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
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
        return cw.toByteArray();
    }

    private static void insertNewRawInstance(ClassVisitor cw, String classNameInternal, String accessClassNameInternal) {
        MethodVisitor mv;
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "newInstance", "()L" + classNameInternal + ";", null, null);
            mv.visitCode();
            mv.visitTypeInsn(NEW, classNameInternal);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, classNameInternal, "<init>", "()V");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "newInstance", "()Ljava/lang/Object;", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, accessClassNameInternal, "newInstance", "()L" + classNameInternal + ";");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
    }

    private static void insertNewInstance(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS + ACC_FINAL, "newInstanceWithIndex", "(I[Ljava/lang/Object;)L" + classNameInternal + ";", "<T:Ljava/lang/Object;>(I[TT;)L" + classNameInternal + ";", null);
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
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
        mv.visitInsn(ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "newInstanceWithIndex", "(I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKESPECIAL, accessClassNameInternal, "newInstanceWithIndex", "(I[Ljava/lang/Object;)L" + classNameInternal + ";");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    private static void insertInvoke(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS + ACC_FINAL, "invokeWithIndex", "(L" + classNameInternal + ";I[Ljava/lang/Object;)Ljava/lang/Object;", "<T:Ljava/lang/Object;V:Ljava/lang/Object;>(L" + classNameInternal + ";I[TV;)TT;", null);
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

                final int inv = isInterface ? INVOKEINTERFACE : (isStatic ? INVOKESTATIC : INVOKESPECIAL);
                mv.visitMethodInsn(inv, info.methodDescs[i][2], methodName, info.methodDescs[i][1]);
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
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
        mv.visitInsn(ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "invokeWithIndex", "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, classNameInternal);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKESPECIAL, accessClassNameInternal, "invokeWithIndex", "(L" + classNameInternal + ";I[Ljava/lang/Object;)Ljava/lang/Object;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(4, 4);
        mv.visitEnd();
    }

    static private void insertSetObject(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "set", "(L" + classNameInternal + ";ILjava/lang/Object;)V", "<T:Ljava/lang/Object;V:Ljava/lang/Object;>(L" + classNameInternal + ";ITV;)V", null);
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
                if (!st) mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 3);
                unbox(mv, fieldType);
                mv.visitFieldInsn(st ? PUTSTATIC : PUTFIELD, info.fieldDescs[i][2], info.fieldNames[i], info.fieldDescs[i][1]);
                mv.visitInsn(RETURN);
            }
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        mv = insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, 4);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "set", "(Ljava/lang/Object;ILjava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, classNameInternal);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKESPECIAL, accessClassNameInternal, "set", "(L" + classNameInternal + ";ILjava/lang/Object;)V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(4, 4);
        mv.visitEnd();
    }

    static private void insertGetObject(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "get", "(L" + classNameInternal + ";I)Ljava/lang/Object;", "<T:Ljava/lang/Object;>(L" + classNameInternal + ";I)TT;", null);
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
                    mv.visitFieldInsn(GETSTATIC, info.fieldDescs[i][2], info.fieldNames[i], info.fieldDescs[i][1]);
                } else {
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitFieldInsn(GETFIELD, info.fieldDescs[i][2], info.fieldNames[i], info.fieldDescs[i][1]);
                }
                Type fieldType = Type.getType(info.fieldTypes[i]);
                box(mv, fieldType);
                mv.visitInsn(ARETURN);
            }
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        insertThrowExceptionForFieldNotFound(mv);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "get", "(Ljava/lang/Object;I)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, classNameInternal);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKESPECIAL, accessClassNameInternal, "get", "(L" + classNameInternal + ";I)Ljava/lang/Object;");
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
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
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
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
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
                mv.visitMethodInsn(INVOKESPECIAL, name, type.getClassName() + "Value", "()" + type.getDescriptor());
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

    public Integer[] indexesOf(Class clz, String name, String type) {
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
        Integer[] list = (Integer[]) classInfo.attrIndex.get(attr);
        if (list != null) {
            if (clz == null || index == 3) return list;
            ArrayList<Integer> ary = new ArrayList();
            String[][] desc = index == 1 ? classInfo.fieldDescs : classInfo.methodDescs;
            String className = Type.getInternalName(clz);
            for (Integer e : list)
                if (desc[e][2].equals(className)) ary.add(e);
            list = ary.toArray(new Integer[]{});
            if (list.length > 0) return list;
        }
        throw new IllegalArgumentException("Unable to find " + type + ": " + name);
    }

    public Integer[] indexesOf(String name, String type) {
        return indexesOf(null, name, type);
    }

    public Integer indexOf(String name, String type) {
        Integer[] indexes = indexesOf(name, type);
        return indexes.length == 1 ? indexes[0] : null;
    }

    public int indexOfField(Class clz, String fieldName) {
        return indexesOf(clz, fieldName, "field")[0];
    }

    public int indexOfField(String fieldName) {
        return indexesOf(null, fieldName, "field")[0];
    }

    public int indexOfField(Field field) {
        return indexOfField(field.getDeclaringClass(), field.getName());
    }

    public int indexOfConstructor(Constructor<?> constructor) {
        return indexOfMethod(null, CONSTRUCTOR_ALIAS, constructor.getParameterTypes());
    }

    public int indexOfConstructor(Class... parameterTypes) {
        return indexOfMethod(null, CONSTRUCTOR_ALIAS, parameterTypes);
    }

    public int indexOfMethod(Method method) {
        return indexOfMethod(method.getDeclaringClass(), method.getName(), method.getParameterTypes());
    }

    /**
     * Returns the index of the first method with the specified name.
     */
    public int indexOfMethod(String methodName) {
        return indexesOf(null, methodName, "method")[0];
    }

    public Integer getSignature(String methodName, Class... paramTypes) {
        int signature = classInfo.baseClass.hashCode() * 65599 + methodName.hashCode();
        if (paramTypes != null) for (int i = 0; i < paramTypes.length; i++) {
            signature = signature * 65599 + (paramTypes[i] == null ? 0 : paramTypes[i].hashCode());
        }
        return signature;
    }

    @SafeVarargs
    private final String typesToString(String methodName, Object... argTypes) {
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
    @SafeVarargs
    public final int indexOfMethod(Class clz, String methodName, Class... argTypes) {
        Integer[] candidates = indexesOf(clz, methodName, "method");
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
                    if (10000 - targetIndex == 1) result = -2;
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
                    if (!IS_STRICT_CONVERT) {
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
                caches[bucket].put(signature, Integer.valueOf(minDistance * 10000 + Math.max(-1, result)));
            }
            if (result >= 0 && argCount == 0 && paramTypes[result].length == 0) return result;
            if (result < 0 || minDistance == 0 //
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

    public final int indexOfMethod(String methodName, Class... argTypes) {
        return indexOfMethod(null, methodName, argTypes);
    }

    /**
     * Returns the index of the first method with the specified name and the specified number of arguments.
     */
    public int indexOfMethod(String methodName, int paramsCount) {
        for (int index : indexesOf(null, methodName, "method")) {
            final int modifier = (methodName == CONSTRUCTOR_ALIAS) ? classInfo.constructorModifiers[index] : classInfo.methodModifiers[index];
            final int len = (methodName == CONSTRUCTOR_ALIAS) ? classInfo.constructorParamTypes[index].length : classInfo.methodParamTypes[index].length;
            if (len == paramsCount || isVarArgs(modifier) && paramsCount >= len - 1) return index;
        }
        throw new IllegalArgumentException("Unable to find method: " + methodName + " with " + paramsCount + " params.");
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

    private <T, V> T[] reArgs(final String method, final int index, V[] args) {
        final boolean isNewInstance = (method instanceof String) && (method.equals(CONSTRUCTOR_ALIAS));
        if (index >= (isNewInstance ? classInfo.constructorCount : classInfo.methodCount))
            throw new IllegalArgumentException("No index: " + index);
        final Class<T> paramTypes[] = isNewInstance ? classInfo.constructorParamTypes[index] : classInfo.methodParamTypes[index];
        final int paramCount = paramTypes.length;
        if (paramCount == 0) return null;
        final int modifier = isNewInstance ? classInfo.constructorModifiers[index] : classInfo.methodModifiers[index];
        final int argCount = args.length;
        final int last = paramCount - 1;
        final boolean isVarArgs = isVarArgs(modifier);

        if (argCount < (isVarArgs ? last : paramCount)) {
            String methodName = getMethodNameByParamTypes(paramTypes);
            throw new IllegalArgumentException("Unable to " + (isNewInstance ? "construct instance" : "invoke method") + " with index " + index + ": "//
                    + "\n    " + typesToString(methodName, args) //
                    + "\n    =>" + typesToString(methodName, paramTypes));
        }
        if (!isVarArgs && IS_STRICT_CONVERT) return (T[]) args;
        try {
            Object[] arg = new Object[paramCount];
            for (int i = 0; i < (isVarArgs ? last : paramCount); i++) {
                if (args[i] == null && paramTypes[i].isPrimitive())
                    throw new IllegalArgumentException("Cannot assign null to " + (i + 1) + "th element: " + paramTypes[i].getCanonicalName());
                arg[i] = IS_STRICT_CONVERT ? args[i] : convert(args[i], paramTypes[i]);
            }
            if (isVarArgs) {
                final Class varArgsType = paramTypes[last];
                final Class subType = varArgsType.getComponentType();
                Object var = null;
                if (argCount > paramCount) {
                    var = Arrays.copyOfRange(args, last, argCount);
                } else if (argCount == paramCount) {
                    var = args[last];
                    if (var == null) {
                        if (subType.isPrimitive())
                            throw new IllegalArgumentException("Cannot assign null to " + paramCount + "th element: " + varArgsType.getCanonicalName());
                        var = Array.newInstance(subType, 1);
                    } else if (getDistance(var.getClass(), varArgsType) <= getDistance(var.getClass(), subType))
                        var = Arrays.copyOfRange(args, last - 1, last);
                } else {
                    var = Array.newInstance(subType, 0);
                }
                arg[last] = IS_STRICT_CONVERT ? var : convert(var, varArgsType);
            }
            return (T[]) arg;
        } catch (Exception e) {
            if (isNonStaticMemberClass() && (args[0] == null || args[0].getClass() != classInfo.baseClass.getEnclosingClass()))
                throw new IllegalArgumentException("Cannot initialize a non-static inner class " + classInfo.baseClass.getCanonicalName() + " without specifying the enclosing instance!");
            String methodName = getMethodNameByParamTypes(paramTypes);
            if (IS_DEBUG) e.printStackTrace();
            throw new IllegalArgumentException("Data conversion error when invoking method: " + e.getMessage()//
                    + "\n    " + typesToString(methodName, args) //
                    + "\n    =>" + typesToString(methodName, paramTypes));
        }

    }

    public <T, V> T invokeWithIndex(ANY instance, final int methodIndex, V... args) {
        Object[] arg = args;
        if (!IS_STRICT_CONVERT) arg = reArgs("method", methodIndex, args);
        return accessor.invokeWithIndex(instance, methodIndex, arg);
    }

    public <T, V> T invoke(ANY instance, String methodName, V... args) {
        Integer index = indexOf(methodName, "method");
        if (index == null) {
            Class[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) paramTypes[i] = args[i] == null ? null : args[i].getClass();
            index = indexOfMethod(null, methodName, paramTypes);
        }
        return invokeWithIndex(instance, index, args);
    }

    public <T, V> T invokeWithTypes(ANY instance, String methodName, Class[] paramTypes, V... args) {
        return invokeWithIndex(instance, indexOfMethod(null, methodName, paramTypes), args);
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

    public <V> ANY newInstanceWithIndex(int constructorIndex, V... args) {
        V[] arg = args;
        if (!IS_STRICT_CONVERT) args = reArgs(CONSTRUCTOR_ALIAS, constructorIndex, args);
        return accessor.newInstanceWithIndex(constructorIndex, args);
    }

    public <T> ANY newInstanceWithTypes(Class[] paramTypes, T... args) {
        return newInstanceWithIndex(indexOfMethod(null, CONSTRUCTOR_ALIAS, paramTypes), args);
    }

    public ANY newInstance(Object... args) {
        Integer index = indexOf(CONSTRUCTOR_ALIAS, "method");
        if (index == null) {
            Class[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) paramTypes[i] = args[i] == null ? null : args[i].getClass();
            index = indexOfMethod(null, CONSTRUCTOR_ALIAS, paramTypes);
        }
        return newInstanceWithIndex(index, args);
    }

    public <T, V> void set(ANY instance, int fieldIndex, V value) {
        if (!IS_STRICT_CONVERT) try {
            accessor.set(instance, fieldIndex, convert(value, (Class<T>) classInfo.fieldTypes[fieldIndex]));
            return;
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to set field '%s.%s' as '%s': %s ",  //
                    classInfo.baseClass.getName(), classInfo.fieldNames[fieldIndex], value == null ? "null" : value.getClass().getCanonicalName(), e.getMessage()));
        }
        accessor.set(instance, fieldIndex, value);
    }

    public <T> void set(ANY instance, String fieldName, T value) {
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

    public <T> T get(ANY instance, int fieldIndex) {
        return accessor.get(instance, fieldIndex);
    }

    public <T> T get(ANY instance, String fieldName) {
        return accessor.get(instance, indexOfField(fieldName));
    }

    public <T> T get(ANY instance, int fieldIndex, Class<T> clz) {
        if (!IS_STRICT_CONVERT) try {
            return convert(accessor.get(instance, fieldIndex), clz);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to set field '%s': ", classInfo.fieldNames[fieldIndex], e.getMessage()));
        }
        return accessor.get(instance, fieldIndex);
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