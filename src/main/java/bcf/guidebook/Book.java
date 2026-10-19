package bcf.guidebook;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A mod's guide book: its colours and its chapters. The mod makes one in a static field, registers a
 * {@link GuideBookItem} under {@code guide_book} with it and writes the texts as translation keys
 * {@code <modid>.book.<name>}; the screen and the item's use come from here.
 */
public final class Book {
    private static final Map<String, Book> BOOKS = new LinkedHashMap<>();

    private final String modId;
    private final BookTheme theme;
    private final Supplier<List<Chapter>> chapters;
    private final AttachmentType<Boolean> gotBook;
    private Map<String, String> standIns = Map.of();

    private Book(String modId, BookTheme theme, Supplier<List<Chapter>> chapters) {
        this.modId = modId;
        this.theme = theme;
        this.chapters = chapters;
        this.gotBook = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(modId, "got_guide_book"),
                b -> b.persistent(Codec.BOOL).copyOnDeath());
    }

    /**
     * The chapters are asked for whenever the book opens: item stacks need the registries, and numbers in the texts
     * may come from a config.
     */
    public static Book of(String modId, BookTheme theme, Supplier<List<Chapter>> chapters) {
        Book book = new Book(modId, theme, chapters);
        BOOKS.put(modId, book);
        return book;
    }

    /** The book of that mod, or null. */
    public static Book of(String modId) {
        return BOOKS.get(modId);
    }

    /** What a recipe's tag ingredient is shown as, e.g. {@code #minecraft:planks} as {@code minecraft:oak_planks}. */
    public Book standIns(Map<String, String> standIns) {
        this.standIns = Map.copyOf(standIns);
        return this;
    }

    public String modId() {
        return modId;
    }

    public BookTheme theme() {
        return theme;
    }

    public List<Chapter> chapters() {
        return chapters.get();
    }

    /** The translation key {@code <modid>.book.<name>}. */
    public String key(String name) {
        return modId + ".book." + name;
    }

    /** The book's name is its item's. */
    public String titleKey() {
        return "item." + modId + ".guide_book";
    }

    /** Every translation key the book shows. */
    public List<String> keys() {
        Set<String> keys = new LinkedHashSet<>(List.of(titleKey()));
        for (Chapter chapter : chapters()) {
            keys.add(chapter.title());
            chapter.sections().forEach(s -> keys.addAll(s.keys()));
        }
        return List.copyOf(keys);
    }

    /** Which of the book's keys the mod's language files lack, as {@code language:key}; for a mod's self-test. */
    public List<String> missingKeys(String... languages) {
        List<String> keys = keys(), missing = new ArrayList<>();
        for (String language : languages) {
            JsonObject lang = json("assets/" + modId + "/lang/" + language + ".json");
            keys.stream().filter(k -> !lang.has(k)).forEach(k -> missing.add(language + ":" + k));
        }
        return missing;
    }

    /** One picture {@code textures/gui/book/<name>.png} of the mod. */
    public Section.Pictures picture(String name, int size, int textureSize, boolean framed) {
        return new Section.Pictures(List.of(Identifier.fromNamespaceAndPath(modId, "textures/gui/book/" + name + ".png")),
                size, textureSize, framed);
    }

    // ponytail: reads the mod's own recipe files; a tag shows its stand-in, datapack overrides are not shown
    /**
     * The recipe {@code data/<modid>/recipe/<name>.json} as a crafting grid: shapeless ingredients fill the fields in
     * order, a shaped pattern is centred. The client has no recipe manager since 1.21.2, hence the file.
     */
    public Section.Recipe recipe(String name) {
        JsonObject json = json("data/" + modId + "/recipe/" + name + ".json");
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        if (json.has("pattern")) {
            JsonArray pattern = json.getAsJsonArray("pattern");
            JsonObject keys = json.getAsJsonObject("key");
            int h = pattern.size(), w = pattern.get(0).getAsString().length();
            for (int y = 0; y < h; y++) {
                String row = pattern.get(y).getAsString();
                for (int x = 0; x < w; x++) {
                    String symbol = String.valueOf(row.charAt(x));
                    if (keys.has(symbol)) {
                        grid.set((y + (3 - h) / 2) * 3 + x + (3 - w) / 2, stack(keys.get(symbol)));
                    }
                }
            }
        } else {
            JsonArray ingredients = json.getAsJsonArray("ingredients");
            for (int i = 0; i < ingredients.size(); i++) {
                grid.set(i, stack(ingredients.get(i)));
            }
        }
        return new Section.Recipe(grid, stack(json.getAsJsonObject("result").get("id")));
    }

    private ItemStack stack(JsonElement id) {
        String name = id.getAsString();
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(standIns.getOrDefault(name, name))));
    }

    private JsonObject json(String path) {
        try {
            return JsonParser.parseString(Files.readString(FabricLoader.getInstance().getModContainer(modId)
                    .orElseThrow().findPath(path).orElseThrow(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- the welcome gift of 1.0

    /** Set by the welcome gift of 1.0; nothing sets it any more, it stays registered so saved players load cleanly. */
    public AttachmentType<Boolean> gotBook() {
        return gotBook;
    }

    /**
     * Does nothing: with every mod giving its book, a new world started with a pile of them. Players craft the book
     * or take it from the creative tab. Kept so mods built against 1.0 still run with this version.
     */
    @Deprecated
    public void giveOnce(ServerPlayer player) {
    }

    /** Does nothing, see {@link #giveOnce}. */
    @Deprecated
    public void giveOnJoin() {
    }
}
