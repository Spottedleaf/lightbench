package ca.spottedleaf.lightbench.mixin.client;

import ca.spottedleaf.lightbench.NotAHackISwear;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.util.FrameTimer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(
            method = "drawChart",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;end()V",
                    ordinal = 0
            )
    )
    private void injectLightTimes(PoseStack poseStack, FrameTimer frameTimer, int i, int j, boolean bl, CallbackInfo ci) {
        // I definitely know what this is doing
        if (!bl) {
            // don't render for integrated server
            return;
        }
        frameTimer = NotAHackISwear.LIGHTENGINE_TIMER;
        int k = frameTimer.getLogStart();
        int l = frameTimer.getLogEnd();
        long[] ls = frameTimer.getLog();
        int n = i;
        int o = Math.max(0, ls.length - j);
        int p = ls.length - o;
        int m = frameTimer.wrapIndex(k + o);
        long q = 0L;
        int r = 2147483647;
        int s = -2147483648;

        int v;
        for(v = 0; v < p; ++v) {
            int u = (int)(ls[frameTimer.wrapIndex(m + v)] / 1000000L);
            r = Math.min(r, u);
            s = Math.max(s, u);
            q += (long)u;
        }

        v = this.minecraft.getWindow().getGuiScaledHeight();

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        //bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        ++n; // behind by one frame...
        for(Matrix4f matrix4f = Transformation.identity().getMatrix(); m != l; m = frameTimer.wrapIndex(m + 1)) {
            int w = frameTimer.scaleSampleTo(ls[m], bl ? 30 : 60, bl ? 60 : 20);
            int x = bl ? 100 : 60;
            int y = 0xFF_00_00_00;
            int z = y >> 24 & 255;
            int aa = y >> 16 & 255;
            int ab = y >> 8 & 255;
            int ac = y & 255;
            bufferBuilder.vertex(matrix4f, (float)(n + 1), (float)v, 0.0F).color(aa, ab, ac, z).endVertex();
            bufferBuilder.vertex(matrix4f, (float)(n + 1), (float)(v - w + 1), 0.0F).color(aa, ab, ac, z).endVertex();
            bufferBuilder.vertex(matrix4f, (float)n, (float)(v - w + 1), 0.0F).color(aa, ab, ac, z).endVertex();
            bufferBuilder.vertex(matrix4f, (float)n, (float)v, 0.0F).color(aa, ab, ac, z).endVertex();
            ++n;
        }
    }
}
