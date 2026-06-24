#!/usr/bin/env python3
"""Update hardcoded colors in MainActivity.java to use RfidGo palette via colorRes(R.color.*)."""

import re

REPLACEMENTS = [
    # Settings toggle
    ('visible ? "#888888" : "#00BCD4"', 'visible ? colorRes(R.color.text_muted) : colorRes(R.color.primary)'),
    # Scan buttons
    ('Color.parseColor("#F44336")', 'colorRes(R.color.err)'),
    ('Color.parseColor("#00BCD4")', 'colorRes(R.color.primary)'),
    ('Color.parseColor("#2E2E2E")', 'colorRes(R.color.step_pending)'),
    ('Color.parseColor("#4CAF50")', 'colorRes(R.color.success)'),
    ('Color.parseColor("#FF9800")', 'colorRes(R.color.accent)'),
    ('Color.parseColor("#888888")', 'colorRes(R.color.text_muted)'),
    ('Color.parseColor("#121212")', 'colorRes(R.color.text_on_primary)'),
    ('Color.parseColor("#E0E0E0")', 'colorRes(R.color.text)'),
    ('Color.parseColor("#d2a8ff")', 'colorRes(R.color.vyhybka_accent)'),
    ('Color.parseColor("#e3b341")', 'colorRes(R.color.accent_stroke)'),
    ('Color.parseColor("#2a1015")', 'colorRes(R.color.dup_row_bg)'),
    ('Color.parseColor("#0d1117")', 'colorRes(R.color.card)'),
    ('Color.parseColor("#21262d")', 'colorRes(R.color.border)'),
    ('Color.parseColor("#0d2b0d")', 'colorRes(R.color.snackbar_success_bg)'),
    ('Color.parseColor("#2b0d0d")', 'colorRes(R.color.snackbar_error_bg)'),
    ('Color.parseColor("#0d1e2b")', 'colorRes(R.color.snackbar_info_bg)'),
    # setStatus hex strings
    ('setStatus("● Připojeno — Chainway C5", "#4CAF50")', 'setStatus("● Připojeno — Chainway C5", colorRes(R.color.success))'),
    ('setStatus("● Chyba inicializace", "#F44336")', 'setStatus("● Chyba inicializace", colorRes(R.color.err))'),
    ('setStatus("● SDK chyba: " + e.getMessage(), "#F44336")', 'setStatus("● SDK chyba: " + e.getMessage(), colorRes(R.color.err))'),
    ('setStatus("● Skenování...", "#00BCD4")', 'setStatus("● Skenování...", colorRes(R.color.primary))'),
    ('setStatus("● Ověřuji tag...", "#00BCD4")', 'setStatus("● Ověřuji tag...", colorRes(R.color.primary))'),
    ('setStatus("● Zapisuji " + bankName + "...", "#FF9800")', 'setStatus("● Zapisuji " + bankName + "...", colorRes(R.color.accent))'),
    ('setStatus("● " + bankName + " zapsáno OK", "#4CAF50")', 'setStatus("● " + bankName + " zapsáno OK", colorRes(R.color.success))'),
    ('setStatus("● Zápis " + bankName + " selhal", "#F44336")', 'setStatus("● Zápis " + bankName + " selhal", colorRes(R.color.error_runtime))'),
    ('setStatus("● Skupinový zápis " + bankName + "...", "#FF9800")', 'setStatus("● Skupinový zápis " + bankName + "...", colorRes(R.color.accent))'),
    ('setStatus("● " + bankName + " zapsáno — naskenujte tag", "#4CAF50")', 'setStatus("● " + bankName + " zapsáno — naskenujte tag", colorRes(R.color.success))'),
    ('setStatus("● Skupinový zápis " + bankName + " selhal", "#F44336")', 'setStatus("● Skupinový zápis " + bankName + " selhal", colorRes(R.color.error_runtime))'),
    ('setStatus("● Zapisuji heslo...", "#FF9800")', 'setStatus("● Zapisuji heslo...", colorRes(R.color.accent))'),
    ('setStatus("● Heslo zapsáno", "#4CAF50")', 'setStatus("● Heslo zapsáno", colorRes(R.color.success))'),
    ('setStatus("● Zápis hesla selhal", "#F44336")', 'setStatus("● Zápis hesla selhal", colorRes(R.color.error_runtime))'),
    ('setStatus("● Zaheslovávám tag...", "#FF9800")', 'setStatus("● Zaheslovávám tag...", colorRes(R.color.accent))'),
    ('setStatus("● Tag zaheslován ✓", "#4CAF50")', 'setStatus("● Tag zaheslován ✓", colorRes(R.color.success))'),
    ('setStatus("● Lock selhal", "#F44336")', 'setStatus("● Lock selhal", colorRes(R.color.error_runtime))'),
    ('setStatus("● Zamykám...", "#FF9800")', 'setStatus("● Zamykám...", colorRes(R.color.lock))'),
    ('setStatus("● Tag zamčen", "#4CAF50")', 'setStatus("● Tag zamčen", colorRes(R.color.success))'),
]

SET_STATUS_SIG = 'private void setStatus(String text, String hexColor) {\n        tvStatus.setText(text); tvStatus.setTextColor(Color.parseColor(hexColor));\n    }'
SET_STATUS_NEW = 'private void setStatus(String text, int color) {\n        tvStatus.setText(text); tvStatus.setTextColor(color);\n    }'


def main():
    path = 'app/src/main/java/com/rfid/writer/MainActivity.java'
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    for old, new in REPLACEMENTS:
        content = content.replace(old, new)

    content = content.replace(SET_STATUS_SIG, SET_STATUS_NEW)

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print('Updated MainActivity.java')


if __name__ == '__main__':
    main()
