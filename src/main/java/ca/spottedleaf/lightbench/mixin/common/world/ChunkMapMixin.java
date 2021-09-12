package ca.spottedleaf.lightbench.mixin.common.world;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin extends ChunkStorage implements ChunkHolder.PlayerProvider {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Nullable
    protected abstract ChunkHolder getUpdatingChunkIfPresent(long l);

    @Unique
    private Thread lightExecutor;

    public ChunkMapMixin(File file, DataFixer dataFixer, boolean bl) {
        super(file, dataFixer, bl);
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    target = "Lnet/minecraft/util/thread/ProcessorMailbox;create(Ljava/util/concurrent/Executor;Ljava/lang/String;)Lnet/minecraft/util/thread/ProcessorMailbox;",
                    value = "INVOKE"
            )
    )
    private ProcessorMailbox<Runnable> createSingleThreadedExecutor(Executor executor, String string) {
        if (!string.equalsIgnoreCase("light")) {
            return ProcessorMailbox.create(executor, string);
        }
        ExecutorService ret = Executors.newFixedThreadPool(1, (Runnable run) -> {
            if (this.lightExecutor != null) {
                throw new IllegalStateException();
            }
            Thread r = this.lightExecutor = new Thread(run);
            r.setName("Light Executor");

            return r;
        });
        return ProcessorMailbox.create(ret, string);
    }

    private boolean run;

    @Inject(
            method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At("HEAD")
    )
    public void tick(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        // saving is for losers
        if (!this.level.dimensionType().hasSkyLight()) {
            // do not care about nether/end
            return;
        }

        if (!Boolean.getBoolean("lightbench.gentest")) {
            return;
        }

        if (run) {
            return;
        }
        this.run = true;

        ThreadMXBean threadManagement = ManagementFactory.getThreadMXBean();

        System.out.println("Starting warmup");

        int offX = -10000;
        int offZ = -10000;
        int radius = 50;

        long cpustart = threadManagement.getThreadCpuTime(this.lightExecutor.getId());
        long start = System.nanoTime();

        int generatedChunks = 0;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                ++generatedChunks;
                this.level.getChunk(dx + offX, dz + offZ);
            }
        }

        // done now

        long cpuend = threadManagement.getThreadCpuTime(this.lightExecutor.getId());
        long end = System.nanoTime();

        System.out.println("Completed warmup with total cpu time " + ((cpuend - cpustart) * 1.0e-6) + "ms");
        System.out.println("Time to generate " + generatedChunks + " chunks: " + ((end - start) * 1.0e-9) + "s");

        System.out.println("Starting real test now");
        offX = 10000;
        offZ = 10000;
        radius = 8;
        int batchsize = 5;
        int targetChunks = 101*101;
        int times = (int)Math.ceil((double)targetChunks/(double)((radius * 2 + 1)*(radius * 2 + 1)));
        generatedChunks = 0;

        cpustart = threadManagement.getThreadCpuTime(this.lightExecutor.getId());
        start = System.nanoTime();

        for (int i = 0; i < times; ++i) {
            // build coordinate list
            LongLinkedOpenHashSet coordinates = new LongLinkedOpenHashSet();

            for (int r = 0; r <= radius; ++r) {
                // top
                for (int x = -r; x <= r; ++x) {
                    coordinates.add(ChunkPos.asLong(x + offX, r + offZ));
                }

                // right
                for (int z = r; z >= -r; --z) {
                    coordinates.add(ChunkPos.asLong(r + offX, z + offZ));
                }

                // down
                for (int x = r; x >= -r; --x) {
                    coordinates.add(ChunkPos.asLong(x + offX, -r + offZ));
                }

                // left
                for (int z = -r; z <= r; ++z) {
                    coordinates.add(ChunkPos.asLong(-r + offX, -z + offZ));
                }
            }

            gen_loop:
            for (;;) {
                LongArrayList queued = new LongArrayList();
                for (int k = 0; k < batchsize; ++k) {
                    if (coordinates.isEmpty()) {
                        break;
                    }
                    long coordinate = coordinates.removeFirstLong();
                    this.level.getChunkSource().addRegionTicket(TicketType.DRAGON, new ChunkPos(coordinate), 0, Unit.INSTANCE);
                    // this should ensure the chunks are loaded in the order we queue them
                    this.level.getChunkSource().runDistanceManagerUpdates();
                    queued.add(coordinate);
                }
                // drain the chunks queued
                for (Long coord : queued) {
                    ++generatedChunks;
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = this.getUpdatingChunkIfPresent(coord.longValue()).getFutureIfPresent(ChunkStatus.FULL);
                    ((BlockableEventLoop<Runnable>)this.level.getChunkSource().mainThreadProcessor)
                            .managedBlock(future::isDone);
                    if (!future.isDone() || future.join().right().isPresent()) {
                        throw new IllegalStateException("Chunk load cancelled?");
                    }
                }

                if (queued.size() < batchsize) {
                    break;
                }
            }

            offX += (radius + 12)*2;
            offZ += (radius + 12)*2;
        }

        cpuend = threadManagement.getThreadCpuTime(this.lightExecutor.getId());
        end = System.nanoTime();

        System.out.println("Completed real test with total cpu time " + ((cpuend - cpustart) * 1.0e-6) + "ms");
        System.out.println("Time to generate " + generatedChunks + " chunks: " + ((end - start) * 1.0e-9) + "s");
    }
}
