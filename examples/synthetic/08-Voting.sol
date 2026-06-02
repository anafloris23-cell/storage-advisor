// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract Voting {
    address chairperson;        // 20B
    uint256 proposalCount;      // 32B
    uint64  votingStart;        // 8B
    uint64  votingEnd;          // 8B
    bool    finalized;          // 1B
    address verifier;           // 20B
    uint128 quorumNumerator;    // 16B
    uint128 quorumDenominator;  // 16B
    uint256 totalVotes;         // 32B
    uint8   votingType;         // 1B
    bool    delegationEnabled;  // 1B
    uint16  proposalThreshold;  // 2B
    address tokenAddress;       // 20B
}
