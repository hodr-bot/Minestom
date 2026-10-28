package net.minestom.server.instance;

import net.minestom.server.MinecraftServer;
import net.minestom.server.thread.MinestomThread;
import org.jetbrains.annotations.ApiStatus;

/**
 * Platform thread owning a single {@link InstanceContainer}.
 * <p>
 * Each instance ticks on its own thread with a phase offset so instances do
 * not all start their tick at the same time. There is no global tick barrier:
 * instances are independent and communicate through mailboxes.
 * <p>
 * The thread also owns the CPU budget of its instance. Every slot refills a
 * token bucket and only ticks the gameplay when the bucket can pay for a full
 * tick; skipped slots degrade the local TPS instead of lagging the others.
 */
@ApiStatus.Internal
public final class InstanceThread extends MinestomThread {

    private static final int MAX_LAG_SLOTS = 5;

    private static final long TICK_NANOS = (long) MinecraftServer.TICK_MS * 1_000_000L;

    private final InstanceContainer owner;

    private final long phaseOffsetNanos;

    private volatile boolean running = true;

    private double budgetBalanceNanos;

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
        long nextSlot = System.nanoTime() + phaseOffsetNanos;

        while (running) {
            final long wait = nextSlot - System.nanoTime();

            if (wait > 0) {
                sleepNanos(wait);
                continue;
            }

            final long behind = System.nanoTime() - nextSlot;

            if (behind > MAX_LAG_SLOTS * TICK_NANOS) {
                // Too far behind: drop missed slots, never catch up.
                nextSlot = System.nanoTime();
            } else {
                nextSlot += TICK_NANOS;
            }

            try {
                runSlot(System.currentTimeMillis());
            } catch (Throwable t) {
                MinecraftServer.getExceptionManager().handleException(t);
            }
        }
    }

    private void runSlot(long time) {
        owner.drainMailbox();

        final long grant = owner.currentBudgetNanos();
        budgetBalanceNanos = Math.min(budgetBalanceNanos + grant, 2.0 * grant);

        if (budgetBalanceNanos < TICK_NANOS) {
            owner.noteSkippedTick();
            return;
        }

        final long start = System.nanoTime();
        owner.tickOwned(time);
        budgetBalanceNanos -= System.nanoTime() - start;
    }

    private void sleepNanos(long nanos) {
        if (nanos <= 0 || !running) return;

        try {
            final long millis = nanos / 1_000_000L;
            final int remainder = (int) (nanos % 1_000_000L);
            Thread.sleep(millis, remainder);
        } catch (InterruptedException _) {
            // Shutdown signal, the loop condition re-checks running.
            Thread.currentThread().interrupt();
        }
    }
}
