import os
import re
import sys
import subprocess

tag = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("GITHUB_REF_NAME", "")

notes = ""
if os.path.exists("CHANGELOG.md") and tag:
    try:
        with open("CHANGELOG.md", "r", encoding="utf-8") as f:
            text = f.read()
        pattern = rf"(?ms)^##\s+\[?{re.escape(tag)}\]?[^\n]*\n(.*?)(?=^##\s+|\Z)"
        m = re.search(pattern, text)
        if m:
            notes = m.group(1).strip()
    except Exception as e:
        print(f"Error reading CHANGELOG.md: {e}")

if not notes and tag:
    try:
        notes = subprocess.check_output(["git", "tag", "-l", "--format=%(contents)", tag], text=True).strip()
    except Exception:
        notes = ""

if not notes:
    notes = f"Release build for {tag or 'unknown version'}."

display_version = tag.removeprefix("Mod-v") if tag else "unknown"
body = f"# APRSdroid Mod {display_version}\n\n{notes}\n"

with open("RELEASE_NOTES.md", "w", encoding="utf-8") as out:
    out.write(body)

print(f"Generated RELEASE_NOTES.md for {tag or 'unknown version'}")
