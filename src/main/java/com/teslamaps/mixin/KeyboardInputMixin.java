package com.teslamaps.mixin;

import com.teslamaps.debug.AgentBridge;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void teslamaps$agentInput(CallbackInfo ci) {
        if (!AgentBridge.hasInputOverride()) return;
        Input in = AgentBridge.inputOverride();
        this.keyPresses = in;
        float fwd = (in.forward() ? 1f : 0f) - (in.backward() ? 1f : 0f);
        float side = (in.left() ? 1f : 0f) - (in.right() ? 1f : 0f);
        this.moveVector = new Vec2(side, fwd).normalized();
    }
}
