#!/usr/bin/env python3
"""Generate Android launcher PNGs from a single source image."""

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app-icon-source.png"
RES_DIR = ROOT / "app/src/main/res"

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Source image not found: {SOURCE}")

    img = Image.open(SOURCE).convert("RGBA")
    width, height = img.size
    if width != height:
        side = max(width, height)
        square = Image.new("RGBA", (side, side), (0, 0, 0, 255))
        square.paste(img, ((side - width) // 2, (side - height) // 2))
        img = square

    for folder, size in SIZES.items():
        out_dir = RES_DIR / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = img.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(out_dir / "ic_launcher.png")
        resized.save(out_dir / "ic_launcher_round.png")

    print(f"Generated launcher icons from {SOURCE}")


if __name__ == "__main__":
    main()
