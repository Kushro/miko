# MIKO — Chapter bookmark SQL check: runs the real 50.sqm DDL + the Moments query against an in-memory
# SQLite with a minimal chapters/mangas schema. Usage: python3 scripts/checks/chapter-bookmarks.py <50.sqm> <chapter_bookmark_types.sq>
import sqlite3, re, sys
mig = open(sys.argv[1], encoding="utf-8").read()
q = open(sys.argv[2], encoding="utf-8").read()
db = sqlite3.connect(":memory:")
db.execute("PRAGMA foreign_keys=ON")
db.executescript("""
CREATE TABLE mangas(_id INTEGER PRIMARY KEY, title TEXT, source INTEGER, favorite INTEGER, thumbnail_url TEXT, cover_last_modified INTEGER, version INTEGER DEFAULT 0, is_syncing INTEGER DEFAULT 0);
CREATE TABLE chapters(_id INTEGER PRIMARY KEY, manga_id INTEGER REFERENCES mangas(_id) ON DELETE CASCADE, name TEXT, chapter_number REAL, scanlator TEXT, read INTEGER DEFAULT 0, bookmark INTEGER DEFAULT 0, date_upload INTEGER DEFAULT 0);
""")
# apply the DDL exactly as in 50.sqm (strip the -- MIKO markers/comments)
ddl = "\n".join(l for l in mig.splitlines() if not l.strip().startswith("--"))
db.executescript(ddl)
db.executescript("""
INSERT INTO mangas VALUES (1,'beta',1,1,NULL,0,0,0),(2,'Alpha',1,1,'x',0,0,0);
INSERT INTO chapters(_id,manga_id,name,chapter_number,bookmark) VALUES (10,1,'c1',1,1),(11,1,'c2',2,1),(12,1,'c3',3,0),(20,2,'a1',1,1);
INSERT OR REPLACE INTO chapter_bookmark_types VALUES (10,1,0),(11,3,0),(20,2,0);
""")
def types(): return db.execute("SELECT chapter_id,type FROM chapter_bookmark_types ORDER BY chapter_id").fetchall()
print("initial types", types())
# 1) re-marking a bookmarked chapter (bookmark=1 -> 1) must NOT clear the type
db.execute("UPDATE chapters SET bookmark=1 WHERE _id=10"); assert (10,1) in types(), "type lost on re-bookmark"
# 2) unrelated update must not clear
db.execute("UPDATE chapters SET read=1 WHERE _id=10"); assert (10,1) in types()
# 3) un-bookmark clears the type
db.execute("UPDATE chapters SET bookmark=0 WHERE _id=10"); assert (10,1) not in types(), "type NOT cleared on unbookmark"
print("after unbookmark 10", types())
# 4) deleting chapter cascades
db.execute("DELETE FROM chapters WHERE _id=11"); assert (11,3) not in types(), "cascade failed"
# 5) favorites query from the .sq
m = re.search(r"getBookmarkedChaptersWithRelations:\n(.*?);", q, re.S); sql = m.group(1)
rows = db.execute(sql).fetchall()
print("favorites rows", rows)
assert [r[0] for r in rows] == [20], f"expected only chapter 20 bookmarked, got {rows}"
assert rows[0][-1] == 2, "COALESCE type mismatch"
db.execute("UPDATE chapters SET bookmark=1 WHERE _id=12")
rows = db.execute(sql).fetchall()
print("after bookmarking 12 (no type row):", [(r[0], r[7], r[-1]) for r in rows])
assert [r[0] for r in rows] == [20,12] and rows[1][-1] == 0, "ordering (title NOCASE asc) or generic COALESCE=0 failed"
print("ALL SQL E2E CHECKS PASSED")
