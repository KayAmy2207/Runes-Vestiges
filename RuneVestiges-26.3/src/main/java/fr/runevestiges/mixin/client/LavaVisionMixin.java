package fr.runevestiges.mixin.client;

import fr.runevestiges.content.ModEnchantmentEffects;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.renderer.fog.FogRenderer")
public abstract class LavaVisionMixin {
    @Redirect(
        method = "getFogType",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"
        ),
        require = 1
    )
    private static FogType showThroughLavaColor(Camera camera) {
        return LavaVisionMixin.fogTypeForArmor(camera);
    }

    private static FogType fogTypeForArmor(Camera camera) {
        Minecraft client = Minecraft.getInstance();
        if (camera.getFluidInCamera() == FogType.LAVA && client.player != null
            && ModEnchantmentEffects.hasObsidianArmor(client.player)) {
            return FogType.NONE;
        }
        return camera.getFluidInCamera();
    }
}
