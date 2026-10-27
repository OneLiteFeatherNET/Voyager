package net.elytrarace.voyager.platform.tutorial;

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

    /** Builds the five pages. */
    public TutorialBook() {
        List<DialogTemplate> built = new ArrayList<>(TutorialPages.COUNT);
        for (int page = 1; page <= TutorialPages.COUNT; page++) {
            built.add(build(page));
        }
        this.pages = List.copyOf(built);
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
        var dialog = DialogType.multiAction(TutorialPages.keyOf(page))
                .meta(meta -> meta
                        .title(Messages.tutorialPageTitle(page))
                        .closeWithEscape(true)
                        .pause(false)
                        .afterAction(DialogAfterAction.NONE)
                        .messageBody(body -> body.contents(Messages.tutorialPageBody(page))))
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
