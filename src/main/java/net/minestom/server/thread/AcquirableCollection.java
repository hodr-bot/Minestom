package net.minestom.server.thread;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

@ApiStatus.Experimental
public class AcquirableCollection<E> implements Collection<Acquirable<E>> {
    private final Collection<Acquirable<E>> acquirableCollection;

    public AcquirableCollection(Collection<Acquirable<E>> acquirableCollection) {
        this.acquirableCollection = acquirableCollection;
    }

    public void acquireSync(Consumer<E> consumer) {
        // Single owner thread per instance: no acquisition necessary.
        for (Acquirable<E> acquirable : acquirableCollection) {
            consumer.accept(acquirable.unwrap());
        }
    }

    public Stream<E> unwrap() {
        return acquirableCollection.stream().map(Acquirable::unwrap);
    }

    @Override
    public int size() {
        return acquirableCollection.size();
    }

    @Override
    public boolean isEmpty() {
        return acquirableCollection.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return acquirableCollection.contains(o);
    }

    @Override
    public Iterator<Acquirable<E>> iterator() {
        return acquirableCollection.iterator();
    }

    @Override
    public Object[] toArray() {
        return acquirableCollection.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return acquirableCollection.toArray(a);
    }

    @Override
    public boolean add(Acquirable<E> eAcquirable) {
        return acquirableCollection.add(eAcquirable);
    }

    @Override
    public boolean remove(Object o) {
        return acquirableCollection.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return acquirableCollection.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Acquirable<E>> c) {
        return acquirableCollection.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return acquirableCollection.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return acquirableCollection.retainAll(c);
    }

    @Override
    public void clear() {
        this.acquirableCollection.clear();
    }

    /**
     * Separates a collection of acquirable elements into a map of thread to elements,
     * consuming immediately the elements already owned by the current thread.
     *
     * @param collection the acquirable collection
     * @param consumer   the consumer to execute when an element is already in the current thread
     * @return a new Thread to acquirable elements map
     */
    protected static <T> Map<TickThread, List<T>> retrieveOptionalThreadMap(Collection<Acquirable<T>> collection,
                                                                            Consumer<T> consumer) {
        // Separate a collection of acquirable elements into a map of thread->elements
        // Useful to reduce the number of acquisition
        Map<TickThread, List<T>> threadCacheMap = new HashMap<>();
        for (var element : collection) {
            final T value = element.unwrap();
            final TickThread elementThread = element.assignedThread();
            if (Thread.currentThread() == elementThread) {
                // The element is managed in the current thread, consumer can be immediately called
                consumer.accept(value);
            } else {
                // The element is manager in a different thread, cache it
                List<T> threadCacheList = threadCacheMap.computeIfAbsent(elementThread, _ -> new ArrayList<>());
                threadCacheList.add(value);
            }
        }
        return threadCacheMap;
    }
}
