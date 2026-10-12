package bcf.guidebook;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/** The guide book as an item: using it opens the book's screen. On a dedicated server it does nothing. */
public class GuideBookItem extends Item {
    /** Set by this library's client entry point; the server side never opens screens. */
    public static Consumer<Book> opener = book -> {
    };

    private final Book book;

    public GuideBookItem(Properties properties, Book book) {
        super(properties);
        this.book = book;
        book.item(this);
    }

    public Book book() {
        return book;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            opener.accept(book);
        }
        return InteractionResult.SUCCESS;
    }
}
