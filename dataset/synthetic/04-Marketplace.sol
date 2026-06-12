// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// NFT marketplace. Layout intenționat suboptim.

contract Marketplace {
    address owner;              // 20B  → slot 0
    address treasury;           // 20B  → slot 1
    uint256 totalSales;         // 32B  → slot 2
    uint16  platformFeeBps;     // 2B   → slot 3
    uint16  royaltyBps;         // 2B   → slot 3
    address paymentToken;       // 20B  → slot 3
    uint256 minListingPrice;    // 32B  → slot 4
    uint256 maxListingPrice;    // 32B  → slot 5
    bool    paused;             // 1B   → slot 6
    bool    whitelistEnabled;   // 1B   → slot 6
    uint32  totalListings;      // 4B   → slot 6
    address verifier;           // 20B  → slot 6
    uint64  lastUpdateAt;       // 8B   → slot 7 (24 wasted)
}
