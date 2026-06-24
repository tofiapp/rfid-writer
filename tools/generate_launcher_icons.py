#!/usr/bin/env python3
"""Generate Android launcher and in-app logo PNGs from app-icon-source.png."""

from __future__ import annotations

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


def trim_uniform_border(img: Image.Image, tolerance: int = 18) -> Image.Image:
    rgba = img.convert("RGBA")
    bg = background_color(rgba)
    width, height = rgba.size
    pixels = rgba.load()

    def matches_bg(x: int, y: int) -> bool:
        r, g, b, a = pixels[x, y]
        if a < 16:
            return True
        br, bgc, bb, _ = bg
        return abs(r - br) <= tolerance and abs(g - bgc) <= tolerance and abs(b - bb) <= tolerance

    left = 0
    while left < width and all(matches_bg(left, y) for y in range(height)):
        left += 1

    right = width - 1
    while right >= left and all(matches_bg(right, y) for y in range(height)):
        right -= 1

    top = 0
    while top < height and all(matches_bg(x, top) for x in range(left, right + 1)):
        top += 1

    bottom = height - 1
    while bottom >= top and all(matches_bg(x, bottom) for x in range(left, right + 1)):
        bottom -= 1

    if left > right or top > bottom:
        return rgba

    return rgba.crop((left, top, right + 1, bottom + 1))


def write_mipmaps(source: Image.Image) -> None:
    square = to_square(source)
    for folder, size in MIPMAP_SIZES.items():
        out_dir = RES_DIR / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = square.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(out_dir / "ic_launcher.png")
        resized.save(out_dir / "ic_launcher_round.png")


def write_header_logo(source: Image.Image) -> None:
    trimmed = trim_uniform_border(source)
    target_height = 128
    scale = target_height / trimmed.height
    target_width = max(1, round(trimmed.width * scale))
    header = trimmed.resize((target_width, target_height), Image.Resampling.LANCZOS)
    HEADER_LOGO.parent.mkdir(parents=True, exist_ok=True)
    header.save(HEADER_LOGO)


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Source image not found: {SOURCE}")

    source = Image.open(SOURCE)
    write_mipmaps(source)
    write_header_logo(source)
    print(f"Generated launcher icons and {HEADER_LOGO.relative_to(ROOT)} from {SOURCE}")


if __name__ == "__main__":
    main()
