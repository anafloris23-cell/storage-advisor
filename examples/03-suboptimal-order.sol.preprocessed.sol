// SPDX-License-Identifier: BUSL-1.1
pragma solidity ^0.8.27;

// [storageadvisor] removed: import "@openzeppelin/contracts-upgradeable/utils/PausableUpgradeable.sol";
// [storageadvisor] removed: import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
// [storageadvisor] removed: import "@openzeppelin/contracts/utils/math/SafeCast.sol";
// [storageadvisor] removed: import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
// [storageadvisor] removed: import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
// [storageadvisor] removed: import "../tokens/USDM.sol";
// [storageadvisor] removed: import "../tokens/sUSDM.sol";
// [storageadvisor] removed: import "./MonetrixConfig.sol";
// [storageadvisor] removed: import "./InsuranceFund.sol";
// [storageadvisor] removed: import "../interfaces/IHyperCore.sol";
// [storageadvisor] removed: import "../interfaces/HyperCoreConstants.sol";
// [storageadvisor] removed: import "../interfaces/IMonetrixAccountant.sol";
// [storageadvisor] removed: import "../interfaces/IRedeemEscrow.sol";
// [storageadvisor] removed: import "../interfaces/IYieldEscrow.sol";
// [storageadvisor] removed: import "./ActionEncoder.sol";
// [storageadvisor] removed: import "./PrecompileReader.sol";
// [storageadvisor] removed: import "./TokenMath.sol";
// [storageadvisor] removed: import "./MonetrixAccountant.sol";
// [storageadvisor] removed: import {MonetrixGovernedUpgradeable} from "../governance/MonetrixGovernedUpgradeable.sol";

/// @title MonetrixVault - Core vault managing USDC deposits, USDM minting, redemption queue, and L1 hedge execution
/// @dev Role mapping (via shared MonetrixAccessController):
///      - GUARDIAN: pause / unpause / pauseOperator / unpauseOperator (delay=0)
///      - OPERATOR: bridge / hedge / HLP / yield-distribution (delay=0)
///      - GOVERNOR: set* / emergency* (24h timelock)
///      - UPGRADER: _authorizeUpgrade (48h timelock, inherited from base)
/// @dev Two-dimensional pause:
///      - `paused` (OZ Pausable): user fund I/O — deposit, redeem claim, outflow paths.
///      - `operatorPaused` (custom):    all operator-driven mutations (hedge/HLP/BLP/bridges/yield).
///      Outflow functions (`keeperBridge`, `settle`, `distributeYield`) are gated by BOTH.
contract MonetrixVault /* [storageadvisor] removed: is PausableUpgradeable, ReentrancyGuard, MonetrixGovernedUpgradeable */ {
// [storageadvisor] removed: using SafeERC20 for IERC20;
    enum BridgeTarget { Vault, Multisig }

    // ═══════════════════════════════════════════════════════════
    //                      STATE
    // ═══════════════════════════════════════════════════════════

    // ─── Core references ────────────────────────────────────
    address public usdc;
    address public usdm;
    address public susdm;
    address public config;
    address public coreDepositWallet;
    address public accountant;
    address public multisigVault;
    address public redeemEscrow;
    address public yieldEscrow;

    // ─── Operational state ──────────────────────────────────
    bool public hlpDepositEnabled;
    bool public multisigVaultEnabled;
    uint256 public lastBridgeTimestamp;

    // ─── L1 principal tracking ───────────────────────────────
    uint256 public outstandingL1Principal;
    uint256 public bridgeRetentionAmount;

    // ─── Redeem queue ───────────────────────────────────────
    /// @dev 2-slot layout without exotic bit widths. `owner` (160 bits) +
    ///      `cooldownEnd` (64 bits) packs into slot 0 (224/256 used); amount
    ///      takes the full uint256 slot 1 — same storage cost as the former
    ///      uint152/uint104 packing, but no truncation risk on usdmAmount.
    struct RedeemRequest {
        address owner;        // slot 0 ┐
        uint64  cooldownEnd;  // slot 0 ┘
        uint256 usdmAmount;   // slot 1
    }

    uint256 public nextRedeemId;
    mapping(uint256 => RedeemRequest) public redeemRequests;
    mapping(address => uint256[]) private _userRedeemIds;

    /// @notice PM activation flag for Vault's L1 account; when true, `_sendL1Bridge` counts 0x811 supplied.
    bool public pmEnabled;

    /// @notice Operator-side pause (independent of `paused`). When true, blocks every operator-driven
    ///         mutation (hedge/HLP/BLP/bridges/yield/escrow routing). User-facing functions keep
    ///         their own `whenNotPaused` gate and are unaffected.
    bool public operatorPaused;

    uint256[50] private __gap;

    // ─── Events ─────────────────────────────────────────────
    event Deposited(address indexed user, uint256 amount);
    event RedeemRequested(uint256 indexed requestId, address indexed owner, uint256 usdmAmount, uint256 cooldownEnd);
    event RedeemClaimed(uint256 indexed requestId, address indexed owner, uint256 usdmAmount);
    event BridgedToL1(uint256 amount);
    event PrincipalBridgedFromL1(uint256 amount);
    event YieldBridgedFromL1(uint256 amount);
    event YieldCollected(uint256 amount);
    event YieldDistributed(uint256 totalYield, uint256 userShare, uint256 insuranceShare, uint256 foundationShare);
    event HedgeExecuted(uint256 indexed batchId, uint32 spotAsset, uint32 perpAsset, uint64 size);
    event HedgeClosed(uint256 indexed positionId, uint32 spotAsset, uint64 size);
    event HedgeRepaired(uint256 indexed positionId, uint16 residualBps);
    event HlpDeposited(uint64 usdAmount);
    event HlpWithdrawn(uint64 usdAmount);
    event HlpDepositEnabledUpdated(bool enabled);
    event RedemptionsFunded(uint256 amount);
    event RedeemEscrowReclaimed(uint256 amount);
    event EmergencyActionSent(address indexed sender, bytes32 dataHash);
    event AccountantUpdated(address newAccountant);
    event MultisigVaultUpdated(address newMultisigVault);
    event RedeemEscrowUpdated(address redeemEscrow);
    event YieldEscrowUpdated(address yieldEscrow);
    event BridgeRetentionAmountUpdated(uint256 amount);
    event PmEnabledUpdated(bool enabled);
    event BlpSupplied(uint64 indexed token, uint64 l1Amount);
    event BlpWithdrawn(uint64 indexed token, uint64 l1Amount);
    event OperatorPaused(address indexed by);
    event OperatorUnpaused(address indexed by);



    // ═══════════════════════════════════════════════════════════
    //                    INITIALIZER
    // ═══════════════════════════════════════════════════════════

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {}

    


    // ═══════════════════════════════════════════════════════════
    //                      MODIFIER
    // ═══════════════════════════════════════════════════════════

    modifier requireWired() { _; }

    modifier whenOperatorNotPaused() { _; }

    // ═══════════════════════════════════════════════════════════
    //                   USER OPERATIONS
    // ═══════════════════════════════════════════════════════════

    

    

    

    // ═══════════════════════════════════════════════════════════
    //                 OPERATOR OPERATIONS
    // ═══════════════════════════════════════════════════════════

    // ─── Bridge (EVM ↔ L1) ──────────────────────────────────
    // NOTE: Once the vault contract account supports Portfolio Margin,
    // all positions will be held by the vault directly and multisigVault
    // will be disabled.
    

    

    

    // ─── Hedge execution ────────────────────────────────────

    

    

    

    // ─── HLP strategy ───────────────────────────────────────

    

    

    

    // ─── BLP (Borrow/Lend Pool) ─────────────────────────────

    /// @notice Supply `l1Amount` of `token` into HL's BLP (action 15 op=0). L1 8-dp wei.
    

    /// @notice Withdraw from BLP back to spot (action 15 op=1). `l1Amount=0` means max.
    

    // ─── Settlement + Yield ─────────────────────────────────

    /// @notice Atomic all-or-nothing settle. Keeper submits `proposedYield`
    ///         (phantom-excluded off-chain); Accountant enforces 4 gates
    ///         (initialized / interval / distributable / annualized) and Vault
    ///         enforces EVM USDC sufficiency. On success the full
    ///         `proposedYield` moves to YieldEscrow; otherwise tx reverts.
    /// @dev Only `shortfall` is reserved here. `bridgeRetentionAmount` is a
    ///      bridge-to-L1 working balance (see `netBridgeable`); it is NOT a
    ///      solvency invariant and must not block yield routing.
    

    

    // ─── Fund routing (Vault ↔ RedeemEscrow) ────────────────

    

    

    // ═══════════════════════════════════════════════════════════
    //                  GUARDIAN OPERATIONS
    // ═══════════════════════════════════════════════════════════

    

    

    /// @notice Halt every operator-driven mutation (hedge/HLP/BLP/bridges/yield/escrow).
    ///         User fund I/O stays on the independent `paused` flag.
    

    

    // ═══════════════════════════════════════════════════════════
    //                  GOVERNOR OPERATIONS
    // ═══════════════════════════════════════════════════════════

    /// @dev Emergency escape hatches DO NOT check either pause flag. They exist precisely
    ///      to recover from states where the operator pipeline is suspect / halted —
    ///      gating them by pause would defeat their purpose. Governor 24h timelock is
    ///      the guard.
    

    

    

    

    

    

    

    

    /// @notice Flip after PM is activated on Vault's L1 account; gates the 0x811 read in `_sendL1Bridge`.
    

    // ═══════════════════════════════════════════════════════════
    //                      INTERNAL
    // ═══════════════════════════════════════════════════════════

    /// @dev Checks L1 USDC (spot + supplied when PM on) covers `amount` before SEND_ASSET; avoids silent L1 drop when hedge is still locked.
    

    /// @dev `spotAsset` is the HL limit-order asset for the spot leg (= 10000 + pair_index),
    ///      NOT the token_index. See `MonetrixConfig.TradeableAsset` for the distinction.
    

    /// @dev For `isPerp=false`, `asset` is the HL pair_asset_id (= 10000 + pair_index).
    

    

    // ═══════════════════════════════════════════════════════════
    //                       VIEW
    // ═══════════════════════════════════════════════════════════

    

    

    



    

    struct RedeemRequestDetail {
        uint256 requestId;
        uint256 usdmAmount;
        uint256 cooldownEnd;
    }

    

    

}
