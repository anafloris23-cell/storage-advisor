# StorageAdvisor

StorageAdvisor is a static analysis tool for Solidity contracts that looks at how state
variables are laid out in storage and points out where slots are being wasted. On the EVM,
each storage slot is 32 bytes and writing one costs ~20,000 gas, so the order in which you
declare variables has a direct effect on deployment and runtime cost. The compiler packs
variables greedily in declaration order; reordering them often fits the same data into fewer
slots without changing any logic.

The tool reads the `storageLayout` produced by `solc`, computes how many slots the current
declaration uses, then searches for a packing that uses the minimum number of slots and reports
the difference.

## Requirements

- Java 17+
- Maven 3.8+
- `solc` available on the `PATH` (the tool shells out to it with `--standard-json`)

## Building

```bash
mvn compile
```

Dependencies are minimal — only Jackson for JSON handling.

## Usage

There are two modes, selected automatically based on whether the argument is a file or a
directory.

**Single file.** Analyzes one `.sol` file and writes a report per contract:

```bash
mvn exec:java -Dexec.args="examples/03-suboptimal-order.sol"
```

For every contract found, it writes `analysis-report-<Contract>.json` and
`analysis-report-<Contract>.md` to the working directory, and prints a summary to the console:
current vs. recommended slot count, wasted bytes, detected issues, the recommended per-slot
layout, and the variable order to use in the source.

**Bulk mode.** Walks a directory recursively, analyzes every `.sol` file, and writes one CSV row
per contract:

```bash
mvn exec:java -Dexec.args="path/to/contracts bulk-report.csv"
```

The CSV captures current/recommended slots, slots saved, wasted bytes, the packing strategy
used, struct-level savings, and any preprocessing that was applied. At the end it prints a
summary and the top contracts by slots saved, which is what the experimental evaluation in the
thesis is built on.

## How it works

The analysis runs as a pipeline:

1. **Preprocessing** (`SolidityPreprocessor`) — so a single file can be compiled in isolation,
   imports and `using` directives are commented out, inheritance lists are stripped, and function
   bodies are emptied. Unresolved external types get minimal `interface` stubs appended. The
   resulting layout reflects only the variables declared explicitly in that file.
2. **Compilation** (`SolcInputBuilder` + `SolcRunner`) — builds the standard-JSON input requesting
   `storageLayout` and invokes `solc`.
3. **Parsing** (`StorageLayoutParser`) — turns the compiler output into typed `ContractLayout`
   objects, including struct definitions.
4. **Detection** (`InefficiencyDetector`) — flags partially wasted slots and small types stranded
   next to large ones.
5. **Packing** (`PackingHeuristic`) — splits variables into full-slot, dynamic, and packable
   groups. The packable ones are arranged using an exact dynamic-programming bin packer
   (`BitmaskBinPacker`) that finds the provably minimal number of slots. For contracts with more
   packable variables than the DP packer can handle, it falls back to First-Fit Decreasing
   (`FirstFitBinPacker`).
6. **Reporting** (`ReportWriter`) — emits the JSON and Markdown reports.

Structs are analyzed separately (`StructOptimizer`): the tool computes the optimal field ordering
and multiplies the per-instance saving by how many times the struct is used directly in storage.

## Examples

`examples/` contains hand-written contracts used during development, including a baseline that is
already optimally packed and several with deliberately suboptimal ordering. `examples/synthetic/`
holds a set of small but realistic contracts (auction, vesting, staking, lending, etc.) used for
testing the analysis end to end.

This is a research prototype developed for a master's thesis; it is meant to surface and quantify
storage inefficiencies, not to rewrite contracts automatically.
