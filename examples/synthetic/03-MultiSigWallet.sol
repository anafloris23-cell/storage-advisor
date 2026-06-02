// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// Multi-sig clasic, layout aranjat după "ce face" fiecare câmp.
// Suboptim: uint256 nonce între address-uri.

contract MultiSigWallet {
    address admin;              // 20B  → slot 0
    uint256 nonce;              // 32B  → slot 1
    address proposer;           // 20B  → slot 2
    uint8   threshold;          // 1B   → slot 2 (11 wasted)
    uint8   ownerCount;         // 1B   → slot 2
    uint256 minDelay;           // 32B  → slot 3
    address guardian;           // 20B  → slot 4
    bool    frozen;             // 1B   → slot 4
    bool    pendingUpgrade;     // 1B   → slot 4 (10 wasted)
    uint16  version;            // 2B   → slot 5 (30 wasted)
    address feeToken;           // 20B  → slot 5
}
