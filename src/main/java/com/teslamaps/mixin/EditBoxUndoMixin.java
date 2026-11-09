package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;

// NEU "text field tweaks": Ctrl+Z / Ctrl+Y (Ctrl+Shift+Z) in text fields
@Mixin(EditBox.class)
public abstract class EditBoxUndoMixin {

    @Shadow public abstract void setValue(String value);
    @Shadow public abstract String getValue();
    @Shadow public abstract boolean canConsumeInput();

    @Unique private ArrayDeque<String> teslamaps$undo;
    @Unique private ArrayDeque<String> teslamaps$redo;
    @Unique private String teslamaps$last = "";
    @Unique private boolean teslamaps$applying;

    @Inject(method = "onValueChange", at = @At("HEAD"))
    private void teslamaps$record(String value, CallbackInfo ci) {
        if (teslamaps$applying) {
            teslamaps$last = value;
            return;
        }
        if (!TeslaMapsConfig.get().textFieldUndo) {
            teslamaps$last = value;
            return;
        }
        if (value.equals(teslamaps$last)) return;
        if (teslamaps$undo == null) {
            teslamaps$undo = new ArrayDeque<>();
            teslamaps$redo = new ArrayDeque<>();
        }
        teslamaps$undo.push(teslamaps$last);
        if (teslamaps$undo.size() > 50) teslamaps$undo.removeLast();
        teslamaps$redo.clear();
        teslamaps$last = value;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$undoKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!event.hasControlDown() || !TeslaMapsConfig.get().textFieldUndo || !canConsumeInput()) return;
        int key = event.key();
        boolean redo = key == com.mojang.blaze3d.platform.InputConstants.KEY_Y || (key == com.mojang.blaze3d.platform.InputConstants.KEY_Z && event.hasShiftDown());
        if (!redo && key != com.mojang.blaze3d.platform.InputConstants.KEY_Z) return;
        ArrayDeque<String> from = redo ? teslamaps$redo : teslamaps$undo;
        ArrayDeque<String> to = redo ? teslamaps$undo : teslamaps$redo;
        if (from != null && !from.isEmpty()) {
            to.push(getValue());
            teslamaps$applying = true;
            try {
                setValue(from.pop());
            } finally {
                teslamaps$applying = false;
            }
        }
        cir.setReturnValue(true);
    }
}
