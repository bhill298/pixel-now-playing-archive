#!/usr/bin/env python3
"""Export visible Pixel Now Playing history through ADB UI automation.

This does not access private app data. It reads the accessibility hierarchy,
scrolls the history list, and checkpoints JSON and CSV after every page.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path


PACKAGE = "com.google.android.apps.pixel.nowplaying"
HISTORY_RECYCLER = f"{PACKAGE}:id/history_recycler"
HEADER_ID = f"{PACKAGE}:id/header_title"
TITLE_ID = f"{PACKAGE}:id/media_title"
SUBTITLE_ID = f"{PACKAGE}:id/media_subtitle"
HISTORY_ACTION = f"{PACKAGE}.NOW_PLAYING_HISTORY"


def run_command(command: list[str], timeout: int = 30) -> bytes:
    try:
        completed = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout,
            check=False,
        )
    except FileNotFoundError as error:
        raise RuntimeError(
            "adb was not found. Install Android Platform Tools and add adb to PATH."
        ) from error
    if completed.returncode:
        detail = completed.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(
            f"Command failed ({completed.returncode}): {' '.join(command)}\n{detail}"
        )
    return completed.stdout


def adb(serial: str, *args: str, timeout: int = 30) -> bytes:
    return run_command(["adb", "-s", serial, *args], timeout=timeout)


def connected_devices() -> list[dict[str, str]]:
    payload = run_command(["adb", "devices", "-l"]).decode(
        "utf-8", errors="replace"
    )
    devices: list[dict[str, str]] = []
    for line in payload.splitlines()[1:]:
        fields = line.split()
        if len(fields) < 2 or fields[1] != "device":
            continue
        metadata = {"serial": fields[0]}
        for field in fields[2:]:
            if ":" in field:
                key, value = field.split(":", 1)
                metadata[key] = value.replace("_", " ")
        devices.append(metadata)
    return devices


def choose_device(requested_serial: str | None) -> str:
    print("Connect the source Pixel, enable USB debugging, and approve this computer.")
    while True:
        devices = connected_devices()
        if requested_serial:
            if any(device["serial"] == requested_serial for device in devices):
                return requested_serial
            input(
                f"Device {requested_serial} is not authorized/online. "
                "Connect and unlock it, then press Enter to retry..."
            )
            continue
        if not devices:
            input("No authorized ADB device found. Connect one, then press Enter to retry...")
            continue
        if len(devices) == 1:
            return devices[0]["serial"]

        print("\nAuthorized ADB devices:")
        for index, device in enumerate(devices, 1):
            label = device.get("model", "Unknown model")
            print(f"  {index}. {label} ({device['serial']})")
        selection = input("Select the SOURCE phone by number: ").strip()
        if selection.isdigit() and 1 <= int(selection) <= len(devices):
            return devices[int(selection) - 1]["serial"]
        print("Invalid selection; try again.")


def effective_display_size(serial: str) -> tuple[int, int]:
    payload = adb(serial, "shell", "wm", "size").decode("utf-8", errors="replace")
    sizes = re.findall(r"(?:Physical|Override) size:\s*(\d+)x(\d+)", payload)
    if not sizes:
        raise RuntimeError(f"Could not determine display size from: {payload.strip()}")
    width, height = sizes[-1]
    return int(width), int(height)


def device_time(serial: str) -> datetime:
    try:
        payload = adb(serial, "shell", "date", "+%Y-%m-%dT%H:%M:%S%z").decode().strip()
        return datetime.strptime(payload, "%Y-%m-%dT%H:%M:%S%z")
    except (RuntimeError, ValueError):
        return datetime.now().astimezone()


def stay_awake_setting(serial: str) -> int:
    payload = adb(
        serial, "shell", "settings", "get", "global", "stay_on_while_plugged_in"
    ).decode().strip()
    try:
        return int(payload)
    except ValueError:
        return 0


def set_stay_awake_setting(serial: str, value: int) -> None:
    adb(
        serial,
        "shell",
        "settings",
        "put",
        "global",
        "stay_on_while_plugged_in",
        str(value),
    )


def capture_hierarchy(serial: str) -> ET.Element:
    payload = adb(
        serial, "exec-out", "uiautomator", "dump", "--compressed", "/dev/tty"
    ).decode("utf-8", errors="replace")
    start = payload.find("<?xml")
    end = payload.rfind("</hierarchy>")
    if start < 0 or end < 0:
        raise RuntimeError("The phone did not return a UI hierarchy; is it unlocked?")
    return ET.fromstring(payload[start : end + len("</hierarchy>")])


def find_by_id(root: ET.Element, resource_id: str) -> ET.Element | None:
    return next(
        (node for node in root.iter("node") if node.get("resource-id") == resource_id),
        None,
    )


def descendants_by_id(root: ET.Element, resource_id: str) -> list[ET.Element]:
    return [
        node for node in root.iter("node") if node.get("resource-id") == resource_id
    ]


def parse_page(root: ET.Element) -> list[dict]:
    recycler = find_by_id(root, HISTORY_RECYCLER)
    if recycler is None:
        raise RuntimeError("Now Playing history is not visible on the phone")

    # Rows just above the viewport can remain in the RecyclerView hierarchy even
    # though their header is gone. Leave those dates unknown; overlap matching
    # will retain their already-established dates from the preceding page.
    current_date: str | None = None
    rows: list[dict] = []
    for child in list(recycler):
        headers = descendants_by_id(child, HEADER_ID)
        if headers:
            label = headers[0].get("text", "").strip()
            if label:
                current_date = label

        titles = descendants_by_id(child, TITLE_ID)
        subtitles = descendants_by_id(child, SUBTITLE_ID)
        if not titles or not subtitles:
            continue

        title = titles[0].get("text", "").strip()
        subtitle = subtitles[0].get("text", "").strip()
        if not title or not subtitle:
            continue

        # Artist may be absent, in which case the rendered subtitle begins with
        # the separator itself (for example, "• 20:35").
        match = re.match(r"^(.*?)\s*•\s*(\d{1,2}:\d{2})$", subtitle)
        artist = match.group(1).strip() if match else subtitle
        recognized_time = match.group(2) if match else None
        rows.append(
            {
                "date_label": current_date,
                "time": recognized_time,
                "title": title,
                "artist": artist,
                "subtitle_raw": subtitle,
            }
        )
    return rows


def signature(row: dict) -> tuple:
    return (
        row.get("time"),
        row.get("title"),
        row.get("artist"),
    )


def merge_page(history: list[dict], page: list[dict]) -> tuple[int, int]:
    """Merge an overlapping page while preserving legitimate repeated rows."""
    old = [signature(row) for row in history]
    new = [signature(row) for row in page]
    overlap = 0
    for length in range(min(len(old), len(new)), 0, -1):
        if old[-length:] == new[:length]:
            overlap = length
            break
    last_date = history[-1].get("date_label") if history else None
    additions = page[overlap:]
    for row in additions:
        if row.get("date_label"):
            last_date = row["date_label"]
        else:
            row["date_label"] = last_date
    history.extend(additions)
    return overlap, len(page) - overlap


def normalized_datetime(row: dict, export_day: datetime) -> str | None:
    label = row.get("date_label")
    clock = row.get("time")
    if not label or not clock:
        return None
    if label == "Today":
        day = export_day.date()
    elif label == "Yesterday":
        day = datetime.fromordinal(export_day.date().toordinal() - 1).date()
    else:
        day = None
        for fmt, value in (
            ("%A, %B %d, %Y", label),
            ("%A, %B %d, %Y", f"{label}, {export_day.year}"),
        ):
            try:
                parsed = datetime.strptime(value, fmt)
                candidate = parsed.date()
                if value != label and candidate > export_day.date():
                    candidate = parsed.replace(year=parsed.year - 1).date()
                day = candidate
                break
            except ValueError:
                pass
        if day is None:
            return None
    hour, minute = (int(value) for value in clock.split(":"))
    return datetime.combine(day, datetime.min.time()).replace(
        hour=hour, minute=minute
    ).isoformat(timespec="minutes")


def checkpoint(
    output: Path,
    serial: str,
    model: str,
    history: list[dict],
    export_time: datetime,
    complete: bool,
) -> None:
    entries = []
    for position, row in enumerate(history):
        entry = dict(row)
        entry["recognized_at_local"] = normalized_datetime(row, export_time)
        entry["position_newest_first"] = position
        entries.append(entry)

    document = {
        "schema_version": 1,
        "source": {"serial": serial, "model": model},
        "exported_at_local": export_time.isoformat(timespec="seconds"),
        "complete": complete,
        "entry_count": len(entries),
        "entries": entries,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    json_temp = output.with_suffix(output.suffix + ".tmp")
    json_temp.write_text(
        json.dumps(document, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    json_temp.replace(output)

    csv_path = output.with_suffix(".csv")
    csv_temp = csv_path.with_suffix(csv_path.suffix + ".tmp")
    fields = [
        "position_newest_first",
        "recognized_at_local",
        "date_label",
        "time",
        "title",
        "artist",
        "subtitle_raw",
    ]
    with csv_temp.open("w", newline="", encoding="utf-8-sig") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(entries)
    csv_temp.replace(csv_path)


def wait_for_history(serial: str) -> None:
    print("\nOn the SOURCE Pixel:")
    print("  1. Unlock the screen.")
    print("  2. Open the Now Playing app and select History.")
    print("  3. Leave the phone connected and in portrait orientation.")
    while True:
        input("Press Enter when the History screen is visible...")
        try:
            page = parse_page(capture_hierarchy(serial))
            if page:
                return
            print("No song rows are visible. Open History and try again.")
        except (RuntimeError, ET.ParseError) as error:
            print(f"Not ready: {error}")


def restart_history_at_top(serial: str) -> list[dict]:
    """Restart the UI activity so RecyclerView state begins at the newest row."""
    print("Opening History at the newest entry...", flush=True)
    adb(serial, "shell", "am", "force-stop", PACKAGE)
    adb(serial, "shell", "am", "start", "-a", HISTORY_ACTION)
    time.sleep(2)
    page = parse_page(capture_hierarchy(serial))
    if not page:
        raise RuntimeError("History reopened, but no song rows were readable")
    return page


def validate_history(history: list[dict], export_time: datetime) -> list[str]:
    warnings: list[str] = []
    normalized = [normalized_datetime(row, export_time) for row in history]
    missing = sum(value is None for value in normalized)
    if missing:
        warnings.append(f"{missing} entries have an unrecognized date/time format")
    out_of_order = sum(
        current is not None and previous is not None and current > previous
        for previous, current in zip(normalized, normalized[1:])
    )
    if out_of_order:
        warnings.append(f"{out_of_order} adjacent entries are not newest-to-oldest")
    return warnings


def export_history(
    serial: str,
    model: str,
    output: Path,
    export_time: datetime,
    width: int,
    height: int,
    max_scrolls: int,
    settle_seconds: float,
) -> int:
    history: list[dict] = []
    unchanged_pages = 0

    # These reproduce the tested gesture as proportions of the effective display.
    x = round(width * 0.50)
    start_y = round(height * 0.77)
    end_y = round(height * 0.42)
    if start_y <= end_y:
        raise RuntimeError(f"Invalid display geometry: {width}x{height}")

    first_page = restart_history_at_top(serial)
    for page_number in range(max_scrolls + 1):
        page = first_page if page_number == 0 else parse_page(capture_hierarchy(serial))
        if not page:
            raise RuntimeError("No song rows were readable on the current screen")

        overlap, added = merge_page(history, page)
        if page_number > 0 and overlap == 0:
            checkpoint(output, serial, model, history, export_time, complete=False)
            raise RuntimeError(
                "A scroll produced no overlapping rows. The incomplete checkpoint was "
                "saved; do not trust it as a complete export."
            )
        unchanged_pages = unchanged_pages + 1 if added == 0 else 0
        checkpoint(output, serial, model, history, export_time, complete=False)
        print(
            f"Page {page_number + 1}: visible={len(page)}, overlap={overlap}, "
            f"added={added}, total={len(history)}",
            flush=True,
        )

        if unchanged_pages >= 3:
            checkpoint(output, serial, model, history, export_time, complete=True)
            print(f"Reached the bottom. Exported {len(history)} entries.", flush=True)
            warnings = validate_history(history, export_time)
            for warning in warnings:
                print(f"WARNING: {warning}", flush=True)
            return 0
        if page_number >= max_scrolls:
            print("Stopped at --max-scrolls; checkpoint is incomplete.", flush=True)
            return 2

        adb(
            serial,
            "shell",
            "input",
            "swipe",
            str(x),
            str(start_y),
            str(x),
            str(end_y),
            "350",
        )
        time.sleep(settle_seconds)

    return 2


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Export Pixel Now Playing history through ADB UI automation."
    )
    parser.add_argument(
        "--serial", help="ADB serial of the source phone; prompts when omitted"
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="JSON output path; CSV is written beside it (default: timestamped)",
    )
    parser.add_argument("--max-scrolls", type=int, default=10000)
    parser.add_argument("--settle-seconds", type=float, default=0.4)
    args = parser.parse_args()

    if shutil.which("adb") is None:
        raise RuntimeError(
            "adb was not found. Install Android Platform Tools and add adb to PATH."
        )

    serial = choose_device(args.serial)
    model = adb(serial, "shell", "getprop", "ro.product.model").decode().strip()
    width, height = effective_display_size(serial)
    export_time = device_time(serial)
    output = args.output or Path(
        f"now-playing-export-{export_time.strftime('%Y%m%d-%H%M%S')}.json"
    )
    if output.suffix.lower() != ".json":
        output = output.with_suffix(".json")

    original_stay_awake = stay_awake_setting(serial)
    usb_stay_awake_bit = 2
    changed_stay_awake = not (original_stay_awake & usb_stay_awake_bit)

    print(f"\nSource: {model} ({serial})")
    print(f"Effective display: {width}x{height}")
    print(f"Output: {output.resolve()}")

    try:
        if changed_stay_awake:
            set_stay_awake_setting(
                serial, original_stay_awake | usb_stay_awake_bit
            )
            print("Temporarily enabled stay-awake while connected over USB.")
        else:
            print("USB stay-awake was already enabled; leaving it unchanged.")

        wait_for_history(serial)
        print("\nKeep the source phone unlocked and untouched during export.")
        return export_history(
            serial=serial,
            model=model,
            output=output,
            export_time=export_time,
            width=width,
            height=height,
            max_scrolls=args.max_scrolls,
            settle_seconds=args.settle_seconds,
        )
    finally:
        if changed_stay_awake:
            try:
                set_stay_awake_setting(serial, original_stay_awake)
                print(
                    f"Restored stay-awake setting to {original_stay_awake}.",
                    flush=True,
                )
            except Exception as error:  # Restoration must not hide the export error.
                print(f"WARNING: Could not restore stay-awake setting: {error}")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\nInterrupted. The latest checkpoint remains on disk.", file=sys.stderr)
        raise SystemExit(130)
    except (RuntimeError, subprocess.TimeoutExpired, ET.ParseError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
