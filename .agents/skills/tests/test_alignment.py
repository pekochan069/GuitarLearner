from pathlib import Path
import re


root = Path(__file__).resolve().parents[1]
skills = (
    "alignment-engineering", "grilling", "grill-with-docs", "wayfinder",
    "domain-modeling", "prototype", "handoff", "research",
    "improve-codebase-architecture", "to-spec", "to-tickets", "implement",
    "implement-spec",
)
links_checked = 0

for name in skills:
    path = root / name / "SKILL.md"
    body = path.read_text(encoding="utf-8-sig")
    match = re.match(r"\A---\r?\n(.*?)\r?\n---(?:\r?\n|$)", body, re.S)
    assert match, f"Missing frontmatter in {path}"
    fields = dict(re.findall(r"^([\w-]+):\s*(.+)$", match[1], re.M))
    assert fields.get("name") == name, f"Wrong skill name in {path}"
    assert fields.get("description"), f"Missing description in {path}"
    if name != "alignment-engineering":
        assert "../alignment-engineering/SKILL.md" in body, f"Missing contract in {path}"
    prose = re.sub(r"^```[^\n]*\n.*?^```[^\n]*$", "", body, flags=re.M | re.S)
    for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", prose):
        if "://" in target or target.startswith("#"):
            continue
        destination = path.parent / target.split("#", 1)[0]
        assert destination.is_file(), f"Broken link in {path}: {target}"
        links_checked += 1
    metadata = root / name / "agents" / "openai.yaml"
    if metadata.is_file():
        config = metadata.read_text(encoding="utf-8-sig")
        explicit_only = "allow_implicit_invocation: false" in config
        explicit = fields.get("disable-model-invocation") == "true"
        assert explicit_only == explicit, f"Invocation policy disagrees in {metadata}"

for name in ("implement", "implement-spec"):
    body = (root / name / "SKILL.md").read_text(encoding="utf-8")
    assert "`pstack:poteto-mode`" in body, f"Missing engineering dispatch in {name}"

for relative in ("prototype/LOGIC.md", "prototype/UI.md", "UPSTREAM.md"):
    path = root / relative
    body = path.read_text(encoding="utf-8-sig")
    for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", body):
        if "://" in target or target.startswith("#"):
            continue
        assert (path.parent / target.split("#", 1)[0]).is_file(), f"Broken link in {path}: {target}"
        links_checked += 1

print(f"PASS: {len(skills)} skills and {links_checked} local links.")
