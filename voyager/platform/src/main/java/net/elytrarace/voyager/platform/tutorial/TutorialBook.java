package net.elytrarace.voyager.platform.tutorial;

import net.elytrarace.voyager.api.race.Author;
import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.LoadedCatalog;
import net.elytrarace.voyager.platform.lobby.LobbyBoard;
import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
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
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The tutorial book: the dialog pages a waiting racer opens with a right click on the book, in the racer's own language.
 *
 * <p>Each page has a Close button in its footer, a Back button on every page but the first and a Next button on every page
 * but the last. A button sends back the key of the page it leads to, so turning a page needs nothing but that key.
 *
 * <p>Every page is built for the racer who asks for it, in that racer's locale, and is not kept. Minestom translates the
 * components of a packet only when the packet is a {@code ComponentHolding}, and a dialog is not one, so a translation key
 * in a dialog would reach the client as the key itself. The text is therefore rendered here, before the dialog is built. The
 * book opens only when {@link #open} is called; nothing calls it at join or at the start, and nothing about a racer's reading
 * is kept.
 *
 * <p>This is the only class that builds a Pica dialog (ADR-0039, enforced by {@code PicaBoundaryTest}).
 */
@ApiStatus.Internal
@NotNullByDefault
public final class TutorialBook {

    private final CatalogHolder catalog;

    /**
     * Holds the catalogue the cup page is read from. No page is built here: each one is built when a racer asks for it.
     *
     * @param catalog the catalogue the cup the room waits for is read from; the sixth page is built from it each time it opens
     */
    public TutorialBook(CatalogHolder catalog) {
        this.catalog = catalog;
    }

    /**
     * Opens the first page for {@code player}. Called only by the right click on the book.
     *
     * @param player the racer who asked for the tutorial
     */
    public void open(Player player) {
        page(TutorialPages.FIRST_PAGE, player.getLocale()).open(player);
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
        page(page.getAsInt(), player.getLocale()).open(player);
        return true;
    }

    /** One page, built now for one locale. The sixth page is built from the cup the room waits for. */
    private DialogTemplate page(int page, Locale locale) {
        if (page == TutorialPages.COUNT) {
            return cupPage(locale);
        }
        return build(page, locale, Messages.tutorialPageTitle(page), Messages.tutorialPageBody(page));
    }

    /**
     * The sixth page, for the cup the room is waiting for: its name, one line per map with its authors, and the cup's authors.
     * It is built when it opens, not at boot, because the cup a room waits for can change with a reload.
     */
    private DialogTemplate cupPage(Locale locale) {
        LoadedCatalog current = catalog.current();
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
        return build(TutorialPages.COUNT, locale, Messages.tutorialCupTitle(board.title()),
                Messages.tutorialCupBody(lines));
    }

    private static DialogTemplate build(int page, Locale locale, Component title, Component body) {
        var dialog = DialogType.multiAction(TutorialPages.keyOf(page))
                .meta(meta -> meta
                        .title(GlobalTranslator.render(title, locale))
                        .closeWithEscape(true)
                        .pause(false)
                        .afterAction(DialogAfterAction.NONE)
                        .messageBody(messageBody -> messageBody.contents(GlobalTranslator.render(body, locale))))
                .columns(2);
        if (TutorialPages.hasBack(page)) {
            dialog.action(button(Messages.tutorialBack(), locale, TutorialPages.keyOf(page - 1)));
        }
        if (TutorialPages.hasNext(page)) {
            dialog.action(button(Messages.tutorialNext(), locale, TutorialPages.keyOf(page + 1)));
        }
        return dialog.exitAction(button(Messages.tutorialClose(), locale, TutorialPages.CLOSE_KEY)).build();
    }

    private static Consumer<ActionButton> button(Component label, Locale locale, Key target) {
        return button -> button.label(GlobalTranslator.render(label, locale))
                .action(new DialogAction.Custom(target, null));
    }
}
