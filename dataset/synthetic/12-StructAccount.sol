// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// Struct cu câmpuri mici (uint64) intercalate între câmpuri mari (uint256).
// Ordinea declarată e "naturală" (a, big1, b, big2, ...) dar foarte suboptimă:
// fiecare uint64 ajunge singur într-un slot pentru că e separat de un uint256.
//
// Layout solc (ordinea declarată) — 7 sloturi:
//   slot 0: a    (8B)  + 24 wasted
//   slot 1: big1 (32B)
//   slot 2: b    (8B)  + 24 wasted
//   slot 3: big2 (32B)
//   slot 4: c    (8B)  + 24 wasted
//   slot 5: big3 (32B)
//   slot 6: d    (8B)  + 24 wasted
//
// Optim (DP-bitmask) — 4 sloturi:
//   slot 0: big1 (32B)
//   slot 1: big2 (32B)
//   slot 2: big3 (32B)
//   slot 3: a + b + c + d (8+8+8+8 = 32B, plin)
// → 3 sloturi economisite per instanță.

contract StructAccount {
    struct Account {
        uint64  a;
        uint256 big1;
        uint64  b;
        uint256 big2;
        uint64  c;
        uint256 big3;
        uint64  d;
    }

    Account accountX;
    Account accountY;
}
