package net.minestom.server.thread;

import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * No-op implementation: there is a single owner thread per instance and all
 * gameplay access is confined to it, so acquisition never blocks.
 */
@ApiStatus.Internal
public final class AcquirableImpl<T> implements Acquirable<T> {
    static final AtomicLong WAIT_COUNTER_NANO = new AtomicLong();

    // Entities currently ticked by this thread, set by the instance tick.
    private static final ThreadLocal<Set<?>> LOCAL_TICKED = new ThreadLocal<>();

    @ApiStatus.Internal
    public static void beginLocalTick(Set<?> entities) {
        LOCAL_TICKED.set(entities);
    }

    @ApiStatus.Internal
    public static void endLocalTick() {
        LOCAL_TICKED.remove();
    }

    static Stream<Entity> localTickedEntities() {
        final Set<?> ticked = LOCAL_TICKED.get();
        if (ticked == null) return Stream.empty();
        return ticked.stream()
                .filter(Entity.class::isInstance)
                .map(Entity.class::cast);
    }

    private final T value;
    private volatile @Nullable TickThread assignedThread;

    public AcquirableImpl(T value) {
        this.value = value;
    }

    @Override
    public Acquired<T> lock() {
        return new AcquiredImpl<>(unwrap(), null);
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public boolean isOwned() {
        return true;
    }

    @Override
    public void sync(Consumer<T> consumer) {
        consumer.accept(unwrap());
    }

    @Override
    public boolean trySync(Consumer<T> consumer) {
        consumer.accept(unwrap());
        return true;
    }

    @Override
    public T unwrap() {
        return value;
    }

    @Override
    public @UnknownNullability TickThread assignedThread() {
        return assignedThread;
    }

    void assign(TickThread thread) {
        this.assignedThread = thread;
    }

    @Override
    public void assertOwnership() {
        // Everything reachable is owned in the instance-thread model.
    }

    static void leave(@Nullable Object lock) {
        // No locks are ever taken.
    }
}
