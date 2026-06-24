#!/usr/bin/env python3
"""Generate RFID Writer brand assets (launcher icon + in-app header logo)."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES_DIR = ROOT / "app/src/main/res"

BG = "#E6E8EB"
RFID_COLOR = "#006292"
WRITER_COLOR = "#F05A14"

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

ICON_SIZE = 1024
CORNER_RADIUS = 180


def rounded_square(size: int, radius: int, fill: str) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=fill)
    return img


def draw_logo_text(
    canvas: Image.Image,
    *,
    rfid_size: int,
    writer_size: int,
    line_gap: int,
) -> None:
    draw = ImageDraw.Draw(canvas)
    rfid_font = ImageFont.truetype(FONT_BOLD, rfid_size)
    writer_font = ImageFont.truetype(FONT_REGULAR, writer_size)

    rfid_text = "RFID"
    writer_text = "Writer"

    rfid_bbox = draw.textbbox((0, 0), rfid_text, font=rfid_font)
    writer_bbox = draw.textbbox((0, 0), writer_text, font=writer_font)

    rfid_w = rfid_bbox[2] - rfid_bbox[0]
    rfid_h = rfid_bbox[3] - rfid_bbox[1]
    writer_w = writer_bbox[2] - writer_bbox[0]
    writer_h = writer_bbox[3] - writer_bbox[1]

    block_h = rfid_h + line_gap + writer_h
    y = (canvas.height - block_h) // 2

    draw.text(
        ((canvas.width - rfid_w) // 2, y),
        rfid_text,
        font=rfid_font,
        fill=RFID_COLOR,
    )
    draw.text(
        ((canvas.width - writer_w) // 2, y + rfid_h + line_gap),
        writer_text,
        font=writer_font,
        fill=WRITER_COLOR,
    )


def create_launcher_icon() -> Image.Image:
    icon = rounded_square(ICON_SIZE, CORNER_RADIUS, BG)
    draw_logo_text(icon, rfid_size=250, writer_size=210, line_gap=8)
    return icon


def create_header_logo() -> Image.Image:
    width, height = 520, 120
    logo = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw_logo_text(logo, rfid_size=58, writer_size=48, line_gap=2)
    bbox = logo.getbbox()
    if bbox:
        logo = logo.crop(bbox)
    return logo


def write_mipmaps(source: Image.Image) -> None:
    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in sizes.items():
        out_dir = RES_DIR / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        resized = source.resize((size, size), Image.Resampling.LANCZOS)
        resized.save(out_dir / "ic_launcher.png")
        resized.save(out_dir / "ic_launcher_round.png")


def main() -> None:
    launcher = create_launcher_icon()
    launcher.save(ROOT / "app-icon-source.png")

    drawable_dir = RES_DIR / "drawable"
    drawable_dir.mkdir(parents=True, exist_ok=True)
    create_header_logo().save(drawable_dir / "ic_app_logo.png")

    write_mipmaps(launcher)
    print("Generated app-icon-source.png, ic_app_logo.png, and launcher mipmaps")


if __name__ == "__main__":
    main()
