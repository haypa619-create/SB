package com.hayden.bazaarhelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Hotkeys (rebind in Options > Controls > Bazaar Helper):
 *   B  open the quote board
 *   N  do the next most urgent thing on the board
 * Kept in its own file because key mapping APIs change between Minecraft versions.
 * Written from Fabric's 26.2 key mapping docs.
 */
public final class PanelKey {
    private static KeyMapping board;
    private static KeyMapping next;

    private PanelKey() {}

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("bazaarhelper", "main"));
        board = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bazaarhelper.panel", InputConstants.Type.KEYSYM, InputConstants.KEY_B, category));
        next = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bazaarhelper.next", InputConstants.Type.KEYSYM, InputConstants.KEY_N, category));
    }

    /** True once per press of the board key. Call every client tick. */
    public static boolean pressed() {
        return consume(board);
    }

    /** True once per press of the next-action key. Call every client tick. */
    public static boolean nextPressed() {
        return consume(next);
    }

    private static boolean consume(KeyMapping key) {
        boolean any = false;
        while (key != null && key.consumeClick()) any = true;
        return any;
    }
}
