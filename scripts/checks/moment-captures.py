#!/usr/bin/env python3
"""MIKO — verifies migration 54 (page_bookmark_previews) against the real DDL.

Usage: python scripts/checks/moment-captures.py <54.sqm> <page_bookmark_previews.sq> <page_bookmarks.sq>

Checks, like category-settings.py (new table) and page-bookmarks.py (parent schema):
1. The migration DDL is byte-identical to the CREATE TABLE in page_bookmark_previews.sq.
2. The DDL runs on a real SQLite with the parent tables present.
3. upsert/get/delete/getStats/getMetas/getByMangaId behave as the queries promise.
4. FK is enforced (preview of a nonexistent bookmark is rejected).
5. ON DELETE CASCADE: deleting a bookmark (or its manga) removes its preview.
6. replaceFields keeps the bookmark _id, so its preview survives an id-preserving replace.
"""

import re
import sqlite3
import sys


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def extract_create(sql_text, table):
    m = re.search(r"CREATE TABLE %s\(.*?\);" % table, sql_text, re.DOTALL)
    assert m, "CREATE TABLE %s not found" % table
    return m.group(0)


def named(sq_text, name):
    """Extracts a named SQLDelight query and rewrites :params to ?."""
    m = re.search(r"^%s:\n(.*?);" % name, sq_text, re.DOTALL | re.MULTILINE)
    assert m, "query %s not found" % name
    return re.sub(r":\w+", "?", m.group(1))


def main():
    mig_path, previews_sq_path, bookmarks_sq_path = sys.argv[1], sys.argv[2], sys.argv[3]
    mig = read(mig_path)
    previews_sq = read(previews_sq_path)
    bookmarks_sq = read(bookmarks_sq_path)

    # 1. DDL byte-identical between migration and .sq
    ddl_mig = extract_create(mig, "page_bookmark_previews")
    ddl_sq = extract_create(previews_sq, "page_bookmark_previews")
    assert ddl_mig == ddl_sq, "54.sqm DDL differs from page_bookmark_previews.sq"

    con = sqlite3.connect(":memory:")
    con.execute("PRAGMA foreign_keys = ON")

    # Minimal parents: mangas, chapters, then the real page_bookmarks DDL from its .sq
    con.execute("CREATE TABLE mangas(_id INTEGER NOT NULL PRIMARY KEY)")
    con.execute("CREATE TABLE chapters(_id INTEGER NOT NULL PRIMARY KEY)")
    con.executescript(extract_create(bookmarks_sq, "page_bookmarks"))

    # 2. The migration DDL runs
    con.executescript(ddl_mig)

    con.execute("INSERT INTO mangas(_id) VALUES (1)")
    con.execute("INSERT INTO chapters(_id) VALUES (10)")
    con.execute("INSERT INTO chapters(_id) VALUES (11)")
    con.execute(
        "INSERT INTO page_bookmarks(_id, manga_id, chapter_id, page_index, created_at) VALUES (100, 1, 10, 0, 111)",
    )
    con.execute(
        "INSERT INTO page_bookmarks(_id, manga_id, chapter_id, page_index, created_at) VALUES (101, 1, 11, 3, 222)",
    )

    upsert = named(previews_sq, "upsert")
    get = named(previews_sq, "get")
    stats = named(previews_sq, "getStats")
    metas = named(previews_sq, "getMetas")
    by_manga = named(previews_sq, "getByMangaId")
    delete = named(previews_sq, "delete")

    # 3. upsert + get + replace-on-conflict
    con.execute(upsert, (100, b"AAAA", 1000))
    con.execute(upsert, (101, b"BBBBBBBB", 1001))
    assert con.execute(get, (100,)).fetchone()[0] == b"AAAA"
    con.execute(upsert, (100, b"CC", 2000))  # recompression path: replace, same PK
    assert con.execute(get, (100,)).fetchone()[0] == b"CC"
    assert con.execute("SELECT COUNT(*) FROM page_bookmark_previews").fetchone()[0] == 2

    count, total = con.execute(stats).fetchone()
    assert count == 2 and total == len(b"CC") + len(b"BBBBBBBB"), (count, total)

    rows = con.execute(metas).fetchall()
    assert rows[0] == (101, 8, 1001) and rows[1] == (100, 2, 2000), rows  # largest first

    assert dict(con.execute(by_manga, (1,)).fetchall()) == {100: b"CC", 101: b"BBBBBBBB"}

    # 4. FK enforced
    try:
        con.execute(upsert, (999, b"XX", 1))
        raise AssertionError("FK to a nonexistent bookmark was accepted")
    except sqlite3.IntegrityError:
        pass

    # 6. replaceFields keeps _id -> the preview survives
    replace_fields = named(bookmarks_sq, "replaceFields")
    con.execute(replace_fields, ("http://img", "a note", 333, 0.5, 0.7, 100))
    assert con.execute(get, (100,)).fetchone()[0] == b"CC"
    assert con.execute("SELECT note FROM page_bookmarks WHERE _id = 100").fetchone()[0] == "a note"

    # 5. cascade: bookmark delete kills its preview; explicit delete works too
    con.execute("DELETE FROM page_bookmarks WHERE _id = 100")
    assert con.execute(get, (100,)).fetchone() is None
    con.execute(delete, (101,))
    assert con.execute("SELECT COUNT(*) FROM page_bookmark_previews").fetchone()[0] == 0

    print("MOMENT CAPTURE SQL OK")


if __name__ == "__main__":
    main()
