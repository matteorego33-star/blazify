#!/usr/bin/env python3
"""Copies a GitHub release of Blazify to Codeberg, the backup update source.

The app asks GitHub first. When GitHub cannot answer, it reads
rajendrapandey.info.np/blazify/home.json, which names Codeberg's release API,
so a release that exists on both is found either way. Run this after each
GitHub release:

    scripts/publish_backup.py v9.17.0

The tag must already be pushed to Codeberg. Notes and APKs are taken from the
GitHub release itself, so both say the same thing. The token is read from
~/.config/blazify-codeberg-token and never printed.
"""
import json
import os
import sys
import urllib.request
from pathlib import Path

GITHUB = "https://api.github.com/repos/rajendra7169/blazify"
CODEBERG = "https://codeberg.org/api/v1/repos/" + os.environ.get("CODEBERG_REPO", "rajendra7169/blazify")
TOKEN = Path("~/.config/blazify-codeberg-token").expanduser().read_text().strip()


def call(url, data=None, method=None, headers=None, raw=False):
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    if url.startswith("https://codeberg.org"):
        req.add_header("Authorization", f"token {TOKEN}")
    with urllib.request.urlopen(req, timeout=600) as r:
        body = r.read()
    return body if raw else json.loads(body or b"null")


def main(tag):
    gh = call(f"{GITHUB}/releases/tags/{tag}")
    apks = [a for a in gh["assets"] if a["name"].endswith(".apk")]
    print(f"{tag}: {len(apks)} APKs on GitHub")

    try:
        rel = call(f"{CODEBERG}/releases/tags/{tag}")
        print("Codeberg release exists, adding what is missing")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
        rel = call(f"{CODEBERG}/releases", method="POST",
                   headers={"Content-Type": "application/json"},
                   data=json.dumps({"tag_name": tag, "name": gh["name"] or tag,
                                    "body": gh["body"] or "", "prerelease": gh["prerelease"]}).encode())
        print("Codeberg release created")

    have = {a["name"]: a["size"] for a in rel.get("assets", [])}
    for a in apks:
        if have.get(a["name"]) == a["size"]:
            print(f"  {a['name']}: already there")
            continue
        blob = call(a["browser_download_url"], raw=True)
        if len(blob) != a["size"]:
            sys.exit(f"  {a['name']}: downloaded {len(blob)} bytes, GitHub says {a['size']}")
        boundary = "blazify" + os.urandom(8).hex()
        form = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"attachment\"; "
                f"filename=\"{a['name']}\"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n"
                ).encode() + blob + f"\r\n--{boundary}--\r\n".encode()
        call(f"{CODEBERG}/releases/{rel['id']}/assets?name={a['name']}", data=form, method="POST",
             headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
        print(f"  {a['name']}: uploaded ({a['size'] // 1_000_000} MB)")

    latest = call(f"{CODEBERG}/releases/latest")
    print(f"Codeberg's latest is now {latest['tag_name']}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
