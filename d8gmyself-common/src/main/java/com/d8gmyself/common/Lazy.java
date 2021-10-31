package com.d8gmyself.common;

import org.apache.commons.lang3.ObjectUtils;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 惰性函数
 */
public final class Lazy<T> implements Supplier<T> {

    private final Supplier<? extends T> supplier;
    private volatile Object value;
    private static final Object NULL = new Object();

    private Lazy(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }

    public static <T> Lazy<T> of(Supplier<? extends T> supplier) {
        return new Lazy<>(supplier);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get() {
        Object currentValue = value;
        if (currentValue != null) {
            return currentValue == NULL ? null : (T) currentValue;
        }
        synchronized (this) {
            currentValue = value;
            if (currentValue != null) {
                return currentValue == NULL ? null : (T) currentValue;
            }
            currentValue = ObjectUtils.defaultIfNull(supplier.get(), NULL);
            value = currentValue;
        }
        return currentValue == NULL ? null : (T) currentValue;
    }

    public <S> Lazy<S> map(Function<? super T, ? extends S> function) {
        return Lazy.of(() -> function.apply(get()));
    }

    public <S> Lazy<S> flatMap(Function<? super T, Lazy<? extends S>> function) {
        return Lazy.of(() -> function.apply(get()).get());
    }
}
