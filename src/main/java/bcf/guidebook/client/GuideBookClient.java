package bcf.guidebook.client;

import bcf.guidebook.Book;
import bcf.guidebook.GuideBookItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Client side: the guide book items open the book screen. */
public final class GuideBookClient implements ClientModInitializer {
    private static int shot = -1, wait, mod;

    @Override
    public void onInitializeClient() {
        GuideBookItem.opener = book -> Minecraft.getInstance().gui.setScreen(new GuideBookScreen(book));
        String shots = System.getProperty("bcf.guidebook.shots");
        if (shots != null) {
            ClientTickEvents.END_CLIENT_TICK.register(minecraft -> bookShots(minecraft, shots.split(",")));
        }
    }

    /**
     * Dev only ({@code -Dbcf.guidebook.shots=<modid>[,<modid>...]}): once the player stands in a world, opens every
     * page of those mods' guide books in turn, saves screenshots/book_&lt;modid&gt;_&lt;language&gt;_&lt;nr&gt;.png
     * ten ticks later each and quits.
     */
    private static void bookShots(Minecraft minecraft, String[] modIds) {
        if (mod >= modIds.length) {
            return;
        }
        String modId = modIds[mod];
        Book book = Book.of(modId);
        if (shot < 0) {
            if (book == null || minecraft.player == null || minecraft.gui.screen() != null
                    || minecraft.gui.overlay() != null) {
                wait = 0;
            } else if (++wait >= 40) { // two seconds in the world, so it is drawn behind the book
                wait = 0;
                shot = 0;
                minecraft.gui.setScreen(new GuideBookScreen(book, 0));
            }
            return;
        }
        if (!(minecraft.gui.screen() instanceof GuideBookScreen screen)) {
            return;
        }
        minecraft.gui.toastManager().clear();
        if (++wait < 10) {
            return;
        }
        wait = 0;
        Screenshot.grab(minecraft.gameDirectory, String.format("book_%s_%s_%02d.png", modId,
                minecraft.getLanguageManager().getSelected(), shot + 1), minecraft.gameRenderer.mainRenderTarget(), 1,
                message -> { });
        if (++shot < screen.pageCount()) {
            screen.turnTo(shot);
        } else if (++mod < modIds.length) {
            shot = -1;
            minecraft.gui.setScreen(null);
        } else {
            minecraft.stop();
        }
    }
}
