package test;

/**
 * Created by Will on 2017/2/7.
 */
public class TestObject {
    static String fs;
    public Double fd;
    public int fi;
    private long fl;

    public TestObject() {
        fs = "TestObject0";
    }

    public TestObject(int fi1, Double fd1, String fs1, long l) {}

    static String func1(String str) {
        fs = str;
        return str;
    }

    public String func2(int fi1, Double fd1, String fs1, long l) {
        return fs;
    }
}
