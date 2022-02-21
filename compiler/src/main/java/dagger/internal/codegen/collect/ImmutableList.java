package dagger.internal.codegen.collect;

import dagger.internal.codegen.base.Util;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ImmutableList<T> extends AbstractList<T> implements ImmutableCollection<T> {

  private static final ImmutableList<?> EMPTY = new ImmutableList<>(List.of());

  private final List<T> delegate;

  private ImmutableList(List<T> delegate) {
    this.delegate = delegate;
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public T get(int index) {
    return delegate.get(index);
  }

  public static <E> Builder<E> builder() {
    return new Builder<>(new ImmutableList<>(new ArrayList<>()));
  }

  public static <E> Builder<E> builderWithExpectedSize(int expectedSize) {
    return new Builder<>(new ImmutableList<>(new ArrayList<>(expectedSize)));
  }

  @Override
  public boolean add(T t) {
    return delegate.add(t);
  }

  public static final class Builder<T> {
    private final ImmutableList<T> list;

    private Builder(ImmutableList<T> list) {
      this.list = list;
    }

    public Builder<T> add(T t) {
      list.add(t);
      return this;
    }

    public Builder<T> addAll(Collection<T> values) {
      list.addAll(values);
      return this;
    }

    public ImmutableList<T> build() {
      return list;
    }
  }

  public ImmutableList<T> reverse() {
    if (size() <= 1) {
      return this;
    }
    return new ImmutableList<>(Util.reverse(this));
  }

  public static <E> ImmutableList<E> copyOf(Iterable<? extends E> elements) {
    if (elements instanceof ImmutableList) {
      return (ImmutableList<E>) elements;
    }
    if (elements instanceof List) {
      return new ImmutableList<>((List<E>) elements);
    }
    return new ImmutableList<>(Util.listOf(elements));
  }

  public static <E> ImmutableList<E> of() {
    return (ImmutableList<E>) EMPTY;
  }

  public static <E> ImmutableList<E> of(E e1) {
    return new ImmutableList<>(List.of(e1));
  }

  public static <E> ImmutableList<E> of(E e1, E e2) {
    return new ImmutableList<>(List.of(e1, e2));
  }

  public static <E> ImmutableList<E> of(E e1, E e2, E e3) {
    return new ImmutableList<>(List.of(e1, e2, e3));
  }

  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4) {
    return new ImmutableList<>(List.of(e1, e2, e3, e4));
  }

  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5) {
    return new ImmutableList<>(List.of(e1, e2, e3, e4, e5));
  }

  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6) {
    return new ImmutableList<>(List.of(e1, e2, e3, e4, e5, e6));
  }

  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
    return new ImmutableList<>(List.of(e1, e2, e3, e4, e5, e6, e7));
  }

  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
    return new ImmutableList<>(List.of(e1, e2, e3, e4, e5, e6, e7, e8));
  }
}