package benchmarking.calculators;public class IntBoxer {
    int value;

    public IntBoxer() {
    }

    public IntBoxer(IntBoxer boxer) {
        this.value = boxer.value;
    }

    public int get() {
        return value;
    }

    public void set(int value) {
        this.value = value;
    }

    public void increase() {
        value++;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }

}
