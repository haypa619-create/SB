package com.hayden.bazaarhelper.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads where a menu is drawn, so the slot to click can be outlined. Read-only. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int bazaarhelper$getLeftPos();

    @Accessor("topPos")
    int bazaarhelper$getTopPos();
}
