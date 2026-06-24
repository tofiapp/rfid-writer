#!/usr/bin/env python3
"""Generate Android launcher and in-app logo assets from app-logo.svg."""

from __future__ import annotations

import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app-logo.svg"
RES_DIR = ROOT / "app/src/main/res"
HEADER_LOGO = RES_DIR / "drawable" / "ic_app_logo.png"
PREVIEW_PNG = ROOT / "app-icon-source.png"

MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

HEADER_HEIGHT = 128
PREVIEW_SIZE = 1024


def require_rsvg_convert() -> str:
    binary = shutil.which("rsvg-convert")
    if binary is None:
        raise SystemExit(
            "rsvg-convert not found. Install librsvg2-bin and rerun this script."
        )
    return binary


def read_background_color(svg_path: Path) -> str:
    try:
        root = ET.parse(svg_path).getroot()
    except ET.ParseError:
        return "none"

    style = root.attrib.get("style", "")
    for chunk in style.split(";"):
        chunk = chunk.strip()
        if chunk.startswith("background-color:"):
            return chunk.split(":", 1)[1].strip()

    return root.attrib.get("data-background-color", "none")


def render_svg(
    rsvg: str,
    svg_path: Path,
    output: Path,
    *,
    width: int,
    height: int,
    background: str,
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    command = [
        rsvg,
        str(svg_path),
        "-o",
        str(output),
        "-f",
        "png",
        "-w",
        str(width),
        "-h",
        str(height),
        "-a",
        "-b",
        background,
    ]
    subprocess.run(command, check=True)


def write_mipmaps(rsvg: str, svg_path: Path, background: str) -> None:
    for folder, size in MIPMAP_SIZES.items():
        out_dir = RES_DIR / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            render_svg(
                rsvg,
                svg_path,
                out_dir / name,
                width=size,
                height=size,
                background=background,
            )


def write_header_logo(rsvg: str, svg_path: Path, background: str) -> None:
    view_box = read_view_box(svg_path)
    if view_box is None:
        width = HEADER_HEIGHT * 4
    else:
        _, _, vb_width, vb_height = view_box
        width = max(1, round(HEADER_HEIGHT * (vb_width / vb_height)))

    render_svg(
        rsvg,
        svg_path,
        HEADER_LOGO,
        width=width,
        height=HEADER_HEIGHT,
        background="none",
    )


def write_preview_png(rsvg: str, svg_path: Path, background: str) -> None:
    render_svg(
        rsvg,
        svg_path,
        PREVIEW_PNG,
        width=PREVIEW_SIZE,
        height=PREVIEW_SIZE,
        background=background,
    )


def read_view_box(svg_path: Path) -> tuple[float, float, float, float] | None:
    try:
        root = ET.parse(svg_path).getroot()
    except ET.ParseError:
        return None

    view_box = root.attrib.get("viewBox")
    if not view_box:
        return None

    parts = [float(value) for value in view_box.replace(",", " ").split()]
    if len(parts) != 4:
        return None
    return parts[0], parts[1], parts[2], parts[3]


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(
            f"Source SVG not found: {SOURCE}\n"
            "Place your logo file at app-logo.svg in the repository root."
        )

    rsvg = require_rsvg_convert()
    background = read_background_color(SOURCE)

    write_mipmaps(rsvg, SOURCE, background)
    write_header_logo(rsvg, SOURCE, background)
    write_preview_png(rsvg, SOURCE, background)

    print(f"Generated launcher icons, header logo, and preview PNG from {SOURCE}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        raise SystemExit(error) from error
