package com.d8gmyself.common;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 惰性函数
 */
public final class Lazy<T> implements Supplier<T> {

    private final Supplier<? extends T> supplier;
    private volatile T value;

    private Lazy(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }

    public static <T> Lazy<T> of(Supplier<? extends T> supplier) {
        return new Lazy<>(supplier);
    }

    @Override
    public T get() {
        T currentValue = value;
        if (currentValue != null) {
            return currentValue;
        }
        synchronized (this) {
            currentValue = value;
            if (currentValue != null) {
                return currentValue;
            }
            currentValue = supplier.get();
            if (currentValue == null) {
                throw new IllegalStateException("Lazy value can not be null");
            }
            value = currentValue;
        }
        return currentValue;
    }

    public <S> Lazy<S> map(Function<? super T, ? extends S> function) {
        return Lazy.of(() -> function.apply(get()));
    }

    public <S> Lazy<S> flatMap(Function<? super T, Lazy<? extends S>> function) {
        return Lazy.of(() -> function.apply(get()).get());
    }
}
