package de.tomalbrc.bucketscollectwater;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public class BucketsCollectWaterPlugin extends JavaPlugin {
    private static BucketsCollectWaterPlugin instance;

    private ComponentType<ChunkStore, WaterCollectingBucketBlock> waterCollectingBucketComponentType;

    public BucketsCollectWaterPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static BucketsCollectWaterPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        this.waterCollectingBucketComponentType = this.getChunkStoreRegistry().registerComponent(WaterCollectingBucketBlock.class, "WaterCollectingBucketBlock", WaterCollectingBucketBlock.CODEC);
        this.getChunkStoreRegistry().registerSystem(new Systems.WaterCollectingBucketTickingSystem());
        this.getChunkStoreRegistry().registerSystem(new Systems.OnWaterCollectingBucketBlockAdded());
    }

    @Override
    protected void start() {
    }

    @Override
    protected void shutdown() {
        instance = null;
    }

    public ComponentType<ChunkStore, WaterCollectingBucketBlock> getWaterCollectingBucketComponentType() {
        return waterCollectingBucketComponentType;
    }
}