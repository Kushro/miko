# MIKO — Page bookmark SQL check: migrates page_bookmarks 49 → 52 → 53 → 54 against an in-memory SQLite and runs
# the page_bookmarks.sq queries on the result, so the column appended by 53.sqm lines up with what
# the .sq declares (the repository mappers are positional).
# Usage: python3 scripts/checks/page-bookmarks.py <49.sqm> <52.sqm> <53.sqm> <54.sqm> <page_bookmarks.sq>
import re
import sqlite3
import sys

if len(sys.argv) != 6:
    sys.exit("Usage: page-bookmarks.py <49.sqm> <52.sqm> <53.sqm> <54.sqm> <page_bookmarks.sq>")
mig49, mig52, mig53, mig54, q = (open(p, encoding="utf-8").read() for p in sys.argv[1:])


def ddl(mig):
    return "\n".join(l for l in mig.splitlines() if not l.strip().startswith("--")).strip()


# 1) 53.sqm is one ALTER TABLE appending focus_fraction REAL, and the .sq CREATE TABLE lists it last
assert ddl(mig53) == "ALTER TABLE page_bookmarks ADD COLUMN focus_fraction REAL;", "53.sqm DDL drifted"
create = re.search(r"CREATE TABLE page_bookmarks\((.*?)\n\);", q, re.S).group(1)
cols = [
    l.strip().split()[0]
    for l in create.splitlines()
    if l.strip() and not l.strip().startswith(("FOREIGN", "ON "))
]
assert cols[-2:] == ["scroll_fraction", "focus_fraction"], cols

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
db.executescript(
    """
CREATE TABLE mangas(_id INTEGER NOT NULL PRIMARY KEY, source INTEGER NOT NULL, title TEXT NOT NULL,
  thumbnail_url TEXT, favorite INTEGER NOT NULL, cover_last_modified INTEGER NOT NULL);
CREATE TABLE chapters(_id INTEGER NOT NULL PRIMARY KEY, manga_id INTEGER NOT NULL, name TEXT NOT NULL,
  chapter_number REAL NOT NULL);
INSERT INTO mangas VALUES (1, 7, 'Manga', NULL, 1, 0);
INSERT INTO chapters VALUES (10, 1, 'Ch. 1', 1.0);
"""
)
# 2) the real migration chain for this table
db.executescript(ddl(mig49))
db.executescript(ddl(mig52))
db.executescript(ddl(mig53))
db.executescript(ddl(mig54))
migrated = [r[1] for r in db.execute("PRAGMA table_info(page_bookmarks)")]
assert migrated == cols, f"migrated columns {migrated} != .sq columns {cols}"


def named(name):
    return re.search(name + r":\n(.*?);", q, re.S).group(1)


insert = re.sub(r":\w+", "?", named("insert"))
db.execute(insert, (1, 10, 3, "https://img/3.jpg", None, 1000, 0.25, 0.62))
db.execute(insert, (1, 10, 4, None, "note", 1001, None, None))

# 3) every SELECT returns the two fractions in the last two positions of the bookmark columns
row = db.execute(re.sub(r":\w+", "?", named("get")), (10, 3)).fetchone()
assert row[-2:] == (0.25, 0.62), row
rows = db.execute(named("getAllWithRelations")).fetchall()
assert [r[3] for r in rows] == [4, 3], "newest first"
assert rows[1][7] == 0.25 and rows[1][8] == 0.62 and rows[1][9] == "Manga", rows[1]
assert rows[0][7] is None and rows[0][8] is None
by_chapter = db.execute(re.sub(r":\w+", "?", named("getByChapterId")), (10,)).fetchall()
assert [r[3] for r in by_chapter] == [3, 4] and by_chapter[0][8] == 0.62

# 4) updateImageUrl re-points the thumbnail key
db.execute(re.sub(r":\w+", "?", named("updateImageUrl")), ("https://img/new.jpg", 1))
assert db.execute("SELECT image_url FROM page_bookmarks WHERE _id=1").fetchone()[0] == "https://img/new.jpg"

# 5) unique (chapter, page) still enforced; cascade still works
try:
    db.execute(insert, (1, 10, 3, None, None, 1002, None, None))
    raise AssertionError("unique index not enforced")
except sqlite3.IntegrityError:
    pass
db.execute("DELETE FROM chapters WHERE _id=10")
assert db.execute("SELECT COUNT(*) FROM page_bookmarks").fetchone()[0] == 0, "cascade failed"
print("PAGE BOOKMARK SQL OK")
