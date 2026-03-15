#!/usr/bin/env python3
"""
Upload videos from .valid/videos to the gabon-service platform.

Flow:
  1. Login to get JWT token (round-robin across accounts)
  2. Call /api/videos/upload-url to get a presigned S3 PUT URL
  3. PUT the file directly to S3 using the presigned URL
  4. Call /api/videos/confirm-upload to save metadata and trigger transcoding
"""

import os
import re
import sys
import time
import random
import mimetypes
import traceback
import subprocess

import requests

# ─────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────
BASE_URL = "http://95.40.34.158:51012/service"

ACCOUNTS = [
    {"username": "zhangsan", "password": "12345678"},
    {"username": "lisi",     "password": "12345678"},
    {"username": "wangwu",   "password": "12345678"},
    {"username": "zhaoliu",  "password": "12345678"},
    {"username": "qianqi",   "password": "12345678"},
    {"username": "sunba",    "password": "12345678"},
]

VIDEO_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".valid", "videos")

S3_ACCESS_KEY   = "CHANGE_ME_ACCESS_KEY"
S3_SECRET_KEY   = "CHANGE_ME_SECRET_KEY"
S3_REGION       = "ap-east-1"
S3_BUCKET       = "aitools888"

UPLOAD_TIMEOUT  = 600   # seconds for a single S3 PUT (large files may be slow)
REQUEST_TIMEOUT = 30    # seconds for API calls


# ─────────────────────────────────────────────
# Helpers
# ─────────────────────────────────────────────

def login(account: dict) -> str:
    """Login and return the JWT token string."""
    url = f"{BASE_URL}/api/auth/login"
    resp = requests.post(url, json=account, timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    body = resp.json()
    # API returns code=0 for success
    if body.get("code") != 0 or not body.get("data", {}).get("token"):
        raise RuntimeError(f"Login failed for {account['username']}: {body}")
    token = body["data"]["token"]
    print(f"  ✓ Logged in as {account['username']}")
    return token


def get_upload_url(token: str, filename: str, content_type: str) -> dict:
    """Request a presigned S3 upload URL from the service."""
    url = f"{BASE_URL}/api/videos/upload-url"
    headers = {"Authorization": f"Bearer {token}"}
    payload = {"fileName": filename, "contentType": content_type}
    resp = requests.post(url, json=payload, headers=headers, timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    body = resp.json()
    if body.get("code") != 0:
        raise RuntimeError(f"get_upload_url failed: {body}")
    return body["data"]  # {uploadUrl, fileUrl, s3Key}


def upload_to_s3_presigned(upload_url: str, filepath: str, content_type: str):
    """PUT the file to S3 using the presigned URL."""
    file_size = os.path.getsize(filepath)
    print(f"  Uploading {os.path.basename(filepath)} ({file_size / 1024 / 1024:.1f} MB) to S3 …")
    with open(filepath, "rb") as f:
        resp = requests.put(
            upload_url,
            data=f,
            headers={
                "Content-Type": content_type,
                "Content-Length": str(file_size),
            },
            timeout=UPLOAD_TIMEOUT,
        )
    if resp.status_code not in (200, 204):
        raise RuntimeError(f"S3 PUT failed: {resp.status_code} {resp.text[:500]}")
    print(f"  ✓ S3 upload done (status {resp.status_code})")


def get_video_duration(filepath: str) -> int:
    """Attempt to get video duration using ffprobe. Fallback to 0 if not available."""
    try:
        result = subprocess.run(
            [
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                filepath
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=5
        )
        if result.returncode == 0:
            return max(1, int(float(result.stdout.strip())))
    except Exception:
        pass
    return 0


def confirm_upload(token: str, s3_key: str, filename: str, file_size: int,
                   mime_type: str, title: str, tags: list[str], duration: int) -> dict:
    """Confirm the upload and persist metadata to the service."""
    url = f"{BASE_URL}/api/videos/confirm-upload"
    headers = {"Authorization": f"Bearer {token}"}
    payload = {
        "s3Key":    s3_key,
        "fileName": filename,
        "fileSize": file_size,
        "mimeType": mime_type,
        "title":    title,
        "tags":     tags[:3],  # API allows max 3 tags
        "duration": duration,
    }
    resp = requests.post(url, json=payload, headers=headers, timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    body = resp.json()
    if body.get("code") != 0:
        raise RuntimeError(f"confirm_upload failed: {body}")
    return body["data"]


def parse_tags_from_filename(filename: str) -> tuple[str, list[str]]:
    """
    Parse hashtag-style tags embedded in the filename.
    Example: "金发美女 #清纯 #室内.mp4" → title="金发美女", tags=["清纯", "室内"]
    """
    stem = os.path.splitext(filename)[0]
    tags = re.findall(r"#(\S+?)(?=\s*#|\s*$)", stem)
    # Remove the hashtag portions from the title
    title = re.sub(r"\s*#\S+", "", stem).strip()
    if not title:
        title = stem
    return title, tags


def get_content_type(filepath: str) -> str:
    mime, _ = mimetypes.guess_type(filepath)
    return mime or "video/mp4"


# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────

def main():
    # Discover video files
    if not os.path.isdir(VIDEO_DIR):
        print(f"ERROR: Video directory not found: {VIDEO_DIR}", file=sys.stderr)
        sys.exit(1)

    video_files = sorted(
        f for f in os.listdir(VIDEO_DIR)
        if f.lower().endswith((".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv"))
    )
    if not video_files:
        print(f"No video files found in {VIDEO_DIR}", file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(video_files)} video(s) in {VIDEO_DIR}\n")

    # Shuffle accounts for randomness
    accounts = ACCOUNTS.copy()
    random.shuffle(accounts)

    success_count = 0
    fail_count = 0

    for idx, filename in enumerate(video_files):
        filepath = os.path.join(VIDEO_DIR, filename)
        title, tags = parse_tags_from_filename(filename)
        content_type = get_content_type(filepath)
        file_size = os.path.getsize(filepath)
        duration = get_video_duration(filepath)
        account = accounts[idx % len(accounts)]

        print(f"\n[{idx + 1}/{len(video_files)}] {filename}")
        print(f"  Title : {title}")
        print(f"  Tags  : {tags}")
        print(f"  Size  : {file_size / 1024 / 1024:.1f} MB")
        print(f"  Duration: {duration}s")
        print(f"  Account: {account['username']}")

        try:
            # Step 1 – Login
            token = login(account)

            # Step 2 – Get presigned upload URL
            upload_info = get_upload_url(token, filename, content_type)
            upload_url = upload_info["uploadUrl"]
            s3_key     = upload_info["s3Key"]
            print(f"  S3 key: {s3_key}")

            # Step 3 – Upload file directly to S3
            upload_to_s3_presigned(upload_url, filepath, content_type)

            # Step 4 – Confirm upload
            result = confirm_upload(
                token=token,
                s3_key=s3_key,
                filename=filename,
                file_size=file_size,
                mime_type=content_type,
                title=title,
                tags=tags,
                duration=duration,
            )
            print(f"  ✓ Confirmed! Video ID: {result.get('id')}, Status: {result.get('status')}")
            success_count += 1

        except Exception as exc:
            print(f"  ✗ FAILED: {exc}")
            traceback.print_exc()
            fail_count += 1

        # Small delay between uploads to avoid hammering the server
        if idx < len(video_files) - 1:
            time.sleep(1)

    print(f"\n{'='*50}")
    print(f"Upload complete: {success_count} succeeded, {fail_count} failed")
    print(f"{'='*50}")


if __name__ == "__main__":
    main()
