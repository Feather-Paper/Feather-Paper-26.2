package net.minecraft.util;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;

public class SortedArraySet<T> extends AbstractSet<T> implements ca.spottedleaf.moonrise.patches.chunk_system.util.ChunkSystemSortedArraySet<T> { // Paper - rewrite chunk system
    private static final int DEFAULT_INITIAL_CAPACITY = 10;
    private final Comparator<T> comparator;
    private final boolean isNaturalOrder; // Leaf - Improve sorting in SortedArraySet
    private T[] contents;
    private int size;

    // Paper start - rewrite chunk system
    @Override
    public final boolean removeIf(final java.util.function.Predicate<? super T> filter) {
        // Leaf start - Make removeIf slightly faster
        int i = 0;
        final int len = this.size;
        final T[] backingArray = this.contents;

        // Find first element to remove
        while (i < len && !filter.test(backingArray[i])) i++;
        if (i == len) return false;

        // Shift elements in-place
        int lastIndex = i;
        for (i++; i < len; i++) {
            T curr = backingArray[i];
            if (!filter.test(curr)) backingArray[lastIndex++] = curr;
        }

        // Only update size - skip Arrays.fill (safe in ChunkHolderManager's context)
        this.size = lastIndex;
        return true;
        // Leaf end - Make removeIf slightly faster
    }

    @Override
    public final T moonrise$replace(final T object) {
        final int index = this.findIndex(object);
        if (index >= 0) {
            final T old = this.contents[index];
            this.contents[index] = object;
            return old;
        } else {
            this.addInternal(object, getInsertionPosition(index));
            return object;
        }
    }

    @Override
    public final T moonrise$removeAndGet(final T object) {
        int i = this.findIndex(object);
        if (i >= 0) {
            final T ret = this.contents[i];
            this.removeInternal(i);
            return ret;
        } else {
            return null;
        }
    }

    @Override
    public final SortedArraySet<T> moonrise$copy() {
        final SortedArraySet<T> ret = SortedArraySet.create(this.comparator, 0);

        ret.size = this.size;
        ret.contents = Arrays.copyOf(this.contents, this.size);

        return ret;
    }

    @Override
    public Object[] moonrise$copyBackingArray() {
        return this.contents.clone();
    }
    // Paper end - rewrite chunk system

    private SortedArraySet(final int initialCapacity, final Comparator<T> comparator) {
        this.comparator = comparator;
        this.isNaturalOrder = comparator == Comparator.naturalOrder(); // Leaf - Improve sorting in SortedArraySet
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity (" + initialCapacity + ") is negative");
        }

        this.contents = (T[])castRawArray(new Object[initialCapacity]);
    }

    public static <T extends Comparable<T>> SortedArraySet<T> create() {
        return create(10);
    }

    public static <T extends Comparable<T>> SortedArraySet<T> create(final int initialCapacity) {
        return new SortedArraySet<T>(initialCapacity, Comparator.naturalOrder());
    }

    public static <T> SortedArraySet<T> create(final Comparator<T> comparator) {
        return create(comparator, 10);
    }

    public static <T> SortedArraySet<T> create(final Comparator<T> comparator, final int initialCapacity) {
        return new SortedArraySet<>(initialCapacity, comparator);
    }

    private static <T> T[] castRawArray(final Object[] array) {
        return (T[])array;
    }

    private int findIndex(final T t) {
        return isNaturalOrder ? naturalBinarySearch(t) : customBinarySearch(t); // Leaf - Improve sorting in SortedArraySet
    }

    // Leaf start - Improve sorting in SortedArraySet
    private int naturalBinarySearch(final T t) {
        int low = 0;
        int high = this.size - 1;
        final Comparable<? super T> key = (Comparable<? super T>) t;
        final T[] a = this.contents;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = a[mid];
            int cmp = key.compareTo(midVal);

            if (cmp < 0) {
                high = mid - 1;
            } else if (cmp > 0) {
                low = mid + 1;
            } else {
                return mid;
            }
        }

        return -(low + 1);
    }

    private int customBinarySearch(final T t) {
        int low = 0;
        int high = this.size - 1;
        final T[] a = this.contents;
        final Comparator<T> c = this.comparator;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = a[mid];
            int cmp = c.compare(midVal, t);

            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }

        return -(low + 1);
    }
    // Leaf end - Improve sorting in SortedArraySet

    private static int getInsertionPosition(final int position) {
        return -position - 1;
    }

    @Override
    public boolean add(final T t) {
        int position = this.findIndex(t);
        if (position >= 0) {
            return false;
        }

        int pos = getInsertionPosition(position);
        this.addInternal(t, pos);
        return true;
    }

    private void grow(int capacity) {
        if (capacity > this.contents.length) {
            if (this.contents != ObjectArrays.DEFAULT_EMPTY_ARRAY) {
                capacity = Util.growByHalf(this.contents.length, capacity);
            } else if (capacity < 10) {
                capacity = 10;
            }

            Object[] t = new Object[capacity];
            System.arraycopy(this.contents, 0, t, 0, this.size);
            this.contents = (T[])castRawArray(t);
        }
    }

    private void addInternal(final T t, final int pos) {
        this.grow(this.size + 1);
        if (pos != this.size) {
            System.arraycopy(this.contents, pos, this.contents, pos + 1, this.size - pos);
        }

        this.contents[pos] = t;
        this.size++;
    }

    private void removeInternal(final int position) {
        this.size--;
        if (position != this.size) {
            System.arraycopy(this.contents, position + 1, this.contents, position, this.size - position);
        }

        this.contents[this.size] = null;
    }

    private T getInternal(final int position) {
        return this.contents[position];
    }

    public T addOrGet(final T t) {
        int position = this.findIndex(t);
        if (position >= 0) {
            return this.getInternal(position);
        }

        this.addInternal(t, getInsertionPosition(position));
        return t;
    }

    @Override
    public boolean remove(final Object o) {
        int position = this.findIndex((T)o);
        if (position >= 0) {
            this.removeInternal(position);
            return true;
        } else {
            return false;
        }
    }

    public @Nullable T get(final T t) {
        int position = this.findIndex(t);
        return position >= 0 ? this.getInternal(position) : null;
    }

    public T first() {
        return this.getInternal(0);
    }

    public T last() {
        return this.getInternal(this.size - 1);
    }

    @Override
    public boolean contains(final Object o) {
        int result = this.findIndex((T)o);
        return result >= 0;
    }

    @Override
    public Iterator<T> iterator() {
        return new SortedArraySet.ArrayIterator();
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOf(this.contents, this.size, Object[].class);
    }

    @Override
    public <U> U[] toArray(final U[] a) {
        if (a.length < this.size) {
            return (U[])Arrays.copyOf(this.contents, this.size, (Class<? extends T[]>)a.getClass());
        }

        System.arraycopy(this.contents, 0, a, 0, this.size);
        if (a.length > this.size) {
            a[this.size] = null;
        }

        return a;
    }

    @Override
    public void clear() {
        Arrays.fill(this.contents, 0, this.size, null);
        this.size = 0;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else {
            return o instanceof SortedArraySet<?> that && this.comparator.equals(that.comparator)
                ? this.size == that.size && Arrays.equals(this.contents, that.contents)
                : super.equals(o);
        }
    }

    private class ArrayIterator implements Iterator<T> {
        private int index;
        private int last = -1;

        @Override
        public boolean hasNext() {
            return this.index < SortedArraySet.this.size;
        }

        @Override
        public T next() {
            if (this.index >= SortedArraySet.this.size) {
                throw new NoSuchElementException();
            }

            this.last = this.index++;
            return SortedArraySet.this.contents[this.last];
        }

        @Override
        public void remove() {
            if (this.last == -1) {
                throw new IllegalStateException();
            }

            SortedArraySet.this.removeInternal(this.last);
            this.index--;
            this.last = -1;
        }
    }
}
