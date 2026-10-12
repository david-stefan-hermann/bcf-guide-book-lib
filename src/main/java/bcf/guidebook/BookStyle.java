package bcf.guidebook;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;

/**
 * What is the same in every guide book: the font, the line heights and the measures of the panel. Changing a value
 * here changes the books of all mods with the next build; the pages break themselves again.
 */
public final class BookStyle {
    /**
     * The font of everything written in the book. For another one, put its definition under
     * {@code assets/bcfguidebooklib/font/<name>.json} and name it here:
     * {@code new FontDescription.Resource(Identifier.fromNamespaceAndPath("bcfguidebooklib", "<name>"))}.
     */
    public static final FontDescription FONT = FontDescription.DEFAULT;
    /** Line heights of headings and of all other text, and the gap under a paragraph. */
    public static final int HEADING_LINE = 10, TEXT_LINE = 9, GAP = 5;

    public static final int PANEL_W = 232, PANEL_H = 230;
    public static final int TAB_W = 26, TAB_H = 24, TAB_GAP = 3;
    public static final int PAGE_MARGIN = 8, TEXT_MARGIN = 6, HEADER_H = 20, FOOTER_H = 24;
    public static final int TEXT_W = PANEL_W - 2 * PAGE_MARGIN - 2 * TEXT_MARGIN;
    public static final int CONTENT_H = PANEL_H - 2 * PAGE_MARGIN - HEADER_H - FOOTER_H;
    public static final int SLOT_SIZE = 18;
    public static final int BUTTON_W = 22, BUTTON_H = 18;

    private BookStyle() {
    }

    /** The text in the book's font. */
    public static MutableComponent styled(MutableComponent text) {
        return text.withStyle(style -> style.withFont(FONT));
    }

    public static MutableComponent translated(String key, Object... args) {
        return styled(Component.translatable(key, args));
    }
}
