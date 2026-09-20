#!/usr/bin/env python3
"""
Standalone TaskLens Archive Validator

Validates .tasklens archive files against the canonical schema specifications.
Has ZERO dependencies on Android, iOS, or Kotlin SDK code.
Uses only standard Python 3 libraries: zipfile, json, sys, hashlib, os.
"""

import sys
import os
import json
import zipfile
import hashlib

REQUIRED_ENTRIES = [
    "manifest.json",
    "task.json",
    "attempts.json",
    "timeline.json",
    "diagnosis.json",
    "events.json",
    "device.json",
    "README.html"
]

def validate_tasklens_archive(archive_path: str) -> bool:
    print(f"==================================================")
    print(f"Validating TaskLens Archive: {archive_path}")
    print(f"==================================================")

    if not os.path.isfile(archive_path):
        print(f"[-] ERROR: File does not exist: {archive_path}")
        return False

    file_size = os.path.getsize(archive_path)
    if file_size == 0:
        print(f"[-] ERROR: Archive is empty (0 bytes)")
        return False

    with open(archive_path, "rb") as f:
        sha256 = hashlib.sha256(f.read()).hexdigest()
    print(f"[+] Archive Size: {file_size} bytes")
    print(f"[+] SHA-256: {sha256}")

    try:
        zf = zipfile.ZipFile(archive_path, "r")
    except zipfile.BadZipFile as e:
        print(f"[-] ERROR: Not a valid ZIP file: {e}")
        return False

    entries = zf.namelist()
    print(f"[+] Found {len(entries)} archive entries: {entries}")

    # 1. Check required entries
    missing = [req for req in REQUIRED_ENTRIES if req not in entries]
    if missing:
        print(f"[-] ERROR: Missing required archive entries: {missing}")
        return False
    print(f"[+] All {len(REQUIRED_ENTRIES)} canonical archive entries are present.")

    # 2. Validate manifest.json
    try:
        manifest_data = json.loads(zf.read("manifest.json").decode("utf-8"))
    except Exception as e:
        print(f"[-] ERROR: manifest.json is not valid JSON: {e}")
        return False

    if manifest_data.get("schema_version") != 1:
        print(f"[-] ERROR: Unsupported schema_version in manifest.json: {manifest_data.get('schema_version')}")
        return False

    task_id = manifest_data.get("task_id")
    platform = manifest_data.get("platform")
    app_version = manifest_data.get("app_version")
    exported_at = manifest_data.get("exported_at_epoch_ms")

    if not task_id:
        print(f"[-] ERROR: manifest.json missing 'task_id'")
        return False
    if not platform:
        print(f"[-] ERROR: manifest.json missing 'platform'")
        return False

    print(f"[+] manifest.json verified:")
    print(f"    - Schema Version: {manifest_data.get('schema_version')}")
    print(f"    - Task ID: {task_id}")
    print(f"    - Platform: {platform}")
    print(f"    - App Version: {app_version}")
    print(f"    - Exported At: {exported_at}")

    # 3. Validate task.json
    try:
        task_data = json.loads(zf.read("task.json").decode("utf-8"))
        if task_data is not None:
            if "id" in task_data and task_data["id"] != task_id:
                print(f"[-] ERROR: task.json id '{task_data['id']}' does not match manifest task_id '{task_id}'")
                return False
            print(f"[+] task.json verified (Task: {task_data.get('name', 'unnamed')}, Scheduler: {task_data.get('scheduler')})")
        else:
            print(f"[+] task.json is null (task not found in store, valid scenario)")
    except Exception as e:
        print(f"[-] ERROR: task.json is invalid JSON: {e}")
        return False

    # 4. Validate attempts.json
    try:
        attempts_data = json.loads(zf.read("attempts.json").decode("utf-8"))
        if not isinstance(attempts_data, list):
            print(f"[-] ERROR: attempts.json must be a JSON array")
            return False
        for att in attempts_data:
            if "attemptId" not in att and "attempt_id" not in att and "id" not in att:
                print(f"[-] ERROR: attempt entry missing attempt identifier: {att}")
                return False
        print(f"[+] attempts.json verified ({len(attempts_data)} attempts recorded)")
    except Exception as e:
        print(f"[-] ERROR: attempts.json is invalid JSON: {e}")
        return False

    # 5. Validate events.json
    try:
        events_data = json.loads(zf.read("events.json").decode("utf-8"))
        if not isinstance(events_data, list):
            print(f"[-] ERROR: events.json must be a JSON array")
            return False
        prev_seq = -1
        for evt in events_data:
            if "id" not in evt:
                print(f"[-] ERROR: event missing 'id': {evt}")
                return False
            seq = evt.get("sequenceNumber", evt.get("sequence_number", 0))
            # Attributes must be string map
            attrs = evt.get("attributes", {})
            for k, v in attrs.items():
                if not isinstance(v, str):
                    print(f"[-] ERROR: Event attribute '{k}' value must be String, got {type(v)}: {v}")
                    return False
        print(f"[+] events.json verified ({len(events_data)} events recorded, attribute types validated)")
    except Exception as e:
        print(f"[-] ERROR: events.json is invalid JSON: {e}")
        return False

    # 6. Validate timeline.json
    try:
        timeline_data = json.loads(zf.read("timeline.json").decode("utf-8"))
        items = timeline_data.get("items", []) if isinstance(timeline_data, dict) else timeline_data
        if not isinstance(items, list):
            print(f"[-] ERROR: timeline.json items must be a JSON array")
            return False
        print(f"[+] timeline.json verified ({len(items)} timeline items recorded)")
    except Exception as e:
        print(f"[-] ERROR: timeline.json is invalid JSON: {e}")
        return False

    # 7. Validate diagnosis.json
    try:
        diag_data = json.loads(zf.read("diagnosis.json").decode("utf-8"))
        if not isinstance(diag_data, list):
            print(f"[-] ERROR: diagnosis.json must be a JSON array")
            return False
        for d in diag_data:
            if "title" not in d or "classification" not in d:
                print(f"[-] ERROR: diagnosis item missing title/classification: {d}")
                return False
        print(f"[+] diagnosis.json verified ({len(diag_data)} diagnoses recorded)")
    except Exception as e:
        print(f"[-] ERROR: diagnosis.json is invalid JSON: {e}")
        return False

    # 8. Validate device.json
    try:
        device_data = json.loads(zf.read("device.json").decode("utf-8"))
        if not isinstance(device_data, dict):
            print(f"[-] ERROR: device.json must be a JSON object")
            return False
        print(f"[+] device.json verified ({len(device_data)} device properties recorded: {list(device_data.keys())})")
    except Exception as e:
        print(f"[-] ERROR: device.json is invalid JSON: {e}")
        return False

    # 9. Validate README.html
    try:
        readme_content = zf.read("README.html").decode("utf-8")
        if len(readme_content) < 50 or "TaskLens" not in readme_content:
            print(f"[-] ERROR: README.html is missing expected content")
            return False
        print(f"[+] README.html verified ({len(readme_content)} characters)")
    except Exception as e:
        print(f"[-] ERROR: README.html could not be read: {e}")
        return False

    print(f"[✓] ARCHIVE PORTABILITY VALIDATION PASSED: {archive_path}")
    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <path_to_tasklens_file>")
        sys.exit(1)

    all_passed = True
    for path in sys.argv[1:]:
        if not validate_tasklens_archive(path):
            all_passed = False

    sys.exit(0 if all_passed else 1)
