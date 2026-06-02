// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// Lending pool simplificat. Câmpuri economice amestecate.

contract LendingPool {
    address admin;              // 20B
    address oracle;             // 20B
    address rateModel;          // 20B
    uint256 totalSupplied;      // 32B
    uint256 totalBorrowed;      // 32B
    uint128 reserveFactor;      // 16B
    uint128 liquidationBonus;   // 16B
    uint256 lastAccrual;        // 32B
    uint8   decimals;           // 1B
    bool    paused;             // 1B
    bool    borrowingEnabled;   // 1B
    bool    liquidationsEnabled; // 1B
    address collateralToken;    // 20B
    uint16  maxLTV;             // 2B
    uint64  cooldownSeconds;    // 8B
    address rewardsDistributor; // 20B
}
