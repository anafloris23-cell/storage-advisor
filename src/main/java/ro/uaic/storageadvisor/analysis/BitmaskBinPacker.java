package ro.uaic.storageadvisor.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bin-packing optim prin programare dinamică pe submulțimi (bitmask DP).
 *
 * Problema: dată o listă de obiecte cu dimensiuni s_0, …, s_{n-1} (fiecare ≤ SLOT_SIZE),
 * găsește partiționarea în număr minim de sloturi de 32 bytes.
 *
 * Stare:  dp[mask] = numărul minim de sloturi pentru a împacheta exact obiectele
 *                    indicate de biții setați în mask.
 * Tranziție:  dp[mask] = min { dp[mask \ S] + 1 : S ⊆ mask, sum(S) ≤ SLOT_SIZE }
 * Caz de bază: dp[0] = 0.
 *
 * Complexitate: O(3^n) timp, O(2^n) spațiu. Garantează optimul absolut.
 *
 */
public final class BitmaskBinPacker {

    public static final int SLOT_SIZE = 32;
    public static final int MAX_ITEMS = 20;

    public List<List<Integer>> pack(int[] sizes) {
        int n = sizes.length;
        if (n == 0) {
            return List.of();
        }
        if (n > MAX_ITEMS) {
            throw new IllegalArgumentException(
                    "BitmaskBinPacker acceptă cel mult " + MAX_ITEMS
                            + " elemente (primit n=" + n + "). "
                            + "Pentru n mai mare folosește o euristică.");
        }
        for (int i = 0; i < n; i++) {
            if (sizes[i] <= 0 || sizes[i] > SLOT_SIZE) {
                throw new IllegalArgumentException(
                        "Dimensiune invalidă sizes[" + i + "]=" + sizes[i]
                                + " (trebuie în [1, " + SLOT_SIZE + "]).");
            }
        }

        int full = (1 << n) - 1;

        // Sume incrementale pentru fiecare submulțime: subsetSum[mask] = subsetSum[mask & (mask-1)] + sizes[lowBit].
        int[] subsetSum = new int[full + 1];
        for (int mask = 1; mask <= full; mask++) {
            int lowBit = Integer.numberOfTrailingZeros(mask);
            subsetSum[mask] = subsetSum[mask & (mask - 1)] + sizes[lowBit];
        }

        int[] dp = new int[full + 1];
        int[] choice = new int[full + 1]; // submulțimea aleasă drept "ultimul slot" pentru fiecare mask
        Arrays.fill(dp, Integer.MAX_VALUE);
        dp[0] = 0;

        for (int mask = 1; mask <= full; mask++) {
            // Iterează submulțimile nevide ale lui mask în ordine descrescătoare.
            for (int sub = mask; sub > 0; sub = (sub - 1) & mask) {
                if (subsetSum[sub] > SLOT_SIZE) {
                    continue;
                }
                int rest = mask ^ sub;
                if (dp[rest] == Integer.MAX_VALUE) {
                    continue;
                }
                int candidate = dp[rest] + 1;
                if (candidate < dp[mask]) {
                    dp[mask] = candidate;
                    choice[mask] = sub;
                }
            }
        }

        List<List<Integer>> result = new ArrayList<>();
        int cur = full;
        while (cur != 0) {
            int sub = choice[cur];
            List<Integer> group = new ArrayList<>();
            int tmp = sub;
            while (tmp != 0) {
                int bit = Integer.numberOfTrailingZeros(tmp);
                group.add(bit);
                tmp &= tmp - 1;
            }
            result.add(group);
            cur ^= sub;
        }
        return result;
    }

    public <T> List<List<T>> pack(List<T> items, java.util.function.ToIntFunction<T> sizeOf) {
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
