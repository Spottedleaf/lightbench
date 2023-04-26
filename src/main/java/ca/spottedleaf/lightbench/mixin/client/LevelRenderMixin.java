package ca.spottedleaf.lightbench.mixin.client;

import ca.spottedleaf.lightbench.NotAHackISwear;
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
                    target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runLightUpdates()I",
                    value = "INVOKE",
                    ordinal = 0
            )
    )
    public int timeUpdatesCall(LevelLightEngine levelLightEngine) {
        long start = System.nanoTime();
        int ret = levelLightEngine.runLightUpdates();
        long end = System.nanoTime();

        NotAHackISwear.LIGHTENGINE_TIMER.logFrameDuration(end - start);

        long diff = end - start;
        double ms = diff * 1.0e-6;

        if (ret != 0) {
            if (Boolean.getBoolean("lightbench.debugout")) {
                System.out.println("Time for updates: " + ms + ", total updates (invalid on Starlight): " + ret);
            }
        }
        return ret;
    }

}
