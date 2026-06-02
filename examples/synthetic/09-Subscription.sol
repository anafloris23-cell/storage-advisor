// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract Subscription {
    address provider;           // 20B
    address paymentToken;       // 20B
    uint256 priceMonthly;       // 32B
    uint32  trialDays;          // 4B
    uint64  graceSeconds;       // 8B
    uint256 priceYearly;        // 32B
    address subscriber;         // 20B
    bool    autoRenew;          // 1B
    bool    isTrial;            // 1B
    uint64  nextBillingAt;      // 8B
    uint16  discountBps;        // 2B
    uint8   tier;               // 1B
    address referrer;           // 20B
}
