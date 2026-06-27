package demo;

public final class HelloWorld {
    private HelloWorld() {
    }

    public static void main(String[] args) {
        System.out.println(message());
    }

    static String message() {
        return "Hello, world!";
    }
}
