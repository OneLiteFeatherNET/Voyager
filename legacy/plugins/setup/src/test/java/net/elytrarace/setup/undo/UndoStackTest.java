package net.elytrarace.setup.undo;

import net.elytrarace.common.map.model.FilePortalDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UndoStackTest {

    private UndoStack stack;

    @BeforeEach
    void setUp() {
        stack = new UndoStack(3); // small cap for testing
    }

    private UndoOperation.PlaceOperation place(int index) {
        return new UndoOperation.PlaceOperation(
                UUID.randomUUID(),
                new FilePortalDTO(index, List.of())
        );
    }

    @Test
    @DisplayName("Pop on empty stack returns empty")
    void shouldReturnEmptyOnEmptyStack() {
        assertThat(stack.pop()).isEmpty();
    }

    @Test
    @DisplayName("Push and pop returns the same operation (LIFO)")
    void shouldPushAndPopLifo() {
        var op1 = place(1);
        var op2 = place(2);

        stack.push(op1);
        stack.push(op2);

        assertThat(stack.pop()).contains(op2);
        assertThat(stack.pop()).contains(op1);
        assertThat(stack.pop()).isEmpty();
    }

    @Test
    @DisplayName("Exceeding max capacity evicts the oldest entry")
    void shouldEvictOldestWhenCapacityExceeded() {
        var op1 = place(1);
        var op2 = place(2);
        var op3 = place(3);
        var op4 = place(4);

        stack.push(op1);
        stack.push(op2);
        stack.push(op3);
        stack.push(op4); // should evict op1

        assertThat(stack.size()).isEqualTo(3);
        assertThat(stack.pop()).contains(op4);
        assertThat(stack.pop()).contains(op3);
        assertThat(stack.pop()).contains(op2);
        assertThat(stack.pop()).isEmpty(); // op1 was evicted
    }

    @Test
    @DisplayName("Size tracks correctly")
    void shouldTrackSize() {
        assertThat(stack.size()).isZero();
        assertThat(stack.isEmpty()).isTrue();

        stack.push(place(1));
        assertThat(stack.size()).isEqualTo(1);
        assertThat(stack.isEmpty()).isFalse();

        stack.push(place(2));
        assertThat(stack.size()).isEqualTo(2);

        stack.pop();
        assertThat(stack.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("Clear empties the stack")
    void shouldClearStack() {
        stack.push(place(1));
        stack.push(place(2));
        assertThat(stack.size()).isEqualTo(2);

        stack.clear();
        assertThat(stack.size()).isZero();
        assertThat(stack.isEmpty()).isTrue();
        assertThat(stack.pop()).isEmpty();
    }

    @Test
    @DisplayName("Supports both PlaceOperation and DeleteOperation")
    void shouldSupportBothOperationTypes() {
        var placeOp = place(1);
        var deleteOp = new UndoOperation.DeleteOperation(
                UUID.randomUUID(),
                new FilePortalDTO(2, List.of())
        );

        stack.push(placeOp);
        stack.push(deleteOp);

        var popped = stack.pop();
        assertThat(popped).contains(deleteOp);
        assertThat(popped.get()).isInstanceOf(UndoOperation.DeleteOperation.class);

        popped = stack.pop();
        assertThat(popped).contains(placeOp);
        assertThat(popped.get()).isInstanceOf(UndoOperation.PlaceOperation.class);
    }
}
