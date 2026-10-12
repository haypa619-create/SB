package com.hayden.bazaarhelper.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the mod read and fill the text lines of an open sign editor.
 * Filling a line sends nothing to the server; the text is only sent when you press Done.
 */
@Mixin(AbstractSignEditScreen.class)
public interface SignEditAccessor {
    @Accessor("messages")
    String[] bazaarhelper$getMessages();
}
