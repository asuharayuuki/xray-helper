"""Update the bundled Google fonts. Run with Python and fonttools installed."""

from pathlib import Path
from tempfile import TemporaryDirectory
from urllib.parse import quote
from urllib.request import urlretrieve
import shutil

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


REVISION = "10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7"
UPSTREAM = f"https://raw.githubusercontent.com/google/material-design-icons/{REVISION}"
MAIN = Path(__file__).resolve().parents[1] / "app/src/main"
FONTS = MAIN / "res/font"
CATALOG = MAIN / "assets/material_icons"


def download(source, target):
    urlretrieve(f"{UPSTREAM}/{quote(source)}", target)


def codepoints(path):
    # Google lists one classic name twice. Keep its first mapping.
    entries = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        name, code = line.split()
        entries.setdefault(name, int(code, 16))
    return entries


def check_glyphs(font, entries):
    missing = set(entries.values()) - font.getBestCmap().keys()
    if missing:
        raise ValueError(f"Missing code points: {sorted(missing)}")


def update(downloads):
    FONTS.mkdir(parents=True, exist_ok=True)
    CATALOG.mkdir(parents=True, exist_ok=True)
    download("LICENSE", CATALOG / "LICENSE")

    symbol_entries = None
    for style in ("Outlined", "Rounded", "Sharp"):
        stem = f"MaterialSymbols{style}[FILL,GRAD,opsz,wght]"
        source = downloads / f"{stem}.ttf"
        names = source.with_suffix(".codepoints")
        download(f"variablefont/{source.name}", source)
        download(f"variablefont/{names.name}", names)
        entries = codepoints(names)
        if symbol_entries is None:
            symbol_entries = entries
            shutil.copyfile(names, CATALOG / "symbols.codepoints")
        elif entries != symbol_entries:
            raise ValueError("The Material Symbols styles no longer share a code point map")

        for fill in (0, 1):
            with TTFont(source, recalcTimestamp=False) as font:
                # Bake the picker styles to keep every glyph with a much smaller APK.
                instantiateVariableFont(
                    font, {"FILL": fill, "GRAD": 0, "opsz": 24, "wght": 400},
                    inplace=True,
                )
                check_glyphs(font, entries)
                suffix = "_filled" if fill else ""
                font.save(FONTS / f"symbols_{style.lower()}{suffix}.ttf")

    for style, suffix in (("", "filled"), ("Outlined", "outlined"), ("Round", "rounded"),
                          ("Sharp", "sharp"), ("TwoTone", "two_tone")):
        stem = f"MaterialIcons{style}-Regular"
        extension = "otf" if style else "ttf"
        font_path = FONTS / f"icons_{suffix}.{extension}"
        names = CATALOG / f"icons_{suffix}.codepoints"
        download(f"font/{stem}.{extension}", font_path)
        download(f"font/{stem}.codepoints", names)
        with TTFont(font_path) as font:
            check_glyphs(font, codepoints(names))


if __name__ == "__main__":
    with TemporaryDirectory() as temp:
        update(Path(temp))
