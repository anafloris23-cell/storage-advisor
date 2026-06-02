// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract Insurance {
    address underwriter;        // 20B
    address insuranceFund;      // 20B
    uint256 totalCoverage;      // 32B
    uint16  coverageRatioBps;   // 2B
    uint16  premiumBps;         // 2B
    uint64  policyExpirySeconds; // 8B
    address claimsManager;      // 20B
    uint256 totalClaimsPaid;    // 32B
    bool    paused;             // 1B
    bool    autoApproveSmall;   // 1B
    uint128 maxClaimAmount;     // 16B
    uint128 minClaimAmount;     // 16B
    uint8   riskLevel;          // 1B
    address oracle;             // 20B
    uint32  cooldownDays;       // 4B
}
