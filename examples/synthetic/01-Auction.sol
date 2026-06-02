// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// Layout scris "natural" (semantic): seller info, item info, bid info, status.
// Suboptim: address (20B) urmat de uint256, apoi alt address, apoi alți biți mici.

contract Auction {
    address seller;             // 20B  → slot 0 (12 wasted)
    uint256 itemId;             // 32B  → slot 1
    address highestBidder;      // 20B  → slot 2 (12 wasted)
    uint256 highestBid;         // 32B  → slot 3
    uint64  startTime;          // 8B   → slot 4
    uint64  endTime;            // 8B   → slot 4
    bool    settled;            // 1B   → slot 4
    bool    cancelled;          // 1B   → slot 4 (10 wasted)
    uint96  minIncrement;       // 12B  → slot 5 (20 wasted)
    address feeRecipient;       // 20B  → slot 5
    uint16  feeBps;             // 2B   → slot 6 (30 wasted)
}
