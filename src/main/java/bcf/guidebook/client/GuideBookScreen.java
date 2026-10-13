package bcf.guidebook.client;

import bcf.guidebook.Book;
import bcf.guidebook.BookStyle;
import bcf.guidebook.BookTheme;
import bcf.guidebook.Chapter;
import bcf.guidebook.Section;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static bcf.guidebook.BookStyle.BUTTON_H;
import static bcf.guidebook.BookStyle.BUTTON_W;
import static bcf.guidebook.BookStyle.CONTENT_H;
import static bcf.guidebook.BookStyle.FOOTER_H;
import static bcf.guidebook.BookStyle.GAP;
import static bcf.guidebook.BookStyle.HEADER_H;
import static bcf.guidebook.BookStyle.HEADING_LINE;
import static bcf.guidebook.BookStyle.PAGE_MARGIN;
import static bcf.guidebook.BookStyle.PANEL_H;
import static bcf.guidebook.BookStyle.PANEL_W;
import static bcf.guidebook.BookStyle.SLOT_SIZE;
import static bcf.guidebook.BookStyle.TAB_GAP;
import static bcf.guidebook.BookStyle.TAB_H;
import static bcf.guidebook.BookStyle.TAB_W;
import static bcf.guidebook.BookStyle.TEXT_LINE;
import static bcf.guidebook.BookStyle.TEXT_MARGIN;
import static bcf.guidebook.BookStyle.TEXT_W;

/**
 * The guide book of every mod: a panel with one page, the tabs of the chapters on its left and right, arrows and the
 * page number below. The screen wraps the chapters' sections and breaks them into pages itself: a section that does
 * not fit on the page starts the next one, so no text can run off the page. Turned with the tabs, the arrows, the
 * arrow and page keys or the mouse wheel. The measures and the font are {@link BookStyle}, the colours the book's
 * {@link BookTheme}.
 */
public class GuideBookScreen extends Screen {
    private static final int RECIPE_W = 3 * SLOT_SIZE + 6 + 13 + 6 + SLOT_SIZE; // grid, arrow, result
    private static final Set<String> LOGGED = new HashSet<>();
    /** By mod: the page open when the book was last closed; this session only. */
    private static final Map<String, Integer> LAST_PAGE = new HashMap<>();

    private final Book book;
    private final BookTheme theme;
    private final Logger logger;
    private final List<Chapter> chapters;
    /** Laid out in {@link #init}: every page with its chapter and its sections, each with its wrapped lines. */
    private final List<Page> pages = new ArrayList<>();
    private int page;
    private int left, top;
    private BookButton previous, next;
    private ItemStack hovered = ItemStack.EMPTY;
    private int mouseX, mouseY;

    private record Placed(Section section, List<FormattedCharSequence> lines, int height) {
    }

    private record Page(int chapter, List<Placed> sections) {
    }

    /** Opens where the book was last closed. */
    public GuideBookScreen(Book book) {
        this(book, LAST_PAGE.getOrDefault(book.modId(), 0));
    }

    public GuideBookScreen(Book book, int page) {
        super(Component.translatable(book.titleKey()));
        this.book = book;
        this.theme = book.theme();
        this.logger = LoggerFactory.getLogger(book.modId());
        this.chapters = book.chapters();
        this.page = Math.max(0, page);
    }

    public Book book() {
        return book;
    }

    /** Known once the screen is open: the pages depend on the font and the language. */
    public int pageCount() {
        return pages.size();
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(2, (height - PANEL_H) / 2);
        layOut();
        page = Math.min(page, pages.size() - 1);
        int onLeft = 0, onRight = 0;
        for (int i = 0; i < chapters.size(); i++) {
            boolean right = chapters.get(i).side() == Chapter.Side.RIGHT;
            int row = right ? onRight++ : onLeft++;
            addRenderableWidget(new TabButton(right ? left + PANEL_W - 3 : left - TAB_W + 3,
                    top + 8 + row * (TAB_H + TAB_GAP), i, right));
        }
        int buttonY = top + PANEL_H - FOOTER_H + 2;
        previous = addRenderableWidget(new BookButton(left + PAGE_MARGIN, buttonY, BUTTON_W, BUTTON_H,
                BookStyle.styled(Component.literal("<")), button -> turnTo(page - 1)));
        next = addRenderableWidget(new BookButton(left + PANEL_W - PAGE_MARGIN - BUTTON_W, buttonY, BUTTON_W, BUTTON_H,
                BookStyle.styled(Component.literal(">")), button -> turnTo(page + 1)));
        updateButtons();
    }

    /**
     * Breaks every chapter into pages: sections go onto a page until the next one would not fit. A heading never ends
     * a page: it moves over together with what follows it.
     */
    private void layOut() {
        pages.clear();
        StringBuilder count = new StringBuilder();
        String prefix = book.key("");
        for (int c = 0; c < chapters.size(); c++) {
            int first = pages.size();
            List<Placed> current = new ArrayList<>();
            int used = 0;
            String title = chapters.get(c).title();
            List<Section> sections = chapters.get(c).sections();
            for (int i = 0; i < sections.size(); i++) {
                Placed placed = place(sections.get(i));
                int needed = placed.height();
                for (int j = i; j + 1 < sections.size() && sections.get(j) instanceof Section.Heading; j++) {
                    needed += place(sections.get(j + 1)).height();
                }
                boolean turn = sections.get(i) instanceof Section.Break || used + needed > CONTENT_H;
                if (turn && !current.isEmpty()) {
                    pages.add(new Page(c, current));
                    current = new ArrayList<>();
                    used = 0;
                }
                if (placed.height() > CONTENT_H) {
                    logger.warn("BOOK OVERFLOW {} section {}: {} of {} pixels", title, i, placed.height(), CONTENT_H);
                }
                current.add(placed);
                used += placed.height();
            }
            pages.add(new Page(c, current));
            count.append(' ').append(title.startsWith(prefix) ? title.substring(prefix.length()) : title).append('=')
                    .append(pages.size() - first);
        }
        String language = minecraft.getLanguageManager().getSelected();
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && LOGGED.add(book.modId() + ' ' + language)) {
            logger.info("BOOK PAGES {}: {} pages,{}", language, pages.size(), count);
            for (String key : book.keys()) {
                if (!Language.getInstance().has(key)) {
                    logger.error("BOOK KEY MISSING {}: {}", language, key);
                }
            }
        }
    }

    private Placed place(Section section) {
        return switch (section) {
            case Section.Heading heading -> text(section, BookStyle.translated(heading.key()), HEADING_LINE);
            case Section.Text text -> text(section, BookStyle.translated(text.key(), text.args()), TEXT_LINE);
            case Section.Quote quote -> text(section, BookStyle.translated(quote.key(), quote.args())
                    .withStyle(s -> s.withItalic(true)), TEXT_LINE);
            case Section.Note note -> text(section, BookStyle.translated(note.key(), note.args()), TEXT_LINE);
            case Section.ItemLine line -> {
                List<FormattedCharSequence> lines = font.split(BookStyle.translated(line.key(), line.args()),
                        TEXT_W - line.icons().size() * SLOT_SIZE - 2);
                yield new Placed(section, lines, Math.max(17, lines.size() * TEXT_LINE + 1) + 4);
            }
            case Section.ItemGrid items -> {
                MutableComponent text = BookStyle.styled(Component.empty());
                for (String key : items.textKeys()) {
                    text.append(text.getSiblings().isEmpty() ? "" : " ").append(Component.translatable(key));
                }
                int grid = items.side() * SLOT_SIZE;
                List<FormattedCharSequence> lines = font.split(text, TEXT_W - grid - 6);
                yield new Placed(section, lines, Math.max(grid, lines.size() * TEXT_LINE) + 4);
            }
            case Section.Recipe recipe -> new Placed(section, List.of(), 3 * SLOT_SIZE + 4);
            case Section.Pictures pictures -> new Placed(section, List.of(), pictures.size() + 4);
            case Section.Break turn -> new Placed(section, List.of(), 0);
        };
    }

    private Placed text(Section section, Component text, int lineHeight) {
        List<FormattedCharSequence> lines = font.split(text, TEXT_W);
        return new Placed(section, lines, lines.size() * lineHeight + GAP);
    }

    private int firstPageOf(int chapter) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).chapter() == chapter) {
                return i;
            }
        }
        return 0;
    }

    /** Also used by dev screenshots to go through the pages. */
    public void turnTo(int target) {
        int clamped = Math.clamp(target, 0, pages.size() - 1);
        if (clamped != page) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
        page = clamped;
        LAST_PAGE.put(book.modId(), page);
        updateButtons();
    }

    private void updateButtons() {
        previous.active = page > 0;
        next.active = page < pages.size() - 1;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> turnTo(page - 1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> turnTo(page + 1);
            default -> {
                return super.keyPressed(key, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) {
            turnTo(page + (scrollY > 0 ? -1 : 1));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        if (theme.texture() != null) {
            graphics.blit(theme.texture(), left, top, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
            return;
        }
        panel(graphics, left, top, PANEL_W, PANEL_H);
        int x0 = left + PAGE_MARGIN, y0 = top + PAGE_MARGIN;
        int x1 = left + PANEL_W - PAGE_MARGIN, y1 = top + PANEL_H - FOOTER_H;
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, theme.pageEdge());
        graphics.fill(x0, y0, x1, y1, theme.page());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); // the background and the buttons; the page goes on top
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hovered = ItemStack.EMPTY;
        Page current = pages.get(page);
        Chapter chapter = chapters.get(current.chapter());
        int x = left + PAGE_MARGIN + TEXT_MARGIN;
        int y = top + PAGE_MARGIN + 3;
        graphics.renderItem(chapter.icon(), x, y);
        graphics.drawString(font, BookStyle.translated(chapter.title()), x + 20, y + 4, theme.title(), false);
        graphics.fill(x, y + HEADER_H - 4, x + TEXT_W, y + HEADER_H - 3, theme.rule());

        int cursorY = top + PAGE_MARGIN + HEADER_H + 2;
        for (Placed placed : current.sections()) {
            List<FormattedCharSequence> lines = placed.lines();
            switch (placed.section()) {
                case Section.Heading heading -> lines(graphics, lines, x, cursorY + 3, HEADING_LINE, theme.heading());
                case Section.Text text -> lines(graphics, lines, x, cursorY, TEXT_LINE, theme.body());
                case Section.Quote quote -> lines(graphics, lines, x, cursorY, TEXT_LINE, theme.soft());
                case Section.Note note -> lines(graphics, lines, x, cursorY, TEXT_LINE, theme.title());
                case Section.ItemLine line -> {
                    for (int i = 0; i < line.icons().size(); i++) {
                        item(graphics, line.icons().get(i), x + i * SLOT_SIZE, cursorY);
                    }
                    lines(graphics, lines, x + line.icons().size() * SLOT_SIZE + 2, cursorY + (lines.size() == 1 ? 4 : 0),
                            TEXT_LINE, theme.body());
                }
                case Section.ItemGrid items -> {
                    int side = items.side();
                    for (int i = 0; i < side * side; i++) {
                        slot(graphics, items.stack(), x + i % side * SLOT_SIZE, cursorY + i / side * SLOT_SIZE);
                    }
                    lines(graphics, lines, x + side * SLOT_SIZE + 6,
                            cursorY + Math.max(0, (side * SLOT_SIZE - lines.size() * TEXT_LINE) / 2), TEXT_LINE,
                            theme.body());
                }
                case Section.Recipe recipe -> recipe(graphics, recipe, x + (TEXT_W - RECIPE_W) / 2, cursorY);
                case Section.Pictures pictures -> {
                    int size = pictures.size(), texture = pictures.textureSize();
                    int px = x + (TEXT_W - pictures.textures().size() * (size + 8) + 8) / 2;
                    for (int i = 0; i < pictures.textures().size(); i++, px += size + 8) {
                        if (pictures.framed()) {
                            graphics.fill(px, cursorY, px + size, cursorY + size, theme.pageEdge());
                            graphics.fill(px + 1, cursorY + 1, px + size - 1, cursorY + size - 1, theme.page());
                        }
                        graphics.blit(pictures.textures().get(i), px, cursorY, size, size, 0, 0, texture, texture, texture,
                                texture);
                    }
                }
                case Section.Break turn -> {
                }
            }
            cursorY += placed.height();
        }
        graphics.drawCenteredString(font, BookStyle.styled(Component.literal((page + 1) + " / " + pages.size())),
                left + PANEL_W / 2, top + PANEL_H - FOOTER_H + 7, theme.pageNumber());
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- pieces

    private void lines(GuiGraphics graphics, List<FormattedCharSequence> lines, int x, int y, int lineHeight,
                       int colour) {
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, colour, false);
            y += lineHeight;
        }
    }

    /** A crafting grid, always three by three, an arrow and the result. */
    private void recipe(GuiGraphics graphics, Section.Recipe recipe, int x, int y) {
        for (int i = 0; i < 9; i++) {
            slot(graphics, recipe.grid().get(i), x + i % 3 * SLOT_SIZE, y + i / 3 * SLOT_SIZE);
        }
        int arrow = x + 3 * SLOT_SIZE + 6, mid = y + 3 * SLOT_SIZE / 2;
        graphics.fill(arrow, mid - 1, arrow + 9, mid + 1, theme.arrow());
        for (int i = 0; i < 4; i++) {
            graphics.fill(arrow + 9 + i, mid - 4 + i, arrow + 10 + i, mid + 4 - i, theme.arrow());
        }
        slot(graphics, recipe.result(), arrow + 13 + 6, mid - SLOT_SIZE / 2);
    }

    /** The one slot of the book, for recipes and item squares alike, so every grid looks the same. */
    private void slot(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, theme.slotEdge());
        graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, theme.slot());
        item(graphics, stack, x + 1, y + 1);
    }

    private void item(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.renderItem(stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            hovered = stack;
        }
    }

    /** Vanilla container panel: transparent corner pixels, 1 px border, 2 px highlight, 2 px shadow. */
    private void panel(GuiGraphics graphics, int x0, int y0, int w, int h) {
        int x1 = x0 + w, y1 = y0 + h;
        int border = theme.border(), light = theme.light(), shadow = theme.shadow();
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, theme.panel());
        graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, border);
        graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, border);
        graphics.fill(x0, y0 + 2, x0 + 1, y1 - 2, border);
        graphics.fill(x1 - 1, y0 + 2, x1, y1 - 2, border);
        graphics.fill(x0 + 1, y0 + 1, x0 + 2, y0 + 2, border);
        graphics.fill(x1 - 2, y0 + 1, x1 - 1, y0 + 2, border);
        graphics.fill(x0 + 1, y1 - 2, x0 + 2, y1 - 1, border);
        graphics.fill(x1 - 2, y1 - 2, x1 - 1, y1 - 1, border);
        graphics.fill(x0 + 2, y0 + 1, x1 - 3, y0 + 2, light);
        graphics.fill(x0 + 1, y0 + 2, x1 - 3, y0 + 3, light);
        graphics.fill(x0 + 1, y0 + 3, x0 + 3, y1 - 3, light);
        graphics.fill(x0 + 3, y0 + 3, x0 + 4, y0 + 4, light);
        graphics.fill(x0 + 3, y1 - 2, x1 - 2, y1 - 1, shadow);
        graphics.fill(x0 + 3, y1 - 3, x1 - 1, y1 - 2, shadow);
        graphics.fill(x1 - 3, y0 + 3, x1 - 1, y1 - 3, shadow);
        graphics.fill(x1 - 4, y1 - 4, x1 - 3, y1 - 3, shadow);
    }

    /** Vanilla button behaviour (click, focus, narration, tooltip) in the theme's colours. */
    private class BookButton extends Button {
        BookButton(int x, int y, int w, int h, Component label, OnPress onPress) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x0 = getX(), y0 = getY(), x1 = x0 + getWidth(), y1 = y0 + getHeight();
            graphics.fill(x0, y0, x1, y1, theme.buttonEdge());
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, !active ? theme.buttonOff()
                    : isHoveredOrFocused() ? theme.buttonHover() : theme.button());
            if (active) {
                graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, theme.buttonLight());
            }
            graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, theme.buttonShadow());
            graphics.drawCenteredString(font, getMessage(), (x0 + x1) / 2, y0 + (getHeight() - 8) / 2,
                    active ? theme.buttonText() : theme.buttonTextOff());
        }
    }

    /**
     * A chapter tab: the chapter's icon on a tab, in the panel's colour and open towards it for the open chapter; the
     * title as tooltip.
     */
    private class TabButton extends BookButton {
        private final int chapter;
        private final boolean right;

        TabButton(int x, int y, int chapter, boolean right) {
            super(x, y, TAB_W, TAB_H, Component.translatable(chapters.get(chapter).title()),
                    button -> turnTo(firstPageOf(chapter)));
            this.chapter = chapter;
            this.right = right;
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean open = pages.get(page).chapter() == chapter;
            int x0 = getX(), y0 = getY(), x1 = x0 + getWidth(), y1 = y0 + getHeight();
            graphics.fill(x0, y0, x1, y1, theme.border());
            graphics.fill(x0 + (open && right ? 0 : 1), y0 + 1, x1 - (open && !right ? 0 : 1), y1 - 1,
                    open ? theme.panel() : isHoveredOrFocused() ? theme.tabHover() : theme.tab());
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, theme.light());
            graphics.renderItem(chapters.get(chapter).icon(), x0 + 5, y0 + 4);
        }
    }
}
