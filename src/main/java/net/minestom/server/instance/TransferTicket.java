package net.minestom.server.instance;

import net.minestom.server.coordinate.Pos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A cross-instance entity transfer request.
 * <p>
 * The ticket travels through the source mailbox ({@code LEAVING}), then
 * through async chunk loading ({@code IN_TRANSIT}), then through the target
 * mailbox ({@code JOINING}). Only the newest ticket of an entity is honored;
 * older tickets are dropped wherever they surface.
 * <p>
 * Pure data: completion lives on the entity, keyed by ticket id, so a stale
 * ticket can never complete the wrong transfer.
 *
 * @param id            the ticket id, unique per entity
 * @param target        the destination instance
 * @param spawnPosition where the entity appears in the target
 * @param leaveFrom     the tracker still holding the entity, or null when nothing must be left (first spawn, or the leave already ran for a chained transfer)
 * @param rollbackPos   the position before the synchronous reassignment, restored if the leave is cancelled
 */
@ApiStatus.Internal
public record TransferTicket(
        long id,
        Instance target,
        Pos spawnPosition,
        @Nullable Instance leaveFrom,
        Pos rollbackPos
) {
}
