import argparse
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description="Time a movie's Play button to the first source-list pre-draw on Nuvio TV.")
parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
parser.add_argument("--movie", required=True, help="Canonical IMDb movie ID")
parser.add_argument("--play-label", action="append", default=["play", "oynat", "watch now"])
parser.add_argument("--prewarm", choices=("on", "off"), required=True)
args = parser.parse_args()
if not re.fullmatch(r"tt[0-9]+", args.movie):
    parser.error("--movie requires an IMDb movie ID")
adb = [args.adb] + (["-s", args.serial] if args.serial else [])
app = "com.nuvio.tv.turbo"
remote_xml = f"/data/local/tmp/nuvio-source-ready-{os.getpid()}.xml"


def shell(*command):
    return subprocess.check_output([*adb, "shell", *command], text=True, timeout=30)


def playback_active():
    return re.search(r"state=PlaybackState\s*\{state=(3|4|5|6),", shell("dumpsys", "media_session")) is not None


def play_button():
    labels = {label.casefold().strip() for label in args.play_label}
    try:
        shell("uiautomator", "dump", remote_xml)
        root = ET.fromstring(shell("cat", remote_xml))
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError):
        return None
    finally:
        shell("rm", "-f", remote_xml)
    for node in root.iter("node"):
        if node.get("package") != app:
            continue
        if any(node.get(key, "").casefold().strip() in labels for key in ("text", "content-desc")):
            match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if match:
                left, top, right, bottom = map(int, match.groups())
                if right > left and bottom > top:
                    return (left + right) // 2, (top + bottom) // 2
    return None


def read_markers(pid):
    logs = shell("logcat", "-d", f"--pid={pid}", "-s", "TurboPerf:I")
    return {name: int(time_ms) for name, time_ms in re.findall(
        r"event=(movie_play_clicked|stream_first_sources_predraw) uptimeMs=(\d+)", logs
    )}


def measure():
    shell("am", "force-stop", app)
    shell("am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", f"nuvio://movie/{args.movie}", "-p", app)
    deadline = time.monotonic() + 65
    button = None
    while time.monotonic() < deadline:
        button = play_button()
        if button:
            break
        time.sleep(2)
    if not button:
        return {"prewarm": args.prewarm, "status": "play_button_unavailable"}
    pid = shell("pidof", app).strip().split()[0]
    shell("input", "tap", str(button[0]), str(button[1]))
    deadline = time.monotonic() + 35
    while time.monotonic() < deadline:
        markers = read_markers(pid)
        clicked = markers.get("movie_play_clicked")
        first = markers.get("stream_first_sources_predraw")
        if clicked is not None and first is not None and first >= clicked:
            return {"prewarm": args.prewarm, "status": "ok", "play_to_first_source_predraw_ms": first - clicked}
        if playback_active():
            return {"prewarm": args.prewarm, "status": "source_list_bypassed_by_playback"}
        time.sleep(2)
    return {"prewarm": args.prewarm, "status": "no_source_marker"}


if playback_active():
    raise SystemExit("Playback active. Measurement postponed.")
tags = ("log.tag.TurboPerf", "log.tag.NuvioPrewarm")
original = {name: shell("getprop", name).strip() for name in tags}
try:
    shell("setprop log.tag.TurboPerf DEBUG")
    shell("setprop", "log.tag.NuvioPrewarm", "DEBUG" if args.prewarm == "on" else "INFO")
    print(json.dumps(measure()), flush=True)
finally:
    for name, value in original.items():
        shell(f'setprop {name} "{value}"')
