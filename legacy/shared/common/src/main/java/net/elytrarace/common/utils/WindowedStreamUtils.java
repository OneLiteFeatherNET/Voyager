package net.elytrarace.common.utils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Utility class for working with streams to chunk data.
 */
public final class WindowedStreamUtils {

    private WindowedStreamUtils() {
        throw new UnsupportedOperationException("Cannot instantiate utility class.");
    }

    /**
     * Returns a list of lists of elements of the given size, starting at the given step.
     *
     * @param list the list to chunk
     * @param size the size of each chunk
     * @param step the step between each chunk
     * @param <T> the type of elements in the list
     * @return a list of lists of elements
     */
    public static <T> List<List<T>> windowed(List<T> list, int size, int step) {
        if (size <= 0 || step <= 0) {
            throw new IllegalArgumentException("Size and step must be greater than 0");
        }
        return IntStream.range(0, (list.size() - size) / step + 1)
                .mapToObj(i -> list.subList(i * step, Math.min(i * step + size, list.size())))
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of lists of elements of the given size, starting at the first element.
     *
     * @param list the list to chunk
     * @param size the size of each chunk
     * @param <T> the type of elements in the list
     * @return a list of lists of elements
     */
    public static <T> List<List<T>> windowed(List<T> list, int size) {
        return windowed(list, size, 1);
    }
}
