# garnix-cli

[![built with garnix](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Fgarnix.io%2Fapi%2Fbadges%2Foutskirtslabs%2Fgarnix-cli)](https://garnix.io/repo/outskirtslabs/garnix-cli)

> A small CLI for inspecting Garnix build runs from the terminal.

`garnix-cli` gives Garnix a `gh`-style workflow:

- list recent runs for a repository
- view the latest run or a specific commit
- watch a run until it finishes
- print failed build IDs so logs are easy to fetch
- emit human, plain text, JSON, or EDN output

> [!NOTE]
> This tool was largely vibe-coded.

## Motivation

Garnix already gives you CI for Nix flakes.
But humans and coding agents often need to stay in the terminal: watch a push, see which build failed, copy the build ID, and fetch the logs.

`garnix-cli` keeps that loop short.

```bash
./garnix-cli watch -R outskirtslabs/garnix-cli --compact --exit-status
# 09783d5d outskirtslabs/garnix-cli main failure — 7 succeeded, 1 failed, 0 pending, 0 cancelled — failed builds: B5aPM2pB (packages-default)

./garnix-cli logs B5aPM2pB
```

## Requirements

- [Babashka](https://babashka.org/)
- `git`, for local repository auto-detection

For private repositories, set `GARNIX_API_TOKEN`:

```bash
export GARNIX_API_TOKEN=...
```

## Install

<details>
<summary><b>Option 1: Direct download</b></summary>

```bash
curl -fsSL https://raw.githubusercontent.com/outskirtslabs/garnix-cli/main/garnix-cli -o ~/.local/bin/garnix-cli
chmod +x ~/.local/bin/garnix-cli
```

</details>

<details>
<summary><b>Option 2: Install with bbin</b></summary>

```bash
bbin install io.github.outskirtslabs/garnix-cli
```

</details>

<details>
<summary><b>Option 3: Install with Nix</b></summary>

```bash
nix profile install github:outskirtslabs/garnix-cli
```

Or run without installing:

```bash
nix run github:outskirtslabs/garnix-cli -- help
```

</details>

<details>
<summary><b>Option 4: From git</b></summary>

```bash
git clone https://github.com/outskirtslabs/garnix-cli.git
cd garnix-cli
bb build
ln -s "$(pwd)/garnix-cli" ~/.local/bin/garnix-cli
```

</details>

## Usage

Run commands from a git checkout, or pass `-R OWNER/REPO`.

### List runs

```bash
# Current repository
./garnix-cli list

# Another repository
./garnix-cli list -R outskirtslabs/garnix-cli

# Filter and limit results
./garnix-cli list -R outskirtslabs/garnix-cli --status failure --limit 5
```

### View a run

```bash
# Latest run for current repository
./garnix-cli view

# Latest run for another repository
./garnix-cli view -R outskirtslabs/garnix-cli

# Specific commit
./garnix-cli view 09783d5d13eb6a6c03dc2c28c1ce82db82d49721
```

When a run has failures, `view` prints the failed build IDs:

```text
Failed Builds:
  • packages-default (x86_64-linux): [FAIL] Failure
    Build ID: B5aPM2pB
```

### Watch a run

```bash
# Watch latest run
./garnix-cli watch -R outskirtslabs/garnix-cli

# Compact agent-friendly output
./garnix-cli watch -R outskirtslabs/garnix-cli --compact --exit-status --interval 10
```

`--exit-status` exits non-zero when the watched run fails or is cancelled.
Compact output includes failed build IDs as soon as Garnix reports them.

### Fetch logs

```bash
./garnix-cli logs B5aPM2pB

# Raw log response
./garnix-cli logs B5aPM2pB --raw
```

### Machine-readable output

Global output flags work before or after the command:

```bash
./garnix-cli --json list -R outskirtslabs/garnix-cli
./garnix-cli --edn view -R outskirtslabs/garnix-cli
./garnix-cli --plain watch -R outskirtslabs/garnix-cli --compact
```

You can also use `--format human|plain|json|edn`.

### Getting help

```bash
./garnix-cli help
```

## Development

Use the Nix dev shell:

```bash
nix develop
```

Common tasks:

```bash
# Run tests
bb test

# Run CI checks: tests, format check, and lint
bb ci

# Rebuild the uberscript
bb build

# Build the Nix package
nix build
```

## License: European Union Public License 1.2

Copyright © 2026 Casey Link

Distributed under the [EUPL-1.2](https://spdx.org/licenses/EUPL-1.2.html).
