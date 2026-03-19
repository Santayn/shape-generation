package app;

import java.util.Arrays;

public final class IntArrayList {
    private int[] data = new int[16];
    private int size = 0;

    public void add(int value) {
        if (size == data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        data[size++] = value;
    }

    public int size() {
        return size;
    }

    public int[] toArray() {
        return Arrays.copyOf(data, size);
    }
}
