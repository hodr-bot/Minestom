package net.minestom.server.instance;

import net.minestom.server.MinecraftServer;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.DimensionType;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A template instance: it holds chunks but is never registered and never
 * ticks.
 * <p>
 * Build the world once in the model, then expose it through
 * {@link ReadOnlyInstance} views created with {@link #createView()}. Views
 * share the model chunks by reference (zero duplication) while keeping their
 * own entities on their own tick thread.
 */
public class ModelInstance extends InstanceContainer {

    public ModelInstance(
            Registries registries,
            UUID uuid,
            RegistryKey<DimensionType> dimensionType,
            @Nullable ChunkLoader loader,
            Key dimensionName
    ) {
        super(registries, uuid, dimensionType, loader, dimensionName);
    }

    public ModelInstance(Registries registries, UUID uuid, RegistryKey<DimensionType> dimensionType) {
        this(registries, uuid, dimensionType, null, dimensionType.key());
    }

    public ModelInstance(UUID uuid, RegistryKey<DimensionType> dimensionType) {
        this(MinecraftServer.getRegistries(), uuid, dimensionType);
    }

    /**
     * Creates an unregistered read-only view of this model. Register the view
     * with the {@link InstanceManager} to tick it on its own thread.
     *
     * @return the new view, sharing this model chunks by reference
     */
    public ReadOnlyInstance createView() {
        return new ReadOnlyInstance(this);
    }

    @Override
    public void startTickThread(long phaseOffsetNanos) {
        throw new UnsupportedOperationException("Model instances are templates: register their views, not the model");
    }
}
