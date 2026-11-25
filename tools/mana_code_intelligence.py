#!/usr/bin/env python3
"""Read-only static diagnostics for ManaOS Kotlin/Java source. Never executes project code."""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path

SOURCE_SUFFIXES = {".kt", ".java"}
MAX_FILE_BYTES = 2_000_000
SKIP_DIRS = {".git", ".gradle", "build", "out", "generated"}

RULES = [
    ("TODO_MARKER", "LOW", re.compile(r"\b(TODO|FIXME|XXX)\b"), "Unfinished-work marker; confirm whether the task is still relevant."),
    ("FORCE_UNWRAP", "MEDIUM", re.compile(r"!!"), "Kotlin force unwrap can crash when the value is null; verify the invariant or use safe handling."),
    ("THREAD_SLEEP", "MEDIUM", re.compile(r"\bThread\.sleep\s*\("), "Blocking sleep may stall a UI or worker thread; verify the execution context."),
    ("EMPTY_CATCH", "MEDIUM", re.compile(r"catch\s*\([^)]*\)\s*\{\s*\}"), "An empty catch block can hide failures; consider reporting or handling the error."),
    ("BROAD_CATCH", "LOW", re.compile(r"catch\s*\([^)]*(?:Exception|Throwable)[^)]*\)"), "Broad exception handling may conceal specific failure causes; inspect error handling."),
    ("GLOBAL_SCOPE", "MEDIUM", re.compile(r"GlobalScope\.(?:launch|async)"), "GlobalScope work may outlive its owner; prefer a lifecycle-bound scope where appropriate."),
    ("WEBVIEW_JS", "HIGH", re.compile(r"addJavascriptInterface\s*\("), "JavaScript bridge exposed to WebView content needs a strict origin and input review."),
    ("DYNAMIC_CODE", "HIGH", re.compile(r"\b(?:DexClassLoader|PathClassLoader|InMemoryDexClassLoader)\s*\("), "Dynamic code loading requires provenance, signature and integrity review."),
    ("PROCESS_EXEC", "HIGH", re.compile(r"Runtime\.getRuntime\s*\(\)\.exec|ProcessBuilder\s*\("), "Process execution is security-sensitive; verify fixed arguments and trusted inputs."),
    ("INSECURE_TLS", "HIGH", re.compile(r"ALLOW_ALL_HOSTNAME_VERIFIER|checkServerTrusted\s*\([^)]*\)\s*\{\s*\}"), "TLS verification override pattern detected; confirm certificate validation is not bypassed."),
    ("SECRET_LITERAL", "HIGH", re.compile(r"(?i)(?:api[_-]?key|client[_-]?secret|password|access[_-]?token)\s*[:=]\s*[\"'][A-Za-z0-9_./+=-]{12,}[\"']"), "Possible credential literal; verify and move secrets out of source control."),
]

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    root = args.source.resolve()
    out = args.out.resolve()
    if not root.is_dir():
        raise SystemExit(f"Source directory not found: {root}")
    findings = []
    scanned = 0
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.suffix not in SOURCE_SUFFIXES:
            continue
        relative = path.relative_to(root)
        if any(part in SKIP_DIRS for part in relative.parts):
            continue
        try:
            if path.stat().st_size > MAX_FILE_BYTES:
                findings.append({"rule":"FILE_TOO_LARGE","severity":"LOW","file":str(relative),"line":1,"evidence":"File skipped because it exceeds 2 MB.","recommendation":"Review this generated or unusually large source file separately."})
                continue
            content = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as exc:
            findings.append({"rule":"SOURCE_READ_ERROR","severity":"LOW","file":str(relative),"line":1,"evidence":str(exc)[:200],"recommendation":"Check file encoding and repository permissions."})
            continue
        scanned += 1
        lines = content.splitlines()
        for number, line in enumerate(lines, 1):
            for rule, severity, pattern, recommendation in RULES:
                match = pattern.search(line)
                if match:
                    findings.append({
                        "rule": rule, "severity": severity, "file": str(relative),
                        "line": number, "evidence": line.strip()[:240],
                        "recommendation": recommendation
                    })
    findings.sort(key=lambda x: ({"HIGH":0,"MEDIUM":1,"LOW":2}.get(x["severity"],3), x["file"], x["line"]))
    summary = {
        "scanner": "MANA Code Intelligence static rules v1",
        "mode": "read-only; source code is never executed",
        "source_root": str(root),
        "files_scanned": scanned,
        "finding_count": len(findings),
        "findings": findings,
        "limitations": [
            "Pattern-based triage only; findings are not proof of vulnerabilities or defects.",
            "No code is generated, modified, committed, merged, or installed.",
            "A clean report does not prove the application is secure or correct."
        ]
    }
    out.mkdir(parents=True, exist_ok=True)
    (out / "MANA_CODE_INTELLIGENCE.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = [
        "# MANA Code Intelligence — static analysis",
        "",
        f"- Files scanned: {scanned}",
        f"- Findings: {len(findings)}",
        "- Mode: read-only; no project code was executed or modified.",
        "",
        "## Findings",
        ""
    ]
    if not findings:
        lines.append("No configured pattern was detected. This is not a security certification.")
    for item in findings:
        lines.extend([
            f"### [{item['severity']}] {item['rule']} — {item['file']}:{item['line']}",
            "",
            f"- Evidence: `{item['evidence']}`",
            f"- Suggested review: {item['recommendation']}",
            ""
        ])
    lines.extend(["## Limitations", ""] + [f"- {x}" for x in summary["limitations"]])
    (out / "MANA_CODE_INTELLIGENCE.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Scanned {scanned} Kotlin/Java files; recorded {len(findings)} review findings.")

if __name__ == "__main__":
    main()
