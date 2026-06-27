package demo;

public final class SmokeApp {
    private SmokeApp() {
    }

    public static void main(String[] args) {
        System.out.println(message(7, 5));
        System.out.println(mix(9L, 4L));
        System.out.println(callAdd(3, 8));
    }

    static String message(int a, int b) {
        return "sum=" + add(a, b);
    }

    static int add(int a, int b) {
        int x = 17;
        return (a + b) * x ^ 0x55AA;
    }

    static long mix(long a, long b) {
        return (a * 31L) - (b ^ 13L);
    }

    static int callAdd(int a, int b) {
        return add(a, b) - 1;
    }
}
