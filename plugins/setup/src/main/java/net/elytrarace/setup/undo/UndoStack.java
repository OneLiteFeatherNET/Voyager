package net.elytrarace.setup.undo;

import java.util.ArrayDeque;
import java.util.Optional;

/**
 * Fixed-capacity LIFO stack for undo operations.
 */
public final class UndoStack {

    private static final int DEFAULT_MAX_SIZE = 20;

    private final int maxSize;
    private final ArrayDeque<UndoOperation> stack;

    public UndoStack() {
        this(DEFAULT_MAX_SIZE);
    }

    public UndoStack(int maxSize) {
        this.maxSize = maxSize;
        this.stack = new ArrayDeque<>(maxSize);
    }

    public void push(UndoOperation operation) {
        if (stack.size() >= maxSize) {
            stack.removeLast(); // evict oldest
        }
        stack.push(operation);
    }

    public Optional<UndoOperation> pop() {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(stack.pop());
    }

    public int size() {
        return stack.size();
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public void clear() {
        stack.clear();
    }
}
