package net.minestom.server.instance;

import net.minestom.server.coordinate.Pos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * A cross-instance entity transfer request.
 * <p>
 * The ticket travels through the source mailbox ({@code LEAVING}), then
 * through async chunk loading ({@code IN_TRANSIT}), then through the target
 * mailbox ({@code JOINING}). Only the newest ticket of an entity is honored;
 * older tickets are dropped wherever they surface.
 */
@ApiStatus.Internal
public final class TransferTicket {
    private final long id;
    private final Instance target;
    private final Pos spawnPosition;
    /**
     * Tracker still holding the entity, or null when nothing must be left
     * (first spawn, or the leave already ran for a chained transfer).
     */
    private final @Nullable Instance leaveFrom;
    /** Position before the synchronous reassignment, restored if the leave is cancelled. */
    private final Pos rollbackPos;
    private final CompletableFuture<Void> future = new CompletableFuture<>();

    public TransferTicket(long id, Instance target, Pos spawnPosition, @Nullable Instance leaveFrom, Pos rollbackPos) {
        this.id = id;
        this.target = target;
        this.spawnPosition = spawnPosition;
        this.leaveFrom = leaveFrom;
        this.rollbackPos = rollbackPos;
    }

    public long id() {
        return id;
    }

    public Instance target() {
        return target;
    }

    public Pos spawnPosition() {
        return spawnPosition;
    }

    public @Nullable Instance leaveFrom() {
        return leaveFrom;
    }

    public Pos rollbackPos() {
        return rollbackPos;
    }

    public CompletableFuture<Void> future() {
        return future;
    }
}
