// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// Struct cu câmpuri ordonate semantic dar suboptim pentru storage:
//   borrower (20B) + uint256 amount + uint64 deadline + bool active
// Layout intern:
//   slot 0: borrower (20B)  + 12 wasted
//   slot 1: amount (32B)
//   slot 2: deadline (8B) + active (1B) + 23 wasted
// Total: 3 sloturi pentru struct.
//
// Optim (dacă reordonezi):
//   slot 0: borrower (20B) + deadline (8B) + active (1B) = 29B
//   slot 1: amount (32B)
// Total: 2 sloturi → 1 slot economisit per Loan instance.

contract StructLoan {
    struct Loan {
        address borrower;
        uint256 amount;
        uint64  deadline;
        bool    active;
    }

    Loan loanA;
    Loan loanB;
    Loan loanC;
}
