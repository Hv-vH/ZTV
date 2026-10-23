"""Build static, renamed Source Han subsets for Android 23+.

Requires fonttools. Source fonts come from the upstream URLs in brand-spec.md.
Usage: python prepare-tv-fonts.py SOURCE_HAN_VARIABLE_TTF OUTPUT_DIRECTORY
"""
import sys
from pathlib import Path
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

source, destination = Path(sys.argv[1]), Path(sys.argv[2])
destination.mkdir(parents=True, exist_ok=True)
characters = set(range(0x20, 0x7f))
for lead in range(0xa1, 0xf8):
    for trail in range(0xa1, 0xff):
        try:
            characters.update(map(ord, bytes([lead, trail]).decode("gb2312")))
        except UnicodeDecodeError:
            pass
characters.update(range(0x2000, 0x2070))
characters.update(range(0x3000, 0x3040))
characters.update(range(0xff00, 0xfff0))

for weight, style in [(400, "Regular"), (500, "Medium")]:
    font = instantiateVariableFont(TTFont(source), {"wght": weight}, inplace=True)
    options = subset.Options()
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    options.layout_features = ["*"]
    worker = subset.Subsetter(options)
    worker.populate(unicodes=characters)
    worker.subset(font)
    names = {1: "ZTV Han", 2: style, 3: "ZTV Han 2026 " + style,
             4: "ZTV Han " + style, 6: "ZTVHan-" + style,
             16: "ZTV Han", 17: style}
    for record in font["name"].names:
        if record.nameID in names:
            record.string = names[record.nameID].encode(record.getEncoding())
    for name_id, value in names.items():
        font["name"].setName(value, name_id, 3, 1, 0x409)
    target = destination / ("ztv_han_" + style.lower() + ".ttf")
    font.save(target)
    print(target, target.stat().st_size, "bytes")
