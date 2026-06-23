package com.zensyra.collector.query.composer;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;

/**
 * Minimal {@link Instance} stand-in for unit tests that need to exercise CDI
 * {@code Instance<T>}-typed constructor injection without bootstrapping a
 * real CDI container. Only {@link #iterator()} and {@link Iterable} usage
 * are supported, which is all {@link ActivityQueryComposer} needs.
 */
final class FakeInstance<T> implements Instance<T> {

    private final List<T> values;

    private FakeInstance(List<T> values) {
        this.values = values;
    }

    @SafeVarargs
    static <T> Instance<T> of(T... values) {
        return new FakeInstance<>(List.of(values));
    }

    @Override
    public Iterator<T> iterator() {
        return values.iterator();
    }

    @Override
    public T get() {
        if (values.size() != 1) {
            throw new IllegalStateException("FakeInstance.get() requires exactly one value, found " + values.size());
        }
        return values.get(0);
    }

    @Override
    public Instance<T> select(Annotation... annotations) {
        throw new UnsupportedOperationException("not needed by ActivityQueryComposer tests");
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... annotations) {
        throw new UnsupportedOperationException("not needed by ActivityQueryComposer tests");
    }

    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... annotations) {
        throw new UnsupportedOperationException("not needed by ActivityQueryComposer tests");
    }

    @Override
    public boolean isUnsatisfied() {
        return values.isEmpty();
    }

    @Override
    public boolean isAmbiguous() {
        return values.size() > 1;
    }

    @Override
    public void destroy(T instance) {
        // no-op for tests
    }

    @Override
    public java.util.stream.Stream<T> stream() {
        return values.stream();
    }
}
