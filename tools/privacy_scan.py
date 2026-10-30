#!/usr/bin/env python3
"""Looks through the release folder for things that must not be published: personal names, private network
addresses, e-mail addresses, keys and tokens. Run it before every push. Extra words to catch (your name, your street,
a client) go one per line in tools/privacy_denylist.txt, which stays out of the repository (it is in .gitignore).
Exit code 1 when anything is found."""
import fnmatch, os, re, socket, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {".git", "third_party"}   # third_party is other people's code, kept as it came
SKIP_EXT = {".png", ".jpg", ".jpeg", ".webp", ".ttf", ".otf", ".so", ".apk", ".jar", ".zip", ".gz", ".ico", ".keystore", ".dex", ".class", ".bin"}
def own_network():
    """This computer's own /24 (192.168.3. for example): the addresses that would give away where the author lives.
    Addresses of other networks are examples in docs and tests; --all-private lists them too."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0].rsplit(".", 1)[0]
    except OSError:
        return None
    finally:
        s.close()


ALL_PRIVATE = "--all-private" in sys.argv
_net = own_network()
PRIVATE = (r"\b(?:192\.168|10\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}\b" if ALL_PRIVATE or not _net
           else r"\b" + re.escape(_net) + r"\.\d{1,3}\b")
PATTERNS = [
    ("private address", re.compile(PRIVATE)),
    ("e-mail", re.compile(r"\b[\w.+-]+@(?!example\.|users\.noreply\.github\.com)[\w-]+\.[a-z]{2,}\b", re.I)),
    ("key or token", re.compile(r"(?:AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30,}|ghp_[0-9A-Za-z]{30,}|sk-[A-Za-z0-9]{30,}|-----BEGIN [A-Z ]*PRIVATE KEY-----)")),
    ("local path", re.compile(r"/Users/[A-Za-z0-9._-]+/|C:\\Users\\[A-Za-z0-9._-]+\\")),
]
# Words that identify a person: the owner's own, plus whatever is in the private denylist.
WORDS = ["giorgio", "piredda"]
deny = os.path.join(ROOT, "tools", "privacy_denylist.txt")
if os.path.exists(deny):
    WORDS += [w.strip().lower() for w in open(deny, encoding="utf-8") if w.strip() and not w.startswith("#")]

def ignore_rules():
    """Patterns from every .gitignore: what git will not publish is not scanned."""
    rules = []
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        if ".gitignore" in files:
            where = os.path.relpath(base, ROOT)
            for line in open(os.path.join(base, ".gitignore"), encoding="utf-8", errors="replace"):
                line = line.strip()
                if line and not line.startswith(("#", "!")):
                    rules.append((where if where != "." else "", line.rstrip("/").lstrip("/")))
    return rules


RULES = ignore_rules()


def ignored(rel):
    parts = rel.split(os.sep)
    for where, pat in RULES:
        if where and not rel.startswith(where + os.sep):
            continue
        sub = rel[len(where) + 1:] if where else rel
        if any(fnmatch.fnmatch(p, pat) for p in sub.split(os.sep)) or fnmatch.fnmatch(sub, pat):
            return True
    return False


def is_test(rel):
    return os.sep + "test" + os.sep in rel or os.sep + "androidTest" + os.sep in rel


hits = []
for base, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not ignored(os.path.relpath(os.path.join(base, d), ROOT))]
    for f in files:
        path = os.path.join(base, f)
        rel = os.path.relpath(path, ROOT)
        if ignored(rel):
            continue
        if os.path.splitext(f)[1].lower() in SKIP_EXT or rel.endswith(("privacy_scan.py", "privacy_denylist.txt")):
            continue
        try:
            text = open(path, encoding="utf-8").read()
        except (UnicodeDecodeError, OSError):
            continue
        for n, line in enumerate(text.splitlines(), 1):
            low = line.lower()
            for label, rx in PATTERNS:
                m = rx.search(line)
                if not m:
                    continue
                if label == "private address" and is_test(rel):   # sample addresses in tests are not anyone's network
                    continue
                if label == "e-mail" and ("this@" in line or m.group(0) == "git@github.com"):   # Kotlin labels and clone URLs
                    continue
                hits.append((rel, n, label, m.group(0)))
            for w in WORDS:
                if w in low:
                    hits.append((rel, n, "name", w))
shown = {}
for rel, n, label, what in hits:
    key = (rel, label, what)
    shown.setdefault(key, [n, 0])[1] += 1
for (rel, label, what), (n, count) in shown.items():
    print(f"{rel}:{n}: {label}: {what}" + (f"  (+{count - 1} more)" if count > 1 else ""))
print(f"\n{len(hits)} thing(s) to look at" if hits else "clean: nothing personal found")
sys.exit(1 if hits else 0)
