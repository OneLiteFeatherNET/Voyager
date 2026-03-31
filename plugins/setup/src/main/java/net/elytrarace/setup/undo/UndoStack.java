package net.elytrarace.setup.undo;

import java.util.ArrayDeque;
import java.util.Optional;

/**
 * Fixed-capacity bidirectional undo/redo stack.
 * A new push clears the redo stack.
 */
public final class UndoStack {

    private static final int DEFAULT_MAX_SIZE = 20;

    private final int maxSize;
    private final ArrayDeque<UndoOperation> undoStack;
    private final ArrayDeque<UndoOperation> redoStack;

    public UndoStack() {
        this(DEFAULT_MAX_SIZE);
    }

    public UndoStack(int maxSize) {
        this.maxSize = maxSize;
        this.undoStack = new ArrayDeque<>(maxSize);
        this.redoStack = new ArrayDeque<>(maxSize);
    }

    public void push(UndoOperation operation) {
        if (undoStack.size() >= maxSize) {
            undoStack.removeLast(); // evict oldest
        }
        undoStack.push(operation);
        redoStack.clear(); // new action invalidates redo history
    }

    public Optional<UndoOperation> pop() {
        if (undoStack.isEmpty()) {
            return Optional.empty();
        }
        var op = undoStack.pop();
        if (redoStack.size() >= maxSize) {
            redoStack.removeLast();
        }
        redoStack.push(op);
        return Optional.of(op);
    }

    public Optional<UndoOperation> redo() {
        if (redoStack.isEmpty()) {
            return Optional.empty();
        }
        var op = redoStack.pop();
        if (undoStack.size() >= maxSize) {
            undoStack.removeLast();
        }
        undoStack.push(op);
        return Optional.of(op);
    }

    public int size() {
        return undoStack.size();
    }

    public int undoSize() {
        return undoStack.size();
    }

    public int redoSize() {
        return redoStack.size();
    }

    public boolean isEmpty() {
        return undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
