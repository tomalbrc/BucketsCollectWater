package de.tomalbrc.bucketscollectwater;

import com.hypixel.hytale.builtin.adventure.farming.config.modifiers.WaterGrowthModifierAsset;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.HashUtil;
import com.hypixel.hytale.protocol.Rangef;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktick.BlockTickStrategy;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class Systems {
    private static final String FILLED_WATER_STATE = "Filled_Water";
    private static final String SFX_WATER_MOVE_IN = "SFX_Water_MoveIn";
    private static final String WATER_ASSET_NAME = "Water";

    public static class OnWaterCollectingBucketBlockAdded extends RefSystem<ChunkStore> {
        private final Query<ChunkStore> QUERY = Query.and(BlockModule.BlockStateInfo.getComponentType(), WaterCollectingBucketBlock.getComponentType());

        @Override
        public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
            WaterCollectingBucketBlock farmingBlock = commandBuffer.getComponent(ref, WaterCollectingBucketBlock.getComponentType());
            assert (farmingBlock != null);
            BlockModule.BlockStateInfo info = commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
            assert (info != null);
            BlockChunk blockChunk = commandBuffer.getComponent(info.getChunkRef(), BlockChunk.getComponentType());

            if (blockChunk != null && farmingBlock.getLastTickGameTime() == null) {
                farmingBlock.setLastTickGameTime(store.getExternalData().getWorld().getEntityStore().getStore().getResource(WorldTimeResource.getResourceType()).getGameTime());
                blockChunk.markNeedsSaving();
            }

            int x = ChunkUtil.xFromBlockInColumn(info.getIndex());
            int y = ChunkUtil.yFromBlockInColumn(info.getIndex());
            int z = ChunkUtil.zFromBlockInColumn(info.getIndex());
            BlockComponentChunk blockComponentChunk = commandBuffer.getComponent(info.getChunkRef(), BlockComponentChunk.getComponentType());
            assert (blockComponentChunk != null);

            ChunkColumn column = commandBuffer.getComponent(info.getChunkRef(), ChunkColumn.getComponentType());
            assert (column != null);
            Ref<ChunkStore> section = column.getSection(ChunkUtil.chunkCoordinate(y));
            BlockSection blockSection = commandBuffer.getComponent(section, BlockSection.getComponentType());
            tickBucket(commandBuffer, blockChunk, blockSection, section, ref, farmingBlock, x, y, z);
        }

        @Override
        public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason removeReason, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        }


        @Override
        @Nullable
        public Query<ChunkStore> getQuery() {
            return QUERY;
        }
    }

    public static class WaterCollectingBucketTickingSystem extends EntityTickingSystem<ChunkStore> {
        private static final Query<ChunkStore> QUERY = Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType());

        @Override
        public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
            BlockSection blocks = archetypeChunk.getComponent(index, BlockSection.getComponentType());
            if (blocks == null || blocks.getTickingBlocksCountCopy() == 0) return;

            ChunkSection section = archetypeChunk.getComponent(index, ChunkSection.getComponentType());
            if (section == null || section.getChunkColumnReference() == null || !section.getChunkColumnReference().isValid())
                return;

            BlockComponentChunk blockComponentChunk = commandBuffer.getComponent(section.getChunkColumnReference(), BlockComponentChunk.getComponentType());
            BlockChunk blockChunk = commandBuffer.getComponent(section.getChunkColumnReference(), BlockChunk.getComponentType());
            if (blockComponentChunk == null || blockChunk == null) return;

            Ref<ChunkStore> ref = archetypeChunk.getReferenceTo(index);

            blocks.forEachTicking(blockComponentChunk, commandBuffer, section.getY(), (compChunk, cmdBuf, localX, localY, localZ, blockId) -> {
                Ref<ChunkStore> blockRef = compChunk.getEntityReference(ChunkUtil.indexBlockInColumn(localX, localY, localZ));
                if (blockRef == null) return BlockTickStrategy.IGNORED;

                WaterCollectingBucketBlock bucketBlock = cmdBuf.getComponent(blockRef, WaterCollectingBucketBlock.getComponentType());
                if (bucketBlock != null) {
                    tickBucket(cmdBuf, blockChunk, blocks, ref, blockRef, bucketBlock, localX, localY, localZ);
                    return BlockTickStrategy.SLEEP;
                }
                return BlockTickStrategy.IGNORED;
            });
        }

        @Nullable
        @Override
        public Query<ChunkStore> getQuery() {
            return QUERY;
        }
    }

    static void tickBucket(CommandBuffer<ChunkStore> commandBuffer, BlockChunk blockChunk, BlockSection blockSection, Ref<ChunkStore> sectionRef, Ref<ChunkStore> blockRef, WaterCollectingBucketBlock bucketBlock, int x, int y, int z) {
        World world = commandBuffer.getExternalData().getWorld();
        var worldTimeResource = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        Instant currentTime = worldTimeResource.getGameTime();

        int blockAssetId = blockSection.get(x, y, z);
        BlockType blockType = BlockType.getAssetMap().getAsset(blockAssetId);
        if (blockType == null || blockType.getItem() == null) return;

        float currentProgress = bucketBlock.getFillProgress();

        if (currentProgress < 0) {
            currentProgress = 0.0F;
            bucketBlock.setFillProgress(0.0F);
        }

        ChunkSection section = commandBuffer.getComponent(sectionRef, ChunkSection.getComponentType());
        if (section == null) return;

        int worldX = ChunkUtil.worldCoordFromLocalCoord(section.getX(), x);
        int worldY = ChunkUtil.worldCoordFromLocalCoord(section.getY(), y);
        int worldZ = ChunkUtil.worldCoordFromLocalCoord(section.getZ(), z);

        if (bucketBlock.getLastTickGameTime() == null) {
            bucketBlock.setLastTickGameTime(worldTimeResource.getGameTime());
        }

        long remainingTimeSeconds = currentTime.getEpochSecond() - bucketBlock.getLastTickGameTime().getEpochSecond();
        Rangef range = new Rangef((60*10), (60*15)); // 20-30mins in-game time

        double rand = HashUtil.random(5, worldX, worldY, worldZ);
        double baseDuration = range.min + (range.max - range.min) * rand;

        long remainingDurationSeconds = Math.round(baseDuration * (1.0F - currentProgress % 1.0F));
        remainingDurationSeconds = Math.round(remainingDurationSeconds);

        bucketBlock.setLastTickGameTime(currentTime);
        blockChunk.markNeedsSaving();

        if (currentProgress >= 1) {
            int filledWaterId = BlockType.getAssetMap().getIndex(blockType.getItem().getItemIdForState(FILLED_WATER_STATE));
            blockSection.set(x, y, z, filledWaterId, blockSection.getRotationIndex(x, y, z), blockSection.getFiller(x, y, z));
            blockChunk.markNeedsSaving();
            commandBuffer.removeEntity(blockRef, RemoveReason.REMOVE);

            SoundUtil.playSoundEvent3d(
                    SoundEvent.getAssetMap().getIndex(SFX_WATER_MOVE_IN),
                    SoundCategory.SFX,
                    worldX, worldY, worldZ,
                    commandBuffer.getExternalData().getWorld().getEntityStore().getStore()
            );
        } else {
            if (remainingDurationSeconds > 0) {
                if (checkIfRaining(commandBuffer, sectionRef, x, y, z, blockAssetId)) {
                    currentProgress += (float) (remainingTimeSeconds / baseDuration);
                    bucketBlock.setFillProgress(currentProgress);
                }
            } else {
                if (checkIfRaining(commandBuffer, sectionRef, x, y, z, blockAssetId)) {
                    if (baseDuration > 0) {
                        currentProgress += (float) (remainingTimeSeconds / baseDuration);
                        bucketBlock.setFillProgress(currentProgress);
                    }
                }
            }

            blockSection.scheduleTick(ChunkUtil.indexBlock(x, y, z), currentTime.plus(2, ChronoUnit.MINUTES)); // in-game minutes
        }
    }

    static boolean checkIfRaining(CommandBuffer<ChunkStore> commandBuffer, Ref<ChunkStore> sectionRef, int x, int y, int z, int blockIdx) {
        var asset = WaterGrowthModifierAsset.getAssetMap().getAsset(WATER_ASSET_NAME); // we re-use tilled soil mechanics
        if (!(asset instanceof WaterGrowthModifierAsset waterGrowthModifierAsset)) return false;

        ChunkSection section = commandBuffer.getComponent(sectionRef, ChunkSection.getComponentType());
        if (section == null) return false;

        Ref<ChunkStore> chunkRef = section.getChunkColumnReference();
        BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return false;

        var store = commandBuffer.getExternalData().getWorld().getEntityStore().getStore();
        var weatherResource = store.getResource(WeatherResource.getResourceType());

        int environment = blockChunk.getEnvironment(x, y, z);
        int weatherId = weatherResource.getForcedWeatherIndex() != 0 ? weatherResource.getForcedWeatherIndex() : weatherResource.getWeatherIndexForEnvironment(environment);

        if (waterGrowthModifierAsset.getWeatherIds().contains(weatherId)) {
            boolean unobstructed = true;
            for (int searchY = y + 1; searchY < 320; ++searchY) {
                int block = blockChunk.getBlock(x, searchY, z);
                if (block == 0 || block == blockIdx) continue;
                unobstructed = false;
                break;
            }
            return unobstructed;
        }

        return false;
    }
}
