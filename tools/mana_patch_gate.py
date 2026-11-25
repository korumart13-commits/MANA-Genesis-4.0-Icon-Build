#!/usr/bin/env python3
"""Validate and apply a narrowly-scoped ManaOS source patch in an isolated CI workspace."""
from __future__ import annotations
import argparse
import re
import subprocess
import sys
from pathlib import Path

ALLOWED_PREFIXES = (
    "app/src/main/java/com/mana/os/",
    "app/src/main/kotlin/com/mana/os/",
    "core/src/main/java/com/mana/os/",
    "core/src/main/kotlin/com/mana/os/",
    "launcher/src/main/java/com/mana/os/",
    "launcher/src/main/kotlin/com/mana/os/",
    "settings/src/main/java/com/mana/os/",
    "settings/src/main/kotlin/com/mana/os/",
    "systemui/src/main/java/com/mana/os/",
    "systemui/src/main/kotlin/com/mana/os/",
)
FORBIDDEN_PARTS = (
    ".github/", "gradle/", "build.gradle", "settings.gradle",
    "AndroidManifest.xml", "proguard", "keystore", ".jks", ".keystore",
    "avb", "bootloader", "selinux", "sepolicy", "vendor/", "kernel/",
)
MAX_PATCH_BYTES = 256_000

def fail(message: str) -> None:
    print(f"PATCH REJECTED: {message}", file=sys.stderr)
    raise SystemExit(2)

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-dir", required=True, type=Path)
    parser.add_argument("--patch", required=True, type=Path)
    parser.add_argument("--scope", choices=("minor_patch", "full_update"), required=True)
    parser.add_argument("--owner-approval", default="")
    args = parser.parse_args()

    project = args.project_dir.resolve()
    patch = args.patch.resolve()
    if not project.is_dir():
        fail("Android project directory does not exist.")
    if not patch.is_file():
        fail("Proposal patch file does not exist.")
    raw = patch.read_bytes()
    if not raw or len(raw) > MAX_PATCH_BYTES:
        fail("Patch is empty or exceeds the 256 KB limit.")
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        fail("Only UTF-8 text patches are accepted.")
    if "\x00" in text or "GIT binary patch" in text:
        fail("Binary patches are not accepted.")
    if re.search(r"(?m)^\s*(?:diff --git|--- )", text) is None:
        fail("No unified diff was found.")

    changed = []
    for line in text.splitlines():
        if line.startswith("+++ "):
            path = line[4:].split("\t", 1)[0]
            if path == "/dev/null":
                continue
            if path.startswith("b/"):
                path = path[2:]
            if path.startswith("/") or ".." in Path(path).parts:
                fail(f"Unsafe patch path: {path}")
            if any(part.lower() in path.lower() for part in FORBIDDEN_PARTS):
                fail(f"Protected path cannot be changed: {path}")
            if not path.endswith((".kt", ".java")):
                fail(f"Only Kotlin/Java source files are allowed: {path}")
            if not path.startswith(ALLOWED_PREFIXES):
                fail(f"Path is outside the ManaOS source allowlist: {path}")
            changed.append(path)

    if not changed:
        fail("Patch does not change an allowed source file.")
    if len(set(changed)) > 12:
        fail("A single proposal may change at most 12 source files.")

    if args.scope == "full_update" and args.owner_approval != "APPROVE_FULL_UPDATE":
        fail("Full update requires owner_approval=APPROVE_FULL_UPDATE.")
    if args.scope == "minor_patch" and args.owner_approval:
        print("Note: owner approval token is ignored for minor patches.")

    check = subprocess.run(
        ["git", "apply", "--check", "--whitespace=error", str(patch)],
        cwd=project, text=True, capture_output=True
    )
    if check.returncode:
        fail("git apply --check failed: " + (check.stderr.strip() or check.stdout.strip()))
    apply = subprocess.run(
        ["git", "apply", "--whitespace=error", str(patch)],
        cwd=project, text=True, capture_output=True
    )
    if apply.returncode:
        fail("Patch application failed: " + (apply.stderr.strip() or apply.stdout.strip()))

    print("PATCH APPLIED IN ISOLATED CI WORKSPACE")
    print("Scope:", args.scope)
    print("Changed files:")
    for path in sorted(set(changed)):
        print(" -", path)
    print("No commit, merge, APK installation, or device/firmware modification was performed.")

if __name__ == "__main__":
    main()
