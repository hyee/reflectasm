package com.esotericsoftware.reflectasm.benchmark;

import com.esotericsoftware.reflectasm.ClassAccess;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by hyee on 2017/2/4.
 */
public class ClassAccessBenchmark {
    public static void main(String[] args) throws Exception {
        //ClassAccess.IS_CACHED = true;
        //ClassAccess.IS_STRICT_CONVERT = true;
        new FieldAccessBenchmark();
        new MethodAccessBenchmark();
        new ConstructorAccessBenchmark();
        long max = 0;
        String[][] results = new String[][]{FieldAccessBenchmark.result, MethodAccessBenchmark.result, ConstructorAccessBenchmark.result};
        String[] times = new String[results.length];
        String[] names = new String[results.length];
        int index = -1;
        String title = ((System.getProperty("java.vm.name").indexOf("Server VM") > -1) ? "Server" : "Client") + " VM";
        HashMap<String, Integer> map = new HashMap();
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
                if (!map.containsKey(name)) map.put(name, i);
                times1[i] = String.valueOf(Math.floor(Long.valueOf(times0[map.get(name)]) / 1e6));
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
}
