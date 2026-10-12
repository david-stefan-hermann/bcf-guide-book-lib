package bcf.guidebook;

import net.minecraft.resources.Identifier;

/**
 * A mod's own colours for its guide book (ARGB). The layout is not part of the theme: that is {@link BookStyle}, the
 * same for all books. With a {@link #texture} the panel and its page are that picture ({@link BookStyle#PANEL_W} by
 * {@link BookStyle#PANEL_H} pixels, the page inside the margins and above the footer); tabs and buttons are always
 * drawn in the theme's colours.
 */
public record BookTheme(Identifier texture, int panel, int border, int light, int shadow, int tab, int tabHover,
                        int page, int pageEdge, int rule, int title, int heading, int body, int soft, int slot,
                        int slotEdge, int arrow, int button, int buttonHover, int buttonOff, int buttonEdge,
                        int buttonLight, int buttonShadow, int buttonText, int buttonTextOff, int pageNumber) {

    /** Starts from a plain slate-grey panel with a white page. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Identifier texture;
        private int panel = 0xFF6E7480, border = 0xFF000000, light = 0xFF9AA0AC, shadow = 0xFF454A54;
        private int tab = 0xFF565B66, tabHover = 0xFF8A909C;
        private int page = 0xFFF6F3EC, pageEdge = 0xFFB4B0A6;
        private Integer rule;
        private int title = 0xFF202020, heading = 0xFF3A3A8A, body = 0xFF303030, soft = 0xFF6A6A6A;
        private int slot = 0xFFE4E1D8, slotEdge = 0xFF9C988E, arrow = 0xFF7A766C;
        private int button = 0xFF4E535E, buttonHover = 0xFF6A7080, buttonOff = 0xFF7C818C, buttonEdge = 0xFF22252B,
                buttonLight = 0xFF8E94A0, buttonShadow = 0xFF33373F, buttonText = 0xFFFFFFFF, buttonTextOff = 0xFFB8BCC4;
        private Integer pageNumber;

        private Builder() {
        }

        /** A picture as panel and page instead of the drawn ones; {@link #panel} still colours the open tab. */
        public Builder texture(Identifier texture) {
            this.texture = texture;
            return this;
        }

        /** The panel around the page, with its bright and dark edges. */
        public Builder panel(int panel, int light, int shadow) {
            this.panel = panel;
            this.light = light;
            this.shadow = shadow;
            return this;
        }

        /** The outline of panel and tabs. */
        public Builder border(int border) {
            this.border = border;
            return this;
        }

        /** The tabs of the chapters that are not open. */
        public Builder tabs(int tab, int tabHover) {
            this.tab = tab;
            this.tabHover = tabHover;
            return this;
        }

        /** The page and its edge, which also frames pictures. */
        public Builder page(int page, int pageEdge) {
            this.page = page;
            this.pageEdge = pageEdge;
            return this;
        }

        /** The line under the chapter title; the page's edge colour unless set. */
        public Builder rule(int rule) {
            this.rule = rule;
            return this;
        }

        /** What is written: chapter title, headings, paragraphs, quotes. */
        public Builder ink(int title, int heading, int body, int soft) {
            this.title = title;
            this.heading = heading;
            this.body = body;
            this.soft = soft;
            return this;
        }

        /** Item slots and the arrow of a recipe. */
        public Builder slots(int slot, int slotEdge, int arrow) {
            this.slot = slot;
            this.slotEdge = slotEdge;
            this.arrow = arrow;
            return this;
        }

        /** The page buttons: face (plain, hovered, inactive), outline, top and bottom edge, lettering. */
        public Builder buttons(int button, int hover, int off, int edge, int light, int shadow, int text, int textOff) {
            this.button = button;
            this.buttonHover = hover;
            this.buttonOff = off;
            this.buttonEdge = edge;
            this.buttonLight = light;
            this.buttonShadow = shadow;
            this.buttonText = text;
            this.buttonTextOff = textOff;
            return this;
        }

        /** The page number on the panel; the buttons' lettering colour unless set. */
        public Builder pageNumber(int pageNumber) {
            this.pageNumber = pageNumber;
            return this;
        }

        public BookTheme build() {
            return new BookTheme(texture, panel, border, light, shadow, tab, tabHover, page, pageEdge,
                    rule == null ? pageEdge : rule, title, heading, body, soft, slot, slotEdge, arrow, button,
                    buttonHover, buttonOff, buttonEdge, buttonLight, buttonShadow, buttonText, buttonTextOff,
                    pageNumber == null ? buttonText : pageNumber);
        }
    }
}
