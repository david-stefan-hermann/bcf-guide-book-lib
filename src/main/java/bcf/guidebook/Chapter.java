package bcf.guidebook;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A chapter with its tab. By convention the things a mod adds (blocks, items, trees) have their tabs on the left of
 * the book and the topics on the right.
 */
public record Chapter(String title, ItemStack icon, Side side, List<Section> sections) {
    public enum Side {
        LEFT, RIGHT
    }
}
