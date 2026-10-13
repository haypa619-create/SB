package com.hayden.bazaarhelper;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
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
    private static KeyMapping click;

    private PanelKey() {}

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("bazaarhelper", "main"));
        board = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bazaarhelper.panel", InputConstants.Type.KEYSYM, InputConstants.KEY_B, category));
        next = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bazaarhelper.next", InputConstants.Type.KEYSYM, InputConstants.KEY_N, category));
        click = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.bazaarhelper.click", InputConstants.Type.KEYSYM, InputConstants.KEY_RETURN, category));
    }

    /** True once per press of the board key. Call every client tick. */
    public static boolean pressed() {
        return consume(board);
    }

    /** True once per press of the next-action key. Call every client tick. */
    public static boolean nextPressed() {
        return consume(next);
    }

    /**
     * Inside a menu or the board, key presses go to the screen instead of the key mapping,
     * so screens check them here.
     */
    public static boolean isNext(KeyEvent e) {
        return next != null && next.matches(e);
    }

    /** The "click the green slot" key (Enter by default; numpad Enter too). */
    public static boolean isClick(KeyEvent e) {
        if (click == null) return false;
        if (click.matches(e)) return true;
        return click.getTranslatedKeyMessage().getString().equalsIgnoreCase("Enter") && e.key() == InputConstants.KEY_NUMPADENTER;
    }

    public static String clickKeyName() {
        return click == null ? "Enter" : click.getTranslatedKeyMessage().getString();
    }

    public static boolean isBoard(KeyEvent e) {
        return board != null && board.matches(e);
    }

    /**
     * Minecraft gives a key to only one control, so if another mod also uses N (or B) ours
     * never fires. Returns which of ours share a key with something else, or null.
     */
    public static String conflicts() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return null;
        StringBuilder sb = new StringBuilder();
        for (KeyMapping mine : new KeyMapping[]{board, next}) {
            if (mine == null || mine.isUnbound()) continue;
            for (KeyMapping other : mc.options.keyMappings) {
                if (other != mine && other != board && other != next && other.same(mine)) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(mine == board ? "Open Quote Board" : "Next Action")
                            .append(" (").append(mine.getTranslatedKeyMessage().getString()).append(")");
                    break;
                }
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static boolean consume(KeyMapping key) {
        boolean any = false;
        while (key != null && key.consumeClick()) any = true;
        return any;
    }
}
