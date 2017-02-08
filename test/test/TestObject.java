package test;

/**
 * Created by Will on 2017/2/7.
 */
public class TestObject {
    static String fs;
    public Double fd;
    public int fl;
    private long fi;

    public TestObject() {
        fs = "TestObject0";
    }

    public TestObject(int fi1, Double fd1, String fs1, long l) {}

    static void func1() {
        fs = "func1";
    }

    public String func2(int fi1, Double fd1, String fs1, long l) {
        return fs1;
    }
}
