package ca.spottedleaf.lightbench.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public class LevelRenderMixin {

    @Redirect(
            method = "renderLevel",
            at = @At(
                    target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runUpdates(IZZ)I",
                    value = "INVOKE",
                    ordinal = 0
            )
    )
    public int timeUpdatesCall(LevelLightEngine levelLightEngine, int i, boolean bl, boolean bl2) {
        long start = System.nanoTime();
        int ret = levelLightEngine.runUpdates(i, bl, bl2);
        long end = System.nanoTime();

        long diff = end - start;
        double ms = diff * 1.0e-6;

        if (ret != 2147483647 && ret != 0) { // oops my bad, Starlight returns 0 when no update are done
            System.out.println("Time for updates: " + ms + ", total updates (invalid on Starlight): " + (2147483647 - ret));
        }
        return ret;
    }

}
