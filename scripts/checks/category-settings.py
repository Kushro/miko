# MIKO — Category settings SQL check: runs the real 51.sqm DDL + the category_library_settings.sq queries
# against an in-memory SQLite with a minimal categories schema.
# Usage: python3 scripts/checks/category-settings.py <51.sqm> <category_library_settings.sq>
import re
import sqlite3
import sys

mig = open(sys.argv[1], encoding="utf-8").read()
q = open(sys.argv[2], encoding="utf-8").read()

# 1) the migration DDL must be byte-identical to the CREATE TABLE block of the .sq
ddl_mig = "\n".join(l for l in mig.splitlines() if not l.strip().startswith("--")).strip()
ddl_sq = re.search(r"(CREATE TABLE category_library_settings\(.*?\);)", q, re.S).group(1).strip()
assert ddl_mig == ddl_sq, "51.sqm DDL differs from category_library_settings.sq"

db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
db.executescript("""
CREATE TABLE categories(_id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, sort INTEGER NOT NULL, flags INTEGER NOT NULL, manga_order TEXT NOT NULL, hidden INTEGER NOT NULL DEFAULT 0);
INSERT OR IGNORE INTO categories(_id, name, sort, flags, manga_order, hidden) VALUES (0, "", -1, 0, "", 0);
INSERT INTO categories VALUES (1,'Reading',0,0,'',0),(2,'Plan',1,0,'',0);
""")
db.executescript(ddl_mig)


def named(name):
    m = re.search(name + r":\n(.*?);", q, re.S)
    return m.group(1)


upsert = named("upsert").replace(":categoryId", "?").replace(":settings", "?").replace(":updatedAt", "?")
get = named("get").replace(":categoryId", "?")
delete = named("delete").replace(":categoryId", "?")
get_all = named("getAll")

# 2) upsert + get + replace
db.execute(upsert, (1, '{"filters":{"unread":"ENABLED_IS"}}', 10))
assert db.execute(get, (1,)).fetchone()[0].startswith('{"filters"')
db.execute(upsert, (1, '{"sort":"LAST_READ,DESC"}', 11))
assert db.execute(get, (1,)).fetchone()[0] == '{"sort":"LAST_READ,DESC"}', "INSERT OR REPLACE did not replace"
assert db.execute("SELECT COUNT(*) FROM category_library_settings").fetchone()[0] == 1

# 3) default category (id 0) may be special too (FK to the system row)
db.execute(upsert, (0, "{}", 12))
assert sorted(r[0] for r in db.execute(get_all).fetchall()) == [0, 1]

# 4) unknown category is rejected by the FK
try:
    db.execute(upsert, (99, "{}", 13))
    raise AssertionError("FK not enforced")
except sqlite3.IntegrityError:
    pass

# 5) deleting the category cascades
db.execute(upsert, (2, "{}", 14))
db.execute("DELETE FROM categories WHERE _id=2")
assert db.execute(get, (2,)).fetchone() is None, "cascade failed"

# 6) explicit delete = category follows global again
db.execute(delete, (1,))
assert db.execute(get, (1,)).fetchone() is None
print("ALL CATEGORY SETTINGS SQL CHECKS PASSED")
