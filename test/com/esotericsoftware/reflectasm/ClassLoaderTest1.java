/**
 * Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.esotericsoftware.reflectasm;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ClassLoaderTest1 extends TestCase {
    public void testDifferentClassloaders() throws Exception {
        // This classloader can see only the Test class and core Java classes.
        ClassLoader testClassLoader = new TestClassLoader1<Test>();
        Class testClass = testClassLoader.loadClass(Test.class.getName());
        Object testObject = testClass.newInstance();

        // Ensure AccessClassLoader can access both the Test class and FieldAccess.
        FieldAccess access = FieldAccess.access(testObject.getClass());
        access.set(testObject, "name", "first");
        assertEquals("first", testObject.toString());
        assertEquals("first", access.get(testObject, "name"));
    }

    public void testAutoUnloadClassloaders() throws Exception {
        int initialCount = AccessClassLoader.activeAccessClassLoaders();

        ClassLoader testClassLoader1 = new TestClassLoader1();
        Class testClass1 = testClassLoader1.loadClass(Test.class.getName());
        Object testObject1 = testClass1.newInstance();
        FieldAccess access1 = FieldAccess.access(testObject1.getClass());
        access1.set(testObject1, "name", "first");
        assertEquals("first", testObject1.toString());
        assertEquals("first", access1.get(testObject1, "name"));

        ClassLoader testClassLoader2 = new TestClassLoader2();
        Class testClass2 = testClassLoader2.loadClass(Test.class.getName());
        Object testObject2 = testClass2.newInstance();
        FieldAccess access2 = FieldAccess.access(testObject2.getClass());
        access2.set(testObject2, "name", "second");
        assertEquals("second", testObject2.toString());
        assertEquals("second", access2.get(testObject2, "name"));

        assertEquals(access1.getClass().toString(), access2.getClass().toString()); // Same class names
        assertFalse(access1.getClass().equals(access2.getClass())); // But different classes

        assertEquals(initialCount + 2, AccessClassLoader.activeAccessClassLoaders());

        testClassLoader1 = null;
        testClass1 = null;
        testObject1 = null;
        access1 = null;
        testClassLoader2 = null;
        testClass2 = null;
        testObject2 = null;
        access2 = null;

        // Force GC to reclaim unreachable (or only weak-reachable) objects
        System.gc();
        try {
            Object[] array = new Object[(int) Runtime.getRuntime().maxMemory()];
            System.out.println(array.length);
        } catch (Throwable e) {
            // Ignore OME
        }
        System.gc();
        int times = 0;
        while (AccessClassLoader.activeAccessClassLoaders() > 1 && times < 50) { // max 5 seconds, should be instant
            Thread.sleep(100); // test again
            times++;
        }

        // Yeah, both reclaimed!
        assertEquals(Math.min(initialCount, 1), AccessClassLoader.activeAccessClassLoaders());
    }

    public void testRemoveClassloaders() throws Exception {
        int initialCount = AccessClassLoader.activeAccessClassLoaders();

        ClassLoader testClassLoader1 = new TestClassLoader1();
        Class testClass1 = testClassLoader1.loadClass(Test.class.getName());
        Object testObject1 = testClass1.newInstance();
        FieldAccess access1 = FieldAccess.access(testObject1.getClass());
        access1.set(testObject1, "name", "first");
        assertEquals("first", testObject1.toString());
        assertEquals("first", access1.get(testObject1, "name"));

        ClassLoader testClassLoader2 = new TestClassLoader2();
        Class testClass2 = testClassLoader2.loadClass(Test.class.getName());
        Object testObject2 = testClass2.newInstance();
        FieldAccess access2 = FieldAccess.access(testObject2.getClass());
        access2.set(testObject2, "name", "second");
        assertEquals("second", testObject2.toString());
        assertEquals("second", access2.get(testObject2, "name"));

        assertEquals(access1.getClass().toString(), access2.getClass().toString()); // Same class names
        assertFalse(access1.getClass().equals(access2.getClass())); // But different classes

        assertEquals(initialCount + 2, AccessClassLoader.activeAccessClassLoaders());

        AccessClassLoader.remove(testObject1.getClass().getClassLoader());
        assertEquals(initialCount + 1, AccessClassLoader.activeAccessClassLoaders());
        AccessClassLoader.remove(testObject2.getClass().getClassLoader());
        assertEquals(initialCount + 0, AccessClassLoader.activeAccessClassLoaders());
        AccessClassLoader.remove(this.getClass().getClassLoader());
        assertEquals(initialCount - 1, AccessClassLoader.activeAccessClassLoaders());
    }

    static public class Test {
        public String name;

        public String toString() {
            return name;
        }
    }

    static public class TestClassLoader1<T> extends ClassLoader {
        protected synchronized Class<T> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class c = findLoadedClass(name);
            if (c != null) return c;
            if (name.startsWith("java.")) return (Class<T>) super.loadClass(name, resolve);
            if (!name.equals(Test.class.getName()))
                throw new ClassNotFoundException("Class not found on purpose: " + name);
            ByteArrayOutputStream output = new ByteArrayOutputStream(32 * 1024);
            InputStream input = ClassLoaderTest1.class.getResourceAsStream("/" + name.replace('.', '/') + ".class");
            if (input == null) return null;
            try {
                byte[] buffer = new byte[4096];
                int total = 0;
                while (true) {
                    int length = input.read(buffer, 0, buffer.length);
                    if (length == -1) break;
                    output.write(buffer, 0, length);
                }
            } catch (IOException ex) {
                throw new ClassNotFoundException("Error reading class file.", ex);
            } finally {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            byte[] buffer = output.toByteArray();
            return (Class<T>) defineClass(name, buffer, 0, buffer.length);
        }
    }

    static public class TestClassLoader2 extends TestClassLoader1 {}
}