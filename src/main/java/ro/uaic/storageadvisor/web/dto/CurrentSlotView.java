package ro.uaic.storageadvisor.web.dto;

import java.util.List;

/**
 * Un slot (sau grup de sloturi consecutive) din layout-ul curent produs de solc.
 * Folosit pentru diagrama "before", simetric cu {@code RecommendedSlot} pentru "after".
 *
 * @param slotIndex    indexul slotului de start
 * @param variables    variabilele care încep la acest slot, ordonate după offset
 * @param usedBytes    bytes ocupați efectiv (suma dimensiunilor variabilelor)
 * @param wastedBytes  bytes irosiți: slotsSpanned*32 − usedBytes
 * @param slotsSpanned câte sloturi fizice ocupă grupul (1 pentru tipuri ≤ 32B)
 */
public record CurrentSlotView(
        int slotIndex,
        List<CurrentVariableView> variables,
        int usedBytes,
        int wastedBytes,
        int slotsSpanned
) {}
