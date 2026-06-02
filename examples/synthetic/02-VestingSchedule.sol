// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// Vesting "natural": beneficiar întâi, apoi sume, apoi timing, apoi flag-uri.
// Suboptim: variabilele mici sunt împrăștiate printre sloturile mari.

contract VestingSchedule {
    address beneficiary;        // 20B  → slot 0
    uint256 totalAmount;        // 32B  → slot 1
    uint256 released;           // 32B  → slot 2
    uint64  startTimestamp;     // 8B   → slot 3
    uint256 cliffDuration;      // 32B  → slot 4
    uint64  vestingDuration;    // 8B   → slot 5
    bool    revocable;          // 1B   → slot 5
    bool    revoked;            // 1B   → slot 5
    address revoker;            // 20B  → slot 6 (12 wasted)
    uint32  pausePeriods;       // 4B   → slot 7 (28 wasted)
}
