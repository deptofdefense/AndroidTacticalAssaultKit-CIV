package android.util;

public final class Pair<A, B> {
    public A first;
    public B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public static <A, B> Pair<A, B> create(A first, B second) {
        return new Pair<A, B>(first, second);
    }
}
