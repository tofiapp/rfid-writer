#!/usr/bin/env python3
"""Generate Android launcher PNGs from a single source image."""

from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BRAND_SCRIPT = ROOT / "tools/generate_brand_assets.py"


def main() -> None:
    result = subprocess.run([sys.executable, str(BRAND_SCRIPT)], check=False)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
