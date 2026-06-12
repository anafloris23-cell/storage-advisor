// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// Game contract cu multe campuri mici împrăștiate.

contract Game {
    address gameMaster;         // 20B
    uint256 prizePool;          // 32B
    address winner;             // 20B
    uint8   round;              // 1B
    uint8   maxRounds;          // 1B
    uint8   playerCount;        // 1B
    uint8   maxPlayers;         // 1B
    uint256 entryFee;           // 32B
    uint64  roundDeadline;      // 8B
    bool    isActive;           // 1B
    bool    isPaused;           // 1B
    bool    rewardsDistributed; // 1B
    uint16  difficulty;         // 2B
    address feeRecipient;       // 20B
    uint8   houseEdgeBps;       // 1B
    uint64  startedAt;          // 8B
}
