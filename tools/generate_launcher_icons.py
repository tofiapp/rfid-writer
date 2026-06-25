#!/usr/bin/env python3
"""Generate Android launcher icons from app-icon-source.png.

The in-app header drawable is a direct copy of app-icon-source.png with no
cropping, trimming, or redrawing.
"""

from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app-icon-source.png"
RES_DIR = ROOT / "app/src/main/res"
HEADER_LOGO = RES_DIR / "drawable" / "ic_app_logo.png"

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def background_color(img: Image.Image) -> tuple[int, int, int, int]:
    rgba = img.convert("RGBA")
    corners = [
        rgba.getpixel((0, 0)),
        rgba.getpixel((rgba.width - 1, 0)),
        rgba.getpixel((0, rgba.height - 1)),
        rgba.getpixel((rgba.width - 1, rgba.height - 1)),
    ]
    return max(corners, key=corners.count)


def to_square(img: Image.Image) -> Image.Image:
    rgba = img.convert("RGBA")
    width, height = rgba.size
    if width == height:
        return rgba

    side = max(width, height)
    square = Image.new("RGBA", (side, side), background_color(rgba))
    square.paste(rgba, ((side - width) // 2, (side - height) // 2), rgba)
    return square


def write_mipmaps(source: Image.Image) -> None:
    square = to_square(source)
    for folder, size in MIPMAP_SIZES.items():
        out_dir = RES_DIR / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = square.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(out_dir / "ic_launcher.png")
        resized.save(out_dir / "ic_launcher_round.png")


def write_header_logo() -> None:
    HEADER_LOGO.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(SOURCE, HEADER_LOGO)


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Source image not found: {SOURCE}")

    source = Image.open(SOURCE)
    write_mipmaps(source)
    write_header_logo()
    print(
        f"Generated launcher icons and copied {SOURCE.name} to "
        f"{HEADER_LOGO.relative_to(ROOT)}"
    )


if __name__ == "__main__":
    main()
