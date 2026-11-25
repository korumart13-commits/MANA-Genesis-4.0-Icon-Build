#!/usr/bin/env python3
"""Summarize CI evidence; never signs, publishes, installs, or deploys an APK."""
from __future__ import annotations
import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tests", type=Path, required=True)
    ap.add_argument("--analysis", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    reports = sorted(args.tests.rglob("TEST-*.xml")) if args.tests.exists() else []
    totals = {"suites": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    parse_errors = []
    for report in reports:
        try:
            root = ET.parse(report).getroot()
            suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
            for suite in suites:
                totals["suites"] += 1
                for key in ("tests", "failures", "errors", "skipped"):
                    try: totals[key] += int(suite.attrib.get(key, "0"))
                    except ValueError: parse_errors.append(str(report))
        except (ET.ParseError, OSError):
            parse_errors.append(str(report))
    analysis = json.loads(args.analysis.read_text(encoding="utf-8"))
    findings = analysis.get("findings", [])
    high = [x for x in findings if x.get("severity") in ("HIGH", "CRITICAL")]
    medium = [x for x in findings if x.get("severity") == "MEDIUM"]
    tests_ok = bool(reports) and totals["failures"] == 0 and totals["errors"] == 0 and not parse_errors
    review_ready = tests_ok and not high
    status = "READY_FOR_OWNER_REVIEW" if review_ready else "NEEDS_REVIEW"
    args.out.mkdir(parents=True, exist_ok=True)
    payload = {
        "status": status,
        "tests": totals,
        "test_report_files": len(reports),
        "test_report_parse_errors": parse_errors,
        "static_analysis": {
            "findings": len(findings), "high_or_critical": len(high), "medium": len(medium),
            "high_findings": high
        },
        "meaning": "CI evidence summary only; not a security certification or release approval.",
        "automatic_release": False,
        "automatic_install": False
    }
    (args.out / "MANA_BUILD_QUALITY.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    lines = [
        "# MANA Build Quality Gate", "", f"- Status: **{status}**",
        f"- Test suites: {totals['suites']}; tests: {totals['tests']}; failures: {totals['failures']}; errors: {totals['errors']}; skipped: {totals['skipped']}",
        f"- XML reports found: {len(reports)}; parse errors: {len(parse_errors)}",
        f"- Static findings: {len(findings)}; high/critical: {len(high)}; medium: {len(medium)}",
        "", "## Decision", ""
    ]
    if review_ready:
        lines.append("Automated evidence is sufficient to hand this build to the owner for review. This is not permission to merge, publish, sign for production, or install.")
    else:
        lines.append("Do not mark this change ready. Inspect missing/failed test reports and high-severity static findings.")
    lines += ["", "## Limits", "", "- Pattern-based static findings may be false positives or miss defects.", "- A passing debug build is not a Play approval, production signature, malware certification, or device compatibility proof.", "- No deployment or installation was performed."]
    (args.out / "MANA_BUILD_QUALITY.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(status)
if __name__ == "__main__":
    main()
