#!/usr/bin/env python3
"""Builds catalog.json: direct download links for every ArtCraft Android app.

Runs in GitHub Actions (see .github/workflows/update-catalog.yml) with the workflow's token, so
the phones never have to call the rate-limited GitHub API. They read the committed file from
raw.githubusercontent.com instead. Standard library only.

  python3 tools/update_catalog.py            # writes catalog.json in the repo root
  GITHUB_TOKEN=... python3 tools/update_catalog.py
"""
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone

API = "https://api.github.com"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "catalog.json")
SOURCES = os.path.join(ROOT, "tools", "sources.json")
TOKEN = os.environ.get("GITHUB_TOKEN", "")


def get(url, accept="application/vnd.github+json"):
    req = urllib.request.Request(url, headers={"Accept": accept, "User-Agent": "artcraft-launcher-catalog"})
    if TOKEN and url.startswith(API):
        req.add_header("Authorization", f"Bearer {TOKEN}")
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def api_json(path):
    try:
        return json.loads(get(API + path))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


def sha256_of(url):
    """Only used when GitHub doesn't publish a digest for the asset."""
    h = hashlib.sha256()
    req = urllib.request.Request(url, headers={"User-Agent": "artcraft-launcher-catalog"})
    with urllib.request.urlopen(req, timeout=300) as r:
        while chunk := r.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def load_previous():
    try:
        with open(OUT, encoding="utf-8") as f:
            old = json.load(f)
        return {(a["repo"], x["url"]): x.get("sha256") for a in old.get("apps", []) for x in a.get("assets", [])}
    except (OSError, ValueError, KeyError):
        return {}


def pick_release(releases, include_pre):
    """Newest non-draft release that has an APK; else the newest non-draft one."""
    fallback = None
    for r in releases or []:
        if r.get("draft") or (r.get("prerelease") and not include_pre):
            continue
        if any(a["name"].lower().endswith(".apk") for a in r.get("assets", [])):
            return r
        fallback = fallback or r
    return fallback


def entry(repo, meta, previous):
    releases = api_json(f"/repos/{repo}/releases?per_page=10")
    r = pick_release(releases, include_pre=True)
    app = {"repo": repo, "name": meta.get("name", ""), "description": meta.get("description", "")}
    if not r:
        return app
    assets = []
    for a in r.get("assets", []):
        if not a["name"].lower().endswith(".apk"):
            continue
        url = a["browser_download_url"]
        digest = a.get("digest") or ""
        sha = digest[7:] if digest.startswith("sha256:") else previous.get((repo, url))
        if not sha:
            print(f"hashing {a['name']} ({a.get('size', 0) // (1 << 20)} MB)", file=sys.stderr)
            sha = sha256_of(url)
        assets.append({"name": a["name"], "url": url, "size": a.get("size", 0), "sha256": sha})
    app.update({
        "tag": r["tag_name"],
        "title": r.get("name") or "",
        "prerelease": bool(r.get("prerelease")),
        "published": r.get("published_at") or "",
        "page": r["html_url"],
        "notes": (r.get("body") or "")[:4000],
        "assets": assets,
    })
    return app


def main():
    with open(SOURCES, encoding="utf-8") as f:
        src = json.load(f)
    repos = {}
    for org in src.get("orgs", []):
        for r in api_json(f"/orgs/{org}/repos?per_page=100&type=public") or []:
            if not r["archived"] and not r["fork"]:
                repos[r["full_name"]] = {"name": "", "description": r.get("description") or ""}
    for r in src.get("repos", []):
        repos.setdefault(r, {"name": "", "description": ""})

    previous = load_previous()
    apps = [entry(repo, repos[repo], previous) for repo in sorted(repos)]

    catalog = {"schema": 1, "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "apps": apps}
    # Keep the file stable when nothing changed, so the workflow doesn't commit noise.
    try:
        with open(OUT, encoding="utf-8") as f:
            old = json.load(f)
        if old.get("apps") == apps:
            print("catalog unchanged")
            return
    except (OSError, ValueError):
        pass
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"wrote {OUT} with {len(apps)} repos")


if __name__ == "__main__":
    main()
