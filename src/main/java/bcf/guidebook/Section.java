package bcf.guidebook;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One piece of a chapter. The screen wraps the sections and breaks them into pages; a section never runs over a page
 * end. The set is closed on purpose: a book that needs something new gets a new kind here, so that every mod's book
 * can use it and all books keep looking alike.
 */
public sealed interface Section {
    /** The translation keys this section shows. */
    default List<String> keys() {
        return List.of();
    }

    /** A heading in the heading colour; it never ends a page but moves over with what follows it. */
    record Heading(String key) implements Section {
        @Override
        public List<String> keys() {
            return List.of(key);
        }
    }

    /** A paragraph. */
    record Text(String key, Object... args) implements Section {
        @Override
        public List<String> keys() {
            return List.of(key);
        }
    }

    /** A line somebody says, or a line of lore: italics in the soft colour. */
    record Quote(String key, Object... args) implements Section {
        @Override
        public List<String> keys() {
            return List.of(key);
        }
    }

    /** A line set off in the title colour. */
    record Note(String key, Object... args) implements Section {
        @Override
        public List<String> keys() {
            return List.of(key);
        }
    }

    /** Item icons with a text beside them. */
    record ItemLine(List<ItemStack> icons, String key, Object... args) implements Section {
        @Override
        public List<String> keys() {
            return List.of(key);
        }
    }

    /** A crafting grid: nine stacks, row by row (empty where nothing goes), and what it makes. */
    record Recipe(List<ItemStack> grid, ItemStack result) implements Section {
    }

    /** Square pictures side by side, each {@code size} wide, from textures {@code textureSize} wide. */
    record Pictures(List<Identifier> textures, int size, int textureSize, boolean framed) implements Section {
    }

    /** A square of one item in slots, {@code side} to a side, with the texts beside it. */
    record ItemGrid(ItemStack stack, int side, List<String> textKeys) implements Section {
        @Override
        public List<String> keys() {
            return textKeys;
        }
    }

    /** What follows starts a new page. */
    record Break() implements Section {
    }
}
