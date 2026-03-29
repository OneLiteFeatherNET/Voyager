package net.elytrarace.api.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public class ConversationTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationTracker.class);
    private LinkedList<Conversation> conversationQueue = new LinkedList<>();

    public synchronized boolean beginConversation(Conversation conversation) {
        if (!this.conversationQueue.contains(conversation)) {
            this.conversationQueue.addLast(conversation);
            if (this.conversationQueue.getFirst() == conversation) {
                conversation.begin();
                conversation.outputNextPrompt();
                return true;
            }
        }
        return true;
    }

    public synchronized void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {
        if (!this.conversationQueue.isEmpty()) {
            if (this.conversationQueue.getFirst() == conversation) {
                conversation.abandon(details);
            }
            if (this.conversationQueue.contains(conversation)) {
                this.conversationQueue.remove(conversation);
            }
            if (!this.conversationQueue.isEmpty()) {
                this.conversationQueue.getFirst().outputNextPrompt();
            }
        }
    }

    public synchronized void abandonAllConversations() {
        LinkedList<Conversation> oldQueue = this.conversationQueue;
        this.conversationQueue = new LinkedList<>();
        for (Conversation conversation : oldQueue) {
            try {
                conversation.abandon(new ConversationAbandonedEvent(conversation, new ManuallyAbandonedConversationCanceller()));
            } catch (Throwable t) {
                LOGGER.error("Unexpected exception while abandoning a conversation", t);
            }
        }
    }

    public synchronized void acceptConversationInput(String input) {
        if (this.isConversing()) {
            Conversation conversation = this.conversationQueue.getFirst();
            try {
                conversation.acceptInput(input);
            } catch (Throwable t) {
                var owner = conversation.getContext().getOwner();
                String ownerName = owner != null ? owner.getName() : "unknown";
                LOGGER.warn("Owner {} generated an exception whilst handling conversation input", ownerName, t);
            }
        }
    }

    public synchronized boolean isConversing() {
        return !this.conversationQueue.isEmpty();
    }

    public synchronized boolean isConversingModaly() {
        return this.isConversing() && this.conversationQueue.getFirst().isModal();
    }
}
