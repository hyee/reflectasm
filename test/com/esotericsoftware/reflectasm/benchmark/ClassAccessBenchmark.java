package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.Accessor;
import com.esotericsoftware.reflectasm.ClassAccess;
import test.TestObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by hyee on 2017/2/4.
 */
public class ClassAccessBenchmark {
    public static String[] doBenchmark() throws Exception {
        final int count = Benchmark.testRounds;
        final int rounds = Benchmark.testCount;
        final String fieldName = "fl";
        final String methodName = "func2";
        final Benchmark benchmark = new Benchmark();

        TestObject obj = null;

        final Class[] args = new Class[]{int.class, Double.class, String.class, long.class};

        //access info from ClassAccessor;
        final ClassAccess<TestObject> console = ClassAccess.access(TestObject.class, ".");
        final Accessor<TestObject> asm = console.accessor;
        final int fieldIndex = console.indexOfField(fieldName);
        final int methodIndex = console.indexOfMethod(methodName, args);
        final int constructorIndex = console.indexOfConstructor(args);

        final Field field = TestObject.class.getField(fieldName);
        final Method method = TestObject.class.getDeclaredMethod(methodName, args);
        final Constructor constructor = TestObject.class.getConstructor(args);

        for (int c1 = 1; c1 <= 9; c1++) {
            for (int c = 0; c <= 1; c++) {
                benchmark.warmup = c == 0 ? true : false;
                String tag = (c1 <= 3 ? "Construction - " : c1 <= 6 ? "Field Set+Get - " : "Method Call - ") + (c1 % 3 == 1 ? "ReflectASM" : c1 % 3 == 2 ? "Reflection" : "Direct");
                benchmark.start();
                for (int i = 0; i < rounds; i++) {
                    for (int ii = 0; ii < count; ii++) {
                        int val = i + ii;
                        switch (c1) {
                            //Compare Constructions
                            //=====================
                            case 1:
                                obj = asm.newInstanceWithIndex(constructorIndex, val, ((Number) val).doubleValue(), String.valueOf(val), ((Number) val).longValue());
                                break;
                            case 2:
                                obj = (TestObject) constructor.newInstance(val, ((Number) val).doubleValue(), String.valueOf(val), ((Number) val).longValue());
                                break;
                            case 3:
                                obj = new TestObject(val, ((Number) val).doubleValue(), String.valueOf(val), ((Number) val).longValue());
                                break;
                            //Compare Field Set+Get
                            //=====================
                            case 4:
                                asm.set(obj, fieldIndex, val);
                                val = asm.get(obj, fieldIndex);
                                break;
                            case 5:
                                field.set(obj, val);
                                val = (int) field.get(obj);
                                break;
                            case 6:
                                obj.fl = val;
                                val = obj.fl;
                                break;
                            //Compare Method Call
                            //=====================
                            case 7:
                                asm.invokeWithIndex(obj, methodIndex, val, ((Number) val).doubleValue(), String.valueOf(val), ((Number) val).longValue());
                                break;
                            case 8:
                                method.invoke(obj, val, ((Number) val).doubleValue(), String.valueOf(val), ((Number) val).longValue());
                                break;
                            case 9:
                                obj.func2(val, ((Number) val).doubleValue(), String.valueOf(val), ((Number) val).longValue());
                                break;
                        }
                    }
                }
                benchmark.end(tag);
            }
        }

        return benchmark.chart("Benchmark Summary");
    }

    public static void print(String[][] results) {
        long max = 0;
        String[] times = new String[results.length];
        String[] names = new String[results.length];
        int index = -1;
        String title = ((System.getProperty("java.vm.name").indexOf("Server VM") > -1) ? "Server" : "Client") + " VM";
        HashMap<String, Integer> map = new HashMap();
        double count = Benchmark.testRounds * Benchmark.testCount;
        System.out.println(count);
        for (String[] result : results) {
            max = Math.max(max, Long.valueOf(result[1]));
            times[++index] = result[0];
            names[results.length - 1 - index] = result[2];
            String[] times0 = result[0].split(",");
            String[] names0 = result[2].split("\\|");
            String[] names1 = new String[names0.length];
            String[] times1 = new String[names0.length];
            String tag = null;
            int tagIdx = 0;
            for (int i = 0; i < names0.length; i++) {
                if (tag == null) {
                    tagIdx = names0[i].indexOf('-');
                    tag = names0[i].substring(0, tagIdx - 1).trim();
                }
                String name = names0[i].substring(tagIdx + 1).trim();
                names1[names0.length - 1 - i] = name;
                if (!map.containsKey(name)) {
                    map.put(name, i);
                }
                times1[map.get(name)] = String.format("%.2f ns", Long.valueOf(times0[i]) / count);
            }

            if (index == 0) {
                System.out.println("| VM | Item | " + String.join(" | ", names1) + " |");
                Arrays.fill(names1, " --------- ");
                System.out.println("| --- | --- | " + String.join(" | ", names1) + " |");
            }
            System.out.println("| " + title + " | " + tag + " | " + String.join(" | ", times1) + " |");
        }
        int height = 9 * 18 + 21;
        int width = Math.min(700, 300000 / height);
        System.out.println("Active ClassLoaders: " + ClassAccess.activeAccessClassLoaders());
        title = "Java " + System.getProperty("java.version") + " " + System.getProperty("os.arch") + "(" + title + ")";
        System.out.println("![](http://chart.apis.google.com/chart?chtt=&" + title + "&chs=" + width + "x" + height + "&chd=t:" + String.join(",", times) + "&chds=0," + max + "&chxl=0:|" + String.join("|", names) + "&cht=bhg&chbh=10&chxt=y&" + "chco=660000|660033|660066|660099|6600CC|6600FF|663300|663333|" + "663366|663399|6633CC|6633FF|666600|666633|666666)\n");
    }

    public static void main(String[] args) throws Exception {
        //ClassAccess.IS_CACHED = true;
        ClassAccess.IS_STRICT_CONVERT = true;
        new FieldAccessBenchmark();
        new MethodAccessBenchmark();
        new ConstructorAccessBenchmark();
        String[][] results = new String[][]{FieldAccessBenchmark.result, MethodAccessBenchmark.result, ConstructorAccessBenchmark.result};
        print(results);
        doBenchmark();
    }
}
