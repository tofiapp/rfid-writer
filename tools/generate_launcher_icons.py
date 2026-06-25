#!/usr/bin/env python3
"""Generate Android launcher icons and in-app header logo from app-icon-source.png.

The source PNG may contain outer white padding from export. This script trims that
padding, builds a square launcher icon on the brand background, and creates a
transparent header logo with just the wordmark.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app-icon-source.png"
RES_DIR = ROOT / "app/src/main/res"
HEADER_LOGO = RES_DIR / "drawable" / "ic_app_logo.png"

BRAND_BG = (230, 232, 235, 255)  # #E6E8EB
WHITE_THRESHOLD = 250
GRAY_BG_RANGE = (210, 245)

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

FOREGROUND_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

HEADER_HEIGHT = 128


def is_near_white(pixel: tuple[int, ...]) -> bool:
    return pixel[0] >= WHITE_THRESHOLD and pixel[1] >= WHITE_THRESHOLD and pixel[2] >= WHITE_THRESHOLD


def is_icon_background(pixel: tuple[int, ...]) -> bool:
    lo, hi = GRAY_BG_RANGE
    return lo <= pixel[0] <= hi and lo <= pixel[1] <= hi and lo <= pixel[2] <= hi


def trim_outer_padding(img: Image.Image) -> Image.Image:
    rgba = img.convert("RGBA")
    pixels = rgba.load()
    width, height = rgba.size

    min_x, min_y = width, height
    max_x, max_y = 0, 0
    found = False

    for y in range(height):
        for x in range(width):
            if not is_near_white(pixels[x, y]):
                found = True
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)

    if not found:
        return rgba

    return rgba.crop((min_x, min_y, max_x + 1, max_y + 1))


def to_brand_square(img: Image.Image) -> Image.Image:
    rgba = img.convert("RGBA")
    width, height = rgba.size
    side = max(width, height)
    square = Image.new("RGBA", (side, side), BRAND_BG)
    square.paste(rgba, ((side - width) // 2, (side - height) // 2), rgba)
    return square


def extract_wordmark(img: Image.Image) -> Image.Image:
    rgba = img.convert("RGBA")
    pixels = rgba.load()
    width, height = rgba.size
    output = Image.new("RGBA", (width, height), (0, 0, 0, 0))

    min_x, min_y = width, height
    max_x, max_y = 0, 0
    found = False

    for y in range(height):
        for x in range(width):
            pixel = pixels[x, y]
            if is_near_white(pixel) or is_icon_background(pixel):
                continue
            output.putpixel((x, y), pixel)
            found = True
            min_x = min(min_x, x)
            min_y = min(min_y, y)
            max_x = max(max_x, x)
            max_y = max(max_y, y)

    if not found:
        return rgba

    cropped = output.crop((min_x, min_y, max_x + 1, max_y + 1))
    scale = HEADER_HEIGHT / cropped.height
    resized = cropped.resize(
        (max(1, round(cropped.width * scale)), HEADER_HEIGHT),
        Image.Resampling.LANCZOS,
    )
    return resized


def center_on_canvas(img: Image.Image, size: int) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    width, height = img.size
    canvas.paste(img, ((size - width) // 2, (size - height) // 2), img)
    return canvas


def write_mipmaps(source: Image.Image) -> None:
    square = to_brand_square(source)
    for folder, size in MIPMAP_SIZES.items():
        out_dir = RES_DIR / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = square.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(out_dir / "ic_launcher.png")
        resized.save(out_dir / "ic_launcher_round.png")


def write_foreground_mipmaps(wordmark: Image.Image) -> None:
    for folder, size in FOREGROUND_SIZES.items():
        out_dir = RES_DIR / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        inset = round(size * 0.12)
        target = size - inset * 2
        scale = min(target / wordmark.width, target / wordmark.height)
        resized = wordmark.resize(
            (max(1, round(wordmark.width * scale)), max(1, round(wordmark.height * scale))),
            Image.Resampling.LANCZOS,
        )
        centered = center_on_canvas(resized, size)
        centered.save(out_dir / "ic_launcher_foreground.png")


def write_header_logo(trimmed: Image.Image) -> None:
    HEADER_LOGO.parent.mkdir(parents=True, exist_ok=True)
    extract_wordmark(trimmed).save(HEADER_LOGO)


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Source image not found: {SOURCE}")

    source = Image.open(SOURCE)
    trimmed = trim_outer_padding(source)
    wordmark = extract_wordmark(trimmed)

    write_mipmaps(trimmed)
    write_foreground_mipmaps(wordmark)
    write_header_logo(trimmed)

    print(
        f"Generated launcher icons from trimmed {trimmed.size[0]}x{trimmed.size[1]} source "
        f"and header logo at {HEADER_LOGO.relative_to(ROOT)}"
    )


if __name__ == "__main__":
    main()
