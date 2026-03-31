package net.elytrarace.api.conversation;

import org.jetbrains.annotations.NotNull;

/**
 * An InactivityConversationCanceller will cancel a {@link Conversation} after
 * a period of inactivity by the user.
 */
public class InactivityConversationCanceller implements ConversationCanceller {
    protected ConversationOwner owner;
    protected int timeoutSeconds;
    protected Conversation conversation;
    private int taskId = -1;

    /**
     * Creates an InactivityConversationCanceller.
     *
     * @param owner The owning conversation owner.
     * @param timeoutSeconds The number of seconds of inactivity to wait.
     */
    public InactivityConversationCanceller(@NotNull ConversationOwner owner, int timeoutSeconds) {
        this.owner = owner;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public void setConversation(@NotNull Conversation conversation) {
        this.conversation = conversation;
        startTimer();
    }

    @Override
    public boolean cancelBasedOnInput(@NotNull ConversationContext context, @NotNull String input) {
        stopTimer();
        startTimer();
        return false;
    }

    @Override
    @NotNull
    public ConversationCanceller clone() {
        return new InactivityConversationCanceller(owner, timeoutSeconds);
    }

    private void startTimer() {
        taskId = owner.getScheduler().scheduleDelayed(() -> {
            if (conversation.getState() == Conversation.ConversationState.UNSTARTED) {
                startTimer();
            } else if (conversation.getState() == Conversation.ConversationState.STARTED) {
                cancelling(conversation);
                conversation.abandon(new ConversationAbandonedEvent(conversation, InactivityConversationCanceller.this));
            }
        }, timeoutSeconds * 20L);
    }

    private void stopTimer() {
        if (taskId != -1) {
            owner.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    /**
     * Subclasses can override this to take additional actions when the
     * inactivity timer abandons the conversation.
     *
     * @param conversation The conversation being abandoned.
     */
    protected void cancelling(@NotNull Conversation conversation) {
    }
}
