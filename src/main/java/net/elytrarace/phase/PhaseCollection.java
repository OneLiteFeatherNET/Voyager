package net.elytrarace.phase;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author Patrick Zdarsky / Rxcki
 * @version 1.0
 * @since 03/01/2020 21:04
 *
 * General Phase which is constructed of other Phases
 * This Class has no Phase logic built-in
 */
public abstract class PhaseCollection<T extends Phase> extends Phase implements Iterable<T>, List<T> {

    protected final List<T> phases;

    public PhaseCollection() {
        phases = new LinkedList<>();
    }

    public PhaseCollection(String name) {
        super(name);
        phases = new LinkedList<>();
    }

    public PhaseCollection(List<T> phases) {
        this.phases = phases;
    }

    public PhaseCollection(String name, List<T> phases) {
        super(name);
        this.phases = phases;
    }

    @Override
    public int size() {
        return phases.size();
    }

    @Override
    public boolean isEmpty() {
        return phases.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return phases.contains(o);
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return phases.toArray();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        return phases.toArray(a);
    }

    @Override
    public boolean add(T phase) {
        return phases.add(phase);
    }

    @Override
    public boolean remove(Object o) {
        return phases.remove(o);
    }

    @Override
    public T remove(int index) {
        return phases.remove(index);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        return phases.containsAll(c);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> c) {
        return phases.addAll(c);
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends T> c) {
        return phases.addAll(c);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        return phases.removeAll(c);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return phases.retainAll(c);
    }

    @Override
    public void clear() {
        phases.clear();
    }

    @Override
    public T get(int index) {
        return phases.get(index);
    }

    @Override
    public T set(int index, T element) {
        return phases.set(index, element);
    }

    @Override
    public void add(int index, T element) {
        phases.add(index, element);
    }

    @Override
    public int indexOf(Object o) {
        return phases.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return phases.lastIndexOf(o);
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator() {
        return phases.listIterator();
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator(int index) {
        return phases.listIterator(index);
    }

    @NotNull
    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return phases.subList(fromIndex, toIndex);
    }

    /**
     * Retrieve an Iterator.
     * @return - Iterator containing this StateHolder's states.
     */

    @Override
    @NotNull
    public Iterator<T> iterator() {
        return phases.iterator();
    }
}