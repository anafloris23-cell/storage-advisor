// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract StakingPool {
    address stakingToken;       // 20B
    address rewardToken;        // 20B
    uint256 totalStaked;        // 32B
    uint64  startTime;          // 8B
    uint256 rewardRate;         // 32B
    uint64  endTime;            // 8B
    uint256 lastUpdateTime;     // 32B
    address rewardsAdmin;       // 20B
    uint128 periodFinish;       // 16B
    uint128 rewardPerTokenStored; // 16B
    bool    paused;             // 1B
    uint8   feePct;             // 1B
    uint16  earlyExitPenaltyBps; // 2B
}
