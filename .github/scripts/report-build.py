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
            # 效能量測：測試用 System.out 印出以「[量測]」開頭的行，轉成 notice 方便在 Checks 頁查看
            for out in case.findall("system-out"):
                for line in (out.text or "").splitlines():
                    if line.startswith("[量測]"):
                        emit("notice", line, "量測")
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
    # SpotBugs 會把發現的問題印成 [ERROR]，建置成功時不當成錯誤，件數另由 report_static_analysis 彙整
    if not any("BUILD FAILURE" in line for line in lines):
        return
    errors = [line for line in lines if line.startswith("[ERROR]") and line.strip() != "[ERROR]"
              and "Re-run Maven" not in line and "Help 1" not in line and "full stack trace" not in line]
    for line in errors[:MAX_ERRORS]:
        emit("error", re.sub(r"^\[ERROR\]\s*", "", line), "Maven")


def report_static_analysis() -> None:
    """Checkstyle、SpotBugs 目前只警告不擋建置：在 Checks 頁列出總件數與件數最多的規則，方便追蹤是否在減少。"""
    from collections import Counter

    try:
        root = ET.parse("target/checkstyle-result.xml").getroot()
        rules = Counter()
        for error in root.iter("error"):
            rules[(error.get("source") or "").split(".")[-1].removesuffix("Check")] += 1
        top = "、".join(f"{name} {count}" for name, count in rules.most_common(8))
        emit("notice", f"共 {sum(rules.values())} 項。{top}", "Checkstyle")
    except (FileNotFoundError, ET.ParseError):
        pass

    try:
        root = ET.parse("target/spotbugsXml.xml").getroot()
        bugs = list(root.iter("BugInstance"))
        patterns = Counter(bug.get("type") for bug in bugs)
        top = "、".join(f"{name} {count}" for name, count in patterns.most_common(8))
        emit("notice", f"共 {len(bugs)} 項。{top}", "SpotBugs")
        for bug in bugs[:MAX_ERRORS]:
            line = bug.find("SourceLine")
            where = f"{line.get('sourcepath')}:{line.get('start')}" if line is not None else ""
            message = bug.findtext("LongMessage") or bug.get("type")
            emit("warning", f"{where} {message}", f"SpotBugs {bug.get('type')}")
    except (FileNotFoundError, ET.ParseError):
        pass


if __name__ == "__main__":
    tests = report_tests()
    report_static_analysis()
    report_build_errors(sys.argv[1] if len(sys.argv) > 1 else "build.log")
