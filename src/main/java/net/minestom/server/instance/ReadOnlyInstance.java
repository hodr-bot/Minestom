package net.minestom.server.instance;

import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.BlockHandler;
import net.minestom.server.instance.generator.Generator;
import net.minestom.server.utils.chunk.ChunkSupplier;
import net.minestom.server.coordinate.Point;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A read-only view of a {@link ModelInstance}.
 * <p>
 * Chunk reads go straight to the model chunks (shared references, zero
 * duplication); every write path is rejected. Entities are fully separated:
 * each view has its own tracker and ticks its entities on its own thread.
 * Shared chunks are owned by the model, so views never tick chunks.
 */
public class ReadOnlyInstance extends InstanceContainer {

    private final InstanceContainer model;

    // False while the superclass constructor runs (it installs the chunk
    // supplier and loader through these same setters): writes are only
    // rejected once the view is fully built.
    private boolean sealed;

    public ReadOnlyInstance(ModelInstance model) {
        super(model.registries(), UUID.randomUUID(), model.getDimensionType(), null, Key.key(model.getDimensionName()));
        this.model = model;
        this.srcInstance = model;
        this.tagHandler = model.tagHandler.copy();
        this.sealed = true;
    }

    /**
     * Gets the model this view reads its chunks from.
     *
     * @return the model instance
     */
    public InstanceContainer model() {
        return model;
    }

    @Override
    public @Nullable Chunk getChunk(int chunkX, int chunkZ) {
        return model.getChunk(chunkX, chunkZ);
    }

    @Override
    public Collection<Chunk> getChunks() {
        return model.getChunks();
    }

    @Override
    public CompletableFuture<Chunk> loadChunk(int chunkX, int chunkZ) {
        return model.loadChunk(chunkX, chunkZ);
    }

    @Override
    public CompletableFuture<@Nullable Chunk> loadOptionalChunk(int chunkX, int chunkZ) {
        return model.loadOptionalChunk(chunkX, chunkZ);
    }

    @Override
    public CompletableFuture<Void> generateChunk(int chunkX, int chunkZ, Generator generator) {
        return model.generateChunk(chunkX, chunkZ, generator);
    }

    @Override
    public synchronized void unloadChunk(Chunk chunk) {
        throw new UnsupportedOperationException("Read-only view: chunks belong to the model");
    }

    @Override
    public void setChunkSupplier(ChunkSupplier chunkSupplier) {
        if (!sealed) {
            super.setChunkSupplier(chunkSupplier);
            return;
        }
        throw new UnsupportedOperationException("Read-only view: chunks belong to the model");
    }

    @Override
    public ChunkSupplier getChunkSupplier() {
        return model.getChunkSupplier();
    }

    @Override
    public void setChunkLoader(ChunkLoader chunkLoader) {
        if (!sealed) {
            super.setChunkLoader(chunkLoader);
            return;
        }
        throw new UnsupportedOperationException("Read-only view: chunks belong to the model");
    }

    @Override
    public ChunkLoader getChunkLoader() {
        return model.getChunkLoader();
    }

    @Override
    public @Nullable Generator generator() {
        return model.generator();
    }

    @Override
    public void setGenerator(@Nullable Generator generator) {
        if (!sealed) {
            super.setGenerator(generator);
            return;
        }
        throw new UnsupportedOperationException("Read-only view: chunks belong to the model");
    }

    @Override
    public void enableAutoChunkLoad(boolean enable) {
        model.enableAutoChunkLoad(enable);
    }

    @Override
    public boolean hasEnabledAutoChunkLoad() {
        return model.hasEnabledAutoChunkLoad();
    }

    @Override
    public void setBlock(int x, int y, int z, Block block, boolean doBlockUpdates) {
        throw new UnsupportedOperationException("Read-only view: blocks belong to the model");
    }

    @Override
    public boolean placeBlock(BlockHandler.Placement placement, boolean doBlockUpdates) {
        throw new UnsupportedOperationException("Read-only view: blocks belong to the model");
    }

    @Override
    public boolean breakBlock(Player player, Point blockPosition, BlockFace blockFace, boolean doBlockUpdates) {
        throw new UnsupportedOperationException("Read-only view: blocks belong to the model");
    }

    @Override
    public CompletableFuture<Void> saveInstance() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> saveChunkToStorage(Chunk chunk) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> saveChunksToStorage() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void invalidateSection(int sectionX, int sectionY, int sectionZ) {
        model.invalidateSection(sectionX, sectionY, sectionZ);
    }

    @Override
    protected void tickChunks(long time) {
        // Shared chunks are owned by the model: views tick entities only.
    }
}
