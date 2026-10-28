package net.minestom.server.instance;

import net.minestom.server.MinecraftServer;
import net.minestom.server.property.ServerProperties;
import net.minestom.server.thread.MinestomThread;
import org.jetbrains.annotations.ApiStatus;

/**
 * Platform thread owning a single {@link InstanceContainer}.
 * <p>
 * Each instance ticks on its own thread with a phase offset so instances do
 * not all start their tick at the same time. There is no global tick barrier:
 * instances are independent and communicate through mailboxes.
 */
@ApiStatus.Internal
public final class InstanceThread extends MinestomThread {
    private static final int MAX_LAG_SLOTS = 5;

    private final InstanceContainer owner;
    private final long phaseOffsetNanos;
    private volatile boolean running = true;

    public InstanceThread(InstanceContainer owner, long phaseOffsetNanos) {
        super("Ms-Instance-" + owner.getUuid().toString().substring(0, 8));
        this.owner = owner;
        this.phaseOffsetNanos = phaseOffsetNanos;
    }

    public void shutdown() {
        this.running = false;
        this.interrupt();
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        final long tickNanos = 1_000_000_000L / ServerProperties.SERVER_TICKS_PER_SECOND.get();
        long nextSlot = System.nanoTime() + phaseOffsetNanos;
        while (running) {
            final long wait = nextSlot - System.nanoTime();
            if (wait > 0) {
                sleepNanos(wait);
                continue;
            }
            final long behind = System.nanoTime() - nextSlot;
            if (behind > MAX_LAG_SLOTS * tickNanos) {
                // Too far behind: drop missed slots, never catch up.
                nextSlot = System.nanoTime();
            } else {
                nextSlot += tickNanos;
            }
            try {
                owner.tickSlot(System.currentTimeMillis());
            } catch (Throwable t) {
                MinecraftServer.getExceptionManager().handleException(t);
            }
        }
    }

    private void sleepNanos(long nanos) {
        if (nanos <= 0 || !running) return;
        try {
            final long millis = nanos / 1_000_000L;
            final int remainder = (int) (nanos % 1_000_000L);
            Thread.sleep(millis, remainder);
        } catch (InterruptedException _) {
            // Shutdown signal, loop condition re-checks running.
            Thread.currentThread().interrupt();
        }
    }
}
