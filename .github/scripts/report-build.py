"""把 Maven 建置結果轉成 GitHub Actions 註記（annotation），失敗原因可直接在 GitHub 網頁的 Checks 頁看到。

用法：python3 .github/scripts/report-build.py build.log
"""
import glob
import re
import sys
import xml.etree.ElementTree as ET

MAX_ERRORS = 10


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def emit(level: str, message: str, title: str = "") -> None:
    title_part = f" title={escape(title)}" if title else ""
    print(f"::{level}{title_part}::{escape(message[:3000])}")


def report_tests() -> int:
    total = failures = errors = skipped = 0
    emitted = 0
    for path in sorted(glob.glob("target/surefire-reports/TEST-*.xml")):
        suite = ET.parse(path).getroot()
        total += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
        for case in suite.iter("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                if emitted >= MAX_ERRORS:
                    continue
                detail = (problem.get("message") or "") + "\n" + (problem.text or "")[:2000]
                emit("error", detail, f"{case.get('classname', '').split('.')[-1]}.{case.get('name')}")
                emitted += 1
    if total:
        emit("notice", f"Tests run: {total}, Failures: {failures}, Errors: {errors}, Skipped: {skipped}",
             "Test summary")
    return total


def report_build_errors(log_path: str) -> None:
    try:
        lines = open(log_path, encoding="utf-8", errors="replace").read().splitlines()
    except FileNotFoundError:
        return
    errors = [line for line in lines if line.startswith("[ERROR]") and line.strip() != "[ERROR]"
              and "Re-run Maven" not in line and "Help 1" not in line and "full stack trace" not in line]
    for line in errors[:MAX_ERRORS]:
        emit("error", re.sub(r"^\[ERROR\]\s*", "", line), "Maven")


if __name__ == "__main__":
    tests = report_tests()
    report_build_errors(sys.argv[1] if len(sys.argv) > 1 else "build.log")
