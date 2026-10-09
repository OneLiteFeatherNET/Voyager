package net.elytrarace.voyager.server.game;

import net.elytrarace.voyager.platform.catalog.CatalogHolder;
import net.elytrarace.voyager.platform.catalog.ReloadOutcome;
import net.elytrarace.voyager.platform.text.Messages;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs one operator reload off the tick thread and reports how it came out.
 *
 * <p>An applied reload is offered to the {@link CatalogHolder}, and plays from the next round. A rejected one
 * changes nothing and reports every problem. A reload that throws is logged once with its cause and reported as
 * failed; it never reaches the tick, and the holder is left as it was.
 *
 * <p>The reload itself is a {@link Supplier} and the executor is a parameter, so the tests run the whole path
 * without a server or a thread. The composition root passes the real reloader and a virtual thread per request.
 */
public final class CatalogReloadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogReloadService.class);

    private final Supplier<ReloadOutcome> reload;
    private final CatalogHolder holder;
    private final Executor executor;

    /**
     * @param reload   runs the reading and the world checks; the composition root binds it to the data path
     * @param holder   receives an applied catalogue
     * @param executor runs each reload; the composition root passes virtual threads, the tests pass a direct one
     */
    public CatalogReloadService(Supplier<ReloadOutcome> reload, CatalogHolder holder, Executor executor) {
        this.reload = reload;
        this.holder = holder;
        this.executor = executor;
    }

    /**
     * Starts one reload and returns at once. {@code reply} receives the outcome, one message at a time, from the
     * executor's thread.
     *
     * @param reply where the operator's messages go
     */
    public void request(Consumer<Component> reply) {
        executor.execute(() -> run(reply));
    }

    private void run(Consumer<Component> reply) {
        try {
            switch (reload.get()) {
                case ReloadOutcome.Applied applied -> {
                    holder.offer(applied.loaded());
                    for (String warning : applied.warnings()) {
                        LOGGER.warn(warning);
                        reply.accept(Component.text(warning));
                    }
                    LOGGER.info("Catalogue reload accepted for cup '{}'; it plays from the next round",
                            applied.loaded().cup().name());
                    reply.accept(Messages.reloadPending());
                }
                case ReloadOutcome.Rejected rejected -> {
                    LOGGER.warn("Catalogue reload refused with {} problem(s); the running catalogue is unchanged",
                            rejected.problems().size());
                    reply.accept(Messages.reloadRejected(rejected.problems().size()));
                    for (String problem : rejected.problems()) {
                        reply.accept(Component.text(problem));
                    }
                }
            }
        } catch (RuntimeException failure) {
            LOGGER.error("Catalogue reload failed; the running catalogue is unchanged", failure);
            reply.accept(Messages.reloadFailed());
        }
    }
}
