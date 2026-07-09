# RFID Writer — Chainway C5

Android aplikace pro čtení, přepis EPC a zamykání UHF RFID tagů na čtečce Chainway C5.

## Funkce
- 📡 Skenování UHF tagů (EPC + TID)
- ✏️ Zápis nového EPC kódu
- 🔒 Zamčení EPC banky (heslem)
- 🔐 Kompletní lock tagu (EPC + USER banka)

## Jak buildovat APK bez Android Studia

### 1. Nahrajte projekt na GitHub
- Vytvořte účet na github.com (zdarma)
- Klikněte **New repository** → název `rfid-writer` → Create
- Nahrajte všechny soubory tohoto projektu

### 2. Nahrajte SDK soubory do app/libs/
Nahrajte tyto soubory do složky `app/libs/` ve vašem repozitáři:
- `DeviceAPI_ver20251103_release.aar`
- `jxl.jar`
- `poi-3_12-android-a.jar`
- `poi-ooxml-schemas-3_12-20150511-a.jar`
- `xUtils-2_5_5.jar`

### 3. Spusťte build
- Na GitHubu jděte do záložky **Actions**
- Klikněte na **Build APK** → **Run workflow** → **Run workflow**
- Po ~3 minutách se APK stáhne ze záložky **Artifacts**

### 4. Nainstalujte APK na C5
- Přeneste APK na C5 (USB / e-mail / Google Drive)
- V nastavení C5 povolte "Instalace z neznámých zdrojů"
- Otevřete APK a nainstalujte

### Aktualizace (bez odinstalace)
- Každé nové APK má vyšší `versionCode` — stačí ho nainstalovat přes starší verzi
- Všechna APK z tohoto repozitáře jsou podepsána stejným klíčem (`keystore/rfid-writer-release.jks`)
- Pokud jste dříve instalovali APK z jiného zdroje (jiný podpis), je potřeba **jednorázově** odinstalovat starou verzi a nainstalovat znovu; další updaty pak půjdou normálně

## Použití

1. Otevřete app → čeká na inicializaci SDK
2. Klikněte **SKENOVAT TAG** → přiblížte tag
3. Po načtení se zobrazí EPC + TID
4. **Přepsat EPC:** zadejte nový hex kód → ZAPSAT EPC
5. **Zamknout tag:** zadejte heslo (8 hex znaků) → Lock EPC nebo Lock ALL

## Hesla
- Výchozí heslo tagu: `00000000`
- Pokud tag změníte na jiné heslo, bez něj ho nelze odemknout!

## Struktura výstupního CSV (karta Skupinový)

Soubor se vytváří přes tlačítko **Nastavit** v sekci OVĚŘENÍ + CSV.

### Záhlaví
```
ID_RFID,EPC,TID,<Skupina 1>,<Skupina 2>,<Skupina 3>,<Skupina 4>
```
Názvy sloupců Skupina 1–4 se berou z editovatelných názvů skupin v šabloně EPC.

### Každý řádek (1 naskenovaný tag)
```
<ID_RFID>,<EPC>,<TID>,<G1>,<G2>,<G3>,<G4>
```

| Sloupec | Obsah | Příklad |
|---------|-------|---------|
| ID_RFID | Skupina 5 (prefix) + skupina 6 (ID) — hex, 8 znaků | `00000001` |
| EPC | Plný EPC kód, 24 hex znaků | `202600000000000000000001` |
| TID | TID čipu | `E2806894000050009C27D793` |
| G1–G4 | Hodnoty skupin 1–4 z EPC (každá 4 hex znaky) | `2026`, `0000`, … |

### Poznámky
- Soubor je kódován UTF-8 s BOM pro kompatibilitu s Excel.
- Každý scan tagu automaticky připojí řádek a nastavení přežije restart aplikace.
- Záhlaví se přepíše, pokud změníte názvy skupin ještě před prvním skenem.
