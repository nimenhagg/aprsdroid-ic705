import os
import re
import sys
import subprocess

tag = os.environ.get("GITHUB_REF_NAME", "")
if not tag and len(sys.argv) > 1:
    tag = sys.argv[1]

notes = ""
if os.path.exists("CHANGELOG.md"):
    try:
        with open("CHANGELOG.md", "r", encoding="utf-8") as f:
            text = f.read()
        pattern = r"##\s*.*?\[?" + re.escape(tag) + r"\]?.*?\n(.*?)(?=\n##\s|\Z)"
        m = re.search(pattern, text, re.DOTALL)
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
    notes = f"Release build for {tag}."

body = f"# 🚀 APRSdroid Mod {tag}\n\n{notes}\n"

with open("RELEASE_NOTES.md", "w", encoding="utf-8") as out:
    out.write(body)

print("Generated RELEASE_NOTES.md successfully")
