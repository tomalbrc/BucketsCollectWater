package de.tomalbrc.bucketscollectwater;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nullable;
import java.time.Instant;

public class WaterCollectingBucketBlock implements Component<ChunkStore> {
    public static final BuilderCodec<WaterCollectingBucketBlock> CODEC = BuilderCodec.builder(WaterCollectingBucketBlock.class, WaterCollectingBucketBlock::new)
            .append(new KeyedCodec<>("Progress", Codec.FLOAT), (farmingBlock, growthProgress) -> farmingBlock.fillProgress = growthProgress, (farmingBlock) -> farmingBlock.fillProgress == 0.0F ? null : farmingBlock.fillProgress).add()
            .append(new KeyedCodec<>("LastTickGameTime", Codec.INSTANT), (farmingBlock, lastTickGameTime) -> farmingBlock.lastTickGameTime = lastTickGameTime, (farmingBlock) -> farmingBlock.lastTickGameTime).add()
            .build();

    private float fillProgress;
    private Instant lastTickGameTime;
    private float spreadRate = 1.0F;

    public static ComponentType<ChunkStore, WaterCollectingBucketBlock> getComponentType() {
        return BucketsCollectWaterPlugin.get().getWaterCollectingBucketComponentType();
    }

    public WaterCollectingBucketBlock() {
    }

    public WaterCollectingBucketBlock(float fillProgress, Instant lastTickGameTime, float spreadRate) {
        this.fillProgress = fillProgress;
        this.lastTickGameTime = lastTickGameTime;
        this.spreadRate = spreadRate;
    }

    public float getFillProgress() {
        return this.fillProgress;
    }

    public void setFillProgress(float fillProgress) {
        this.fillProgress = fillProgress;
    }

    public Instant getLastTickGameTime() {
        return this.lastTickGameTime;
    }

    public void setLastTickGameTime(Instant lastTickGameTime) {
        this.lastTickGameTime = lastTickGameTime;
    }

    @Nullable
    public Component<ChunkStore> clone() {
        return new WaterCollectingBucketBlock(this.fillProgress, this.lastTickGameTime, this.spreadRate);
    }
}
