package net.elytrarace.voyager.platform.tutorial;

import net.elytrarace.voyager.api.race.Author;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.lobby.LobbyBoard;
import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.dialog.DialogAction;
import net.minestom.server.dialog.DialogAfterAction;
import net.minestom.server.entity.Player;
import net.onelitefeather.pica.dialog.DialogTemplate;
import net.onelitefeather.pica.dialog.action.ActionButton;
import net.onelitefeather.pica.dialog.type.DialogType;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The tutorial book: five dialog pages a waiting racer opens with a right click on the book, in the racer's own language.
 *
 * <p>Each page has a Close button in its footer, a Back button on every page but the first and a Next button on every page
 * but the last. A button sends back the key of the page it leads to, so turning a page needs nothing but that key. The
 * pages are built once and shared by every racer. The book opens only when {@link #open} is called; nothing calls it at join
 * or at the start, and nothing about a racer's reading is kept.
 *
 * <p>This is the only class that builds a Pica dialog (ADR-0039, enforced by {@code PicaBoundaryTest}).
 */
@ApiStatus.Internal
@NotNullByDefault
public final class TutorialBook {

    private final List<DialogTemplate> pages;
    private final Supplier<LoadedCatalog> upcoming;

    /**
     * Builds the five fixed pages.
     *
     * @param upcoming the catalogue of the cup the next round plays; the sixth page is built from it each time it opens
     */
    public TutorialBook(Supplier<LoadedCatalog> upcoming) {
        this.upcoming = upcoming;
        List<DialogTemplate> built = new ArrayList<>(TutorialPages.COUNT - 1);
        for (int page = 1; page < TutorialPages.COUNT; page++) {
            built.add(build(page));
        }
        this.pages = List.copyOf(built);
    }

    /**
     * The sixth page, "Next cup", for the cup the next round plays: its name, one line per map with its authors, and the
     * cup's authors. It is built when it opens, not at boot, because the armed cup changes between rounds.
     */
    private DialogTemplate cupPage() {
        LoadedCatalog current = upcoming.get();
        LobbyBoard board = LobbyBoard.of(current.cup(), current.snapshot());
        List<Component> lines = new ArrayList<>();
        for (int index = 0; index < board.maps().size(); index++) {
            LobbyBoard.BoardMap map = board.maps().get(index);
            lines.add(Messages.lobbyBoardMap(index + 1, map.name(), map.authors().isEmpty() ? null : map.authors()));
        }
        if (!current.cup().authors().isEmpty()) {
            lines.add(Messages.tutorialCupAuthors(current.cup().authors().stream().map(Author::name)
                    .collect(Collectors.joining(", "))));
        }
        return build(TutorialPages.COUNT, Messages.tutorialCupTitle(board.title()), Messages.tutorialCupBody(lines));
    }

    /**
     * Opens the first page for {@code player}. Called only by the right click on the book.
     *
     * @param player the racer who asked for the tutorial
     */
    public void open(Player player) {
        pages.get(TutorialPages.FIRST_PAGE - 1).open(player);
    }

    /**
     * Answers a button of the tutorial: Close shuts the dialog for the racer who clicked, and a page key opens that page.
     *
     * @param player the racer who clicked
     * @param key    the key the button sent back
     * @return whether the key was one of the tutorial's, so the caller knows the click was answered here
     */
    public boolean click(Player player, Key key) {
        if (TutorialPages.CLOSE_KEY.equals(key)) {
            player.closeDialog();
            return true;
        }
        OptionalInt page = TutorialPages.indexOf(key);
        if (page.isEmpty()) {
            return false;
        }
        if (page.getAsInt() == TutorialPages.COUNT) {
            cupPage().open(player);
            return true;
        }
        pages.get(page.getAsInt() - 1).open(player);
        return true;
    }

    /**
     * The built page, counted from one. Package-private, for {@code TutorialBookTest}.
     */
    DialogTemplate page(int page) {
        return pages.get(page - 1);
    }

    private static DialogTemplate build(int page) {
        return build(page, Messages.tutorialPageTitle(page), Messages.tutorialPageBody(page));
    }

    private static DialogTemplate build(int page, Component title, Component body) {
        var dialog = DialogType.multiAction(TutorialPages.keyOf(page))
                .meta(meta -> meta
                        .title(title)
                        .closeWithEscape(true)
                        .pause(false)
                        .afterAction(DialogAfterAction.NONE)
                        .messageBody(messageBody -> messageBody.contents(body)))
                .columns(2);
        if (TutorialPages.hasBack(page)) {
            dialog.action(button(Messages.tutorialBack(), TutorialPages.keyOf(page - 1)));
        }
        if (TutorialPages.hasNext(page)) {
            dialog.action(button(Messages.tutorialNext(), TutorialPages.keyOf(page + 1)));
        }
        return dialog.exitAction(button(Messages.tutorialClose(), TutorialPages.CLOSE_KEY)).build();
    }

    private static Consumer<ActionButton> button(Component label, Key target) {
        return button -> button.label(label).action(new DialogAction.Custom(target, null));
    }
}
