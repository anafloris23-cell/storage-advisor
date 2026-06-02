package ro.uaic.storageadvisor.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Bin-packing euristic prin First-Fit Decreasing (FFD).
 *
 * Algoritmul:
 *   1. Sortează elementele descrescător după dimensiune (cu spargere stabilă a egalităților după index).
 *   2. Pentru fiecare element, îl pune în primul slot existent în care încape;
 *      dacă nu există, deschide un slot nou.
 *
 * Complexitate: O(n²) în cel mai rău caz (pentru fiecare element scanăm toate sloturile).
 * Garanție: First-Fit Decreasing folosește cel mult (11/9) · OPT + 6/9 sloturi.
 *           Nu e optim absolut, dar e rapid și predictibil — util ca fallback când
 *           {@link BitmaskBinPacker} nu poate rula (n prea mare).
 */
public final class FirstFitBinPacker {

    public static final int SLOT_SIZE = BitmaskBinPacker.SLOT_SIZE;

    public List<List<Integer>> pack(int[] sizes) {
        int n = sizes.length;
        if (n == 0) {
            return List.of();
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        // Descrescător după dimensiune; egalitățile se sparg după indexul original (stabilitate).
        java.util.Arrays.sort(order, Comparator.<Integer>comparingInt(i -> -sizes[i]).thenComparingInt(i -> i));

        List<List<Integer>> bins = new ArrayList<>();
        List<Integer> binSums = new ArrayList<>();

        for (int idx : order) {
            int size = sizes[idx];
            int placed = -1;
            for (int b = 0; b < bins.size(); b++) {
                if (binSums.get(b) + size <= SLOT_SIZE) {
                    placed = b;
                    break;
                }
            }
            if (placed == -1) {
                bins.add(new ArrayList<>());
                binSums.add(0);
                placed = bins.size() - 1;
            }
            bins.get(placed).add(idx);
            binSums.set(placed, binSums.get(placed) + size);
        }
        return bins;
    }

    public <T> List<List<T>> pack(List<T> items, ToIntFunction<T> sizeOf) {
        int[] sizes = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            sizes[i] = sizeOf.applyAsInt(items.get(i));
        }
        List<List<Integer>> indexGroups = pack(sizes);
        List<List<T>> result = new ArrayList<>(indexGroups.size());
        for (List<Integer> group : indexGroups) {
            List<T> mapped = new ArrayList<>(group.size());
            for (int idx : group) {
                mapped.add(items.get(idx));
            }
            result.add(mapped);
        }
        return result;
    }
}
