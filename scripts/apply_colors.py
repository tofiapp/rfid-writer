#!/usr/bin/env python3
"""Replace hardcoded hex colors in Android XML layouts with @color/ resources."""

import re
import sys

# Context-specific replacements (applied first)
CONTEXT_RULES = [
    (r'android:background="#121212"', 'android:background="@color/bg"'),
    (r'android:backgroundTint="#121212"', 'android:backgroundTint="@color/card"'),
    (r'android:textColor="#121212"', 'android:textColor="@color/text_on_primary"'),
]

# General hex -> color resource mapping (case-insensitive)
HEX_MAP = {
    '#00BCD4': '@color/primary',
    '#1A1A1A': '@color/card',
    '#161b22': '@color/card',
    '#161B22': '@color/card',
    '#1E1E1E': '@color/card',
    '#0d1117': '@color/bg',
    '#0D1117': '@color/bg',
    '#E0E0E0': '@color/text',
    '#888888': '@color/text_muted',
    '#8b949e': '@color/text_muted',
    '#8B949E': '@color/text_muted',
    '#666666': '@color/text_muted',
    '#555555': '@color/text_muted',
    '#4CAF50': '@color/success',
    '#3fb950': '@color/success',
    '#3FB950': '@color/success',
    '#FF9800': '@color/accent',
    '#F44336': '@color/err',
    '#f85149': '@color/error_runtime',
    '#F85149': '@color/error_runtime',
    '#2E2E2E': '@color/step_pending',
    '#21262d': '@color/border',
    '#21262D': '@color/border',
    '#d2a8ff': '@color/vyhybka_accent',
    '#D2A8FF': '@color/vyhybka_accent',
    '#e3b341': '@color/accent_stroke',
    '#E3B341': '@color/accent_stroke',
    '#3d1a1a': '@color/accent_container',
    '#3D1A1A': '@color/accent_container',
    '#2A2A2A': '@color/border',
    '#58a6ff': '@color/primary',
    '#58A6FF': '@color/primary',
    '#1f6feb': '@color/primary',
    '#1F6FEB': '@color/primary',
    '#FFFFFF': '@color/white',
}


def apply_replacements(content: str) -> str:
    for pattern, replacement in CONTEXT_RULES:
        content = re.sub(pattern, replacement, content)

    for hex_val, color_ref in HEX_MAP.items():
        content = content.replace(hex_val, color_ref)

    return content


def main():
    for path in sys.argv[1:]:
        with open(path, 'r', encoding='utf-8') as f:
            original = f.read()
        updated = apply_replacements(original)
        if updated != original:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(updated)
            print(f'Updated: {path}')
        else:
            print(f'No changes: {path}')


if __name__ == '__main__':
    main()
