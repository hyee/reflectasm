package com.esotericsoftware.reflectasm.util;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Probe {
    public static Probe p = new Probe();

    public static void mark_(String tag) {p.mark(tag);}

    public static void clear_() {p.clear();}

    public static void end_() {p.end();}

    public static void print_() {p.print();}

    public static void reset_() {p.reset();}

    public boolean warmup = true;
    public Map<String, Long[]> times = new HashMap(10);
    private long s;
    private volatile long e;
    private String prevTag;
    private volatile boolean isRunning = false;
    private Thread thread = null;
    private long duration;
    private ArrayDeque<Object[]> queue = new ArrayDeque();
    private ReentrantLock lock = new ReentrantLock();

    static Comparator<Object[]> comparator = new Comparator<Object[]>() {
        public int compare(Object[] o1, Object[] o2) {
            return ((Long) ((Long) o2[1] - (Long) o1[1])).intValue();
        }
    };

    public void mark(String tag) {
        if (warmup) return;
        if (prevTag != null) {
            Long time = e - s;
            String name = prevTag + " -> " + (tag == null ? "<end>" : tag);
            //lock.lock();
            Long[] oldTime = times.get(name);
            if (oldTime == null) times.put(name, new Long[]{time, 1L, Integer.valueOf(times.size()).longValue()});
            else {
                oldTime[0] += time;
                ++oldTime[1];
            }
            //lock.unlock();
        }
        prevTag = tag == null ? "<begin>" : tag;
        s = e;
    }

    public void reset() {
        mark(null);
        if (thread == null && !warmup) {
            duration = System.nanoTime();
            e = 0L;
            isRunning = true;
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isRunning) {
                        try {
                            Thread.sleep(0, 10);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                        e = System.nanoTime();
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();

            try {
                while (e == 0L) Thread.sleep(1L);
            } catch (InterruptedException e) {
                thread = null;
            }
            s = e;
        }
    }

    public void end() {
        if (isRunning) {
            isRunning = false;
            if (thread != null && thread.isAlive()) try {
                thread.join();
            } catch (InterruptedException e) {}
            thread = null;
            duration = e - duration;
        }
    }

    public void clear() {
        queue.clear();
        end();
        if (warmup) return;
        times.clear();
        prevTag = null;
    }

    public void print() {
        end();
        String[] keys = times.keySet().toArray(new String[]{});
        Object[][] o = new Object[keys.length][];
        Long totalTime = 0L;
        int maxsize = 0;
        for (int i = 0, n = o.length; i < n; i++) {
            Long[] item = times.get(keys[i]);
            o[i] = new Object[]{keys[i], item[0], item[1], item[2]};
            totalTime += item[0];
            maxsize = Math.max(maxsize, keys[i].length());
        }
        Arrays.sort(o, comparator);
        StringBuilder sb = new StringBuilder(1024);
        for (Object[] i : o) {
            sb.append(String.format("|%4d:%-" + (maxsize + 1) + "s | %10.3f ms | %2.2f%% | %10d times |\n",//
                    i[3], i[0], ((Long) i[1]) / (Double) 1e6, 100 * ((Long) i[1]) / totalTime.doubleValue(), ((Long) i[2]).intValue()));
        }
        System.out.println(sb.toString());
    }
}
