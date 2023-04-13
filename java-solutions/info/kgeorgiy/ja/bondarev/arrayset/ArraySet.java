package info.kgeorgiy.ja.bondarev.arrayset;

import java.util.*;

public class ArraySet<T> extends AbstractSet<T> implements SortedSet<T> {
    private final List<T> array;
    private final Comparator<? super T> comparator;

    public ArraySet() {
        this.array = new ArrayList<>();
        this.comparator = null;
    }

    public ArraySet(Collection<? extends T> collection) {
        this.array = new ArrayList<>(new TreeSet<>(collection));
        this.comparator = null;
    }

    public ArraySet(Collection<? extends T> collection, Comparator<? super T> comparator) {
        SortedSet<T> sortedSet = new TreeSet<>(comparator);
        sortedSet.addAll(collection);
        this.array = new ArrayList<>(sortedSet);
        this.comparator = comparator;
    }

    private ArraySet(Comparator<? super T> comparator) {
        this.array = new ArrayList<>();
        this.comparator = comparator;
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.unmodifiableList(array).iterator();
    }

    @Override
    public int size() {
        return array.size();
    }

    @Override
    public Comparator<? super T> comparator() {
        return comparator;
    }

    private int binarySearch(T element) {
        final int insertionPoint = Collections.binarySearch(array, element, comparator);
        return insertionPoint < 0 ? -insertionPoint - 1 : insertionPoint;
    }

    private SortedSet<T> subList(int fromIndex, int toIndex) {
        return new ArraySet<>(array.subList(fromIndex, toIndex), comparator);
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        if (comparator != null && comparator.compare(fromElement, toElement) > 0) {
            throw new IllegalArgumentException();
        }
        return subList(binarySearch(fromElement), binarySearch(toElement));
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
        return subList(0, binarySearch(toElement));
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
        return subList(binarySearch(fromElement), size());
    }

    @Override
    public T first() {
        requireNotEmpty();
        return array.get(0);
    }

    @Override
    public T last() {
        requireNotEmpty();
        return array.get(array.size() - 1);
    }

    private void requireNotEmpty() {
        if (array.isEmpty()) {
            throw new NoSuchElementException();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object object) {
        return Collections.binarySearch(array, (T) Objects.requireNonNull(object), comparator) >= 0;
    }
}
