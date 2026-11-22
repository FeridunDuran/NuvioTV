import argparse
import hashlib
import io
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageStat

parser = argparse.ArgumentParser(description="Switch between three cards in a loaded Modern Home row and compare Hero artwork.")
parser.add_argument("--adb", default=os.environ.get("ADB", "adb"))
parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
parser.add_argument("--package", default="com.nuvio.tv.turbo")
parser.add_argument("--row", required=True, help="Case-insensitive substring of the visible catalog title")
args = parser.parse_args()
adb = [args.adb] + (["-s", args.serial] if args.serial else [])
pkg = args.package

def shell(*args):
    return subprocess.check_output([*adb, "shell", *args], text=True, timeout=35)

def bounds(node):
    return tuple(map(int,re.findall(r"\d+",node.get("bounds",""))))

def hierarchy():
    remote = f"/data/local/tmp/nuvio-hero-test-{os.getpid()}.xml"
    last_error = None
    for _ in range(3):
        try:
            shell("uiautomator", "dump", remote)
            text = shell("cat", remote)
            return ET.fromstring(text).findall(".//node")
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ET.ParseError) as error:
            last_error = error
            time.sleep(2)
        finally:
            shell("rm", "-f", remote)
    raise RuntimeError("Android UI hierarchy was unavailable after three attempts") from last_error

def selection():
    nodes=hierarchy()
    focused=next((n for n in nodes if n.get("focused")=="true"),None)
    target_row=next((n for n in nodes if args.row.casefold() in n.get("text","").casefold()),None)
    if focused is None or target_row is None:
        return False, None, None, None
    fx, fy, _, fb = bounds(focused)
    wx, _, _, wb = bounds(target_row)
    card = -16 <= fy - wb < 155 and fx >= wx-20 and fb-fy >= 90
    content="|".join(value for node in focused.iter("node")
                     for value in (node.get("text",""),node.get("content-desc","")) if value)
    return card,hashlib.sha256(content.encode()).hexdigest()[:10],fx,fy-wb

def crop_artwork():
    data=subprocess.check_output([*adb,"exec-out","screencap","-p"],timeout=20)
    image=Image.open(io.BytesIO(data)).convert("RGB")
    w,h=image.size
    crop=image.crop((round(w*.63),round(h*.045),round(w*.96),round(h*.43)))
    return crop.resize((120,80))

if re.search(r"state=PlaybackState\s*\{state=(3|4|5|6),",shell("dumpsys","media_session")):
    raise SystemExit("Playback active. Hero test postponed.")
if pkg not in shell("dumpsys","window","windows").split("mCurrentFocus=")[-1].splitlines()[0]:
    raise SystemExit("Nuvio must be in the foreground.")
for trial in range(14):
    on_target_row, card, x, vertical_gap=selection()
    if on_target_row:
        break
    direction = "19" if (vertical_gap is not None and vertical_gap >= 155) or (vertical_gap is None and trial < 4) else "20"
    shell("input","keyevent",direction)
    time.sleep(1)
else:
    raise SystemExit("Catalog row not located.")
print("Catalog focus located", flush=True)
prior=None
for step in range(3):
    time.sleep(7)
    on_target_row, card, x, _=selection()
    if not on_target_row:
        raise SystemExit("Focus moved away from the catalog.")
    crop=crop_artwork()
    diff=round(sum(ImageStat.Stat(ImageChops.difference(prior,crop)).mean)/3,1) if prior else None
    print("Card",step+1,card,"left",x,"artworkDelta",diff,"luma",round(ImageStat.Stat(crop.convert("L")).mean[0],1),flush=True)
    prior=crop
    if step < 2:
        shell("input", "keyevent", "22")
