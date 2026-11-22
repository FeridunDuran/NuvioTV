import argparse
import json
import os
import re
import subprocess
import time

parser = argparse.ArgumentParser(description="Measure cold Home content readiness on an Android TV.")
parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
parser.add_argument("--package", default="com.nuvio.tv.turbo")
parser.add_argument("--observe", type=int, default=45)
parser.add_argument("--runs", type=int, default=1)
args = parser.parse_args()
if args.observe <= 0 or args.runs <= 0:
    parser.error("--observe and --runs must be positive")

adb = [args.adb] + (["-s", args.serial] if args.serial else [])
pkg = args.package

def shell(*args):
    return subprocess.check_output([*adb, "shell", *args], text=True, timeout=60)

def prop(value):
    shell(f'setprop log.tag.TurboPerf "{value}"')

def require_idle_tv():
    if re.search(r"state=PlaybackState\s*\{state=(3|4|5|6),", shell("dumpsys", "media_session")):
        raise SystemExit("Playback active. Measurement postponed.")


def measure():
    require_idle_tv()
    shell("am", "force-stop", pkg)
    launch = shell("am", "start", "-W", "-n", f"{pkg}/com.nuvio.tv.launcher.AppIconDefault")
    if "Status: ok" not in launch:
        raise RuntimeError("Nuvio Home did not open")
    time.sleep(args.observe)
    pid = shell("pidof", pkg).strip().split()[0]
    logs = shell("logcat", "-d", f"--pid={pid}", "-v", "monotonic", "-s", "TurboPerf:D", "HomeViewModel:D")
    starts = re.findall(r"Loading home catalog addonId=([^ ]+)", logs)
    errors = re.findall(r"Home catalog failed[^\r\n]*?code=(\w+)", logs)
    start = None
    responses = []
    hero = []
    selected = []
    requests = []
    cache_previews = []
    publications = []
    for line in logs.splitlines():
        timestamp = re.match(r"\[?\s*(\d+\.\d+)", line)
        if not timestamp:
            continue
        seconds = float(timestamp[1])
        if "event=process_start" in line:
            start = seconds
        if "Home catalog loaded " in line:
            responses.append(seconds)
        if "event=hero_artwork_selected" in line:
            selected.append(seconds)
        if "event=hero_artwork_request" in line:
            requests.append(seconds)
        if "event=home_catalog_cache_preview" in line:
            cache_previews.append(seconds)
        if "event=hero_backdrop_loaded" in line:
            hero.append(seconds)
        if marker := re.search(r"event=home_rows_published_(\d+)", line):
            publications.append((seconds, int(marker[1])))

    def elapsed(seconds):
        return round(seconds - start, 2) if start is not None else None

    launch_time = re.search(r"TotalTime: (\d+)", launch)
    return {
        "activity_window_ms": int(launch_time[1]) if launch_time else None,
        "catalog_response_s": [elapsed(seconds) for seconds in responses],
        "published_rows": [{"seconds": elapsed(seconds), "count": count} for seconds, count in publications],
        "hero_selected_s": [elapsed(seconds) for seconds in selected],
        "hero_request_s": [elapsed(seconds) for seconds in requests],
        "hero_artwork_s": [elapsed(seconds) for seconds in hero],
        "catalog_cache_preview_s": [elapsed(seconds) for seconds in cache_previews],
        "addons_in_first_three_requests": len(set(starts[:3])),
        "catalog_error_codes": errors,
    }


require_idle_tv()
original = shell("getprop", "log.tag.TurboPerf").strip()
try:
    prop("DEBUG")
    for _ in range(args.runs):
        print(json.dumps(measure()), flush=True)
finally:
    prop(original)
