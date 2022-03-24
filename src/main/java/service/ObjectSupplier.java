package service;

import java.util.function.Supplier;

public class ObjectSupplier<T> implements Supplier<T> {

    private Supplier<? extends T> supplier;
    private volatile T value;

    private ObjectSupplier(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }

    public static <T> ObjectSupplier<T> of(Supplier<? extends T> supplier) {
        return new ObjectSupplier<>(supplier);
    }

    @Override
    public T get() {
        T localReference = value;
        if (localReference == null) {
            synchronized (this) {
                localReference = value;
                if (localReference == null) {
                    value = localReference = supplier.get();
                    supplier = null;
                }
            }
        }
        return localReference;
    }

}
