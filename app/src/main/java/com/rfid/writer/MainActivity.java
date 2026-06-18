package com.rfid.writer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayout;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RFIDWriter";
    // Chainway C5 physical scan trigger keycode
    private static final int TRIGGER_KEYCODE = 293;
    private static final String PREFS_NAME = "rfid_records";
    private static final String KEY_RECORDS = "records";

    // ── SDK ───────────────────────────────────────────────────────────────
    private RFIDWithUHFUART mReader;

    // ── Global header ─────────────────────────────────────────────────────
    private TextView tvStatus;

    // ── Tab layout + pages ────────────────────────────────────────────────
    private TabLayout tabLayout;
    private View pageTag, pageWriteEpc, pageGroupWrite, pageLock;

    // ── Page 0: Načítání tagů ─────────────────────────────────────────────
    private EditText etChipNum;
    private Button btnScan, btnSkip, btnModeRecord, btnModeRead;
    private Button btnCopyRecords, btnExportCsv, btnClearAll;
    private TextView tvScanEpc, tvScanTid;
    private TextView tvStatPairs, tvStatOk, tvStatBad;
    private LinearLayout llRecords;
    private ScrollView svRecords;
    private View llStats, llListHeader, llEmptyState, llReadResult;
    private TextView tvReadEpc, tvReadTid;

    // ── Page 1: Zápis EPC ─────────────────────────────────────────────────
    private EditText etWritePrt, etWriteLen, etWriteAccessPwd, etNewEpc;
    private Button btnWrite;

    // ── Page 3: Uzamčení ──────────────────────────────────────────────────
    private EditText etPwdCurrentPwd, etPwdNewPwd;
    private Button btnWritePwd;
    private EditText etLockAccessPwd, etLockCode;
    private Button btnLock;

    // ── State ─────────────────────────────────────────────────────────────
    private boolean mInventorying = false;
    private boolean mRecordMode = true;
    private final List<ScanRecord> mRecords = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // ── Data model ────────────────────────────────────────────────────────

    static class ScanRecord {
        int seq;
        String num, epc, tid, time;
        boolean complete, dup;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupTabs();
        bindButtons();
        loadRecords();
        renderRecordList();
        updateStats();
        initReader();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReader != null) {
            if (mInventorying) mReader.stopInventory();
            mReader.free();
        }
    }

    // ── Physical trigger button ───────────────────────────────────────────

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == TRIGGER_KEYCODE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                onTriggerDown();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void onTriggerDown() {
        switch (tabLayout.getSelectedTabPosition()) {
            case 0:
                if (mInventorying) stopScan();
                else startScan();
                break;
            case 1:
                writeEpc();
                break;
            case 2:
                break;
            case 3:
                lockTag();
                break;
        }
    }

    // ── View binding ──────────────────────────────────────────────────────

    private void bindViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tabLayout = findViewById(R.id.tabLayout);

        pageTag        = findViewById(R.id.pageTag);
        pageWriteEpc   = findViewById(R.id.pageWriteEpc);
        pageGroupWrite = findViewById(R.id.pageGroupWrite);
        pageLock       = findViewById(R.id.pageLock);

        // Page 0
        etChipNum      = findViewById(R.id.etChipNum);
        btnScan        = findViewById(R.id.btnScan);
        btnSkip        = findViewById(R.id.btnSkip);
        btnModeRecord  = findViewById(R.id.btnModeRecord);
        btnModeRead    = findViewById(R.id.btnModeRead);
        tvScanEpc      = findViewById(R.id.tvScanEpc);
        tvScanTid      = findViewById(R.id.tvScanTid);
        tvStatPairs    = findViewById(R.id.tvStatPairs);
        tvStatOk       = findViewById(R.id.tvStatOk);
        tvStatBad      = findViewById(R.id.tvStatBad);
        llStats        = findViewById(R.id.llStats);
        llListHeader   = findViewById(R.id.llListHeader);
        llRecords      = findViewById(R.id.llRecords);
        llEmptyState   = findViewById(R.id.llEmptyState);
        svRecords      = findViewById(R.id.svRecords);
        llReadResult   = findViewById(R.id.llReadResult);
        tvReadEpc      = findViewById(R.id.tvReadEpc);
        tvReadTid      = findViewById(R.id.tvReadTid);
        btnCopyRecords = findViewById(R.id.btnCopyRecords);
        btnExportCsv   = findViewById(R.id.btnExportCsv);
        btnClearAll    = findViewById(R.id.btnClearAll);

        // Page 1
        etWritePrt       = findViewById(R.id.etWritePrt);
        etWriteLen       = findViewById(R.id.etWriteLen);
        etWriteAccessPwd = findViewById(R.id.etWriteAccessPwd);
        etNewEpc         = findViewById(R.id.etNewEpc);
        btnWrite         = findViewById(R.id.btnWrite);

        // Page 3
        etPwdCurrentPwd = findViewById(R.id.etPwdCurrentPwd);
        etPwdNewPwd     = findViewById(R.id.etPwdNewPwd);
        btnWritePwd     = findViewById(R.id.btnWritePwd);
        etLockAccessPwd = findViewById(R.id.etLockAccessPwd);
        etLockCode      = findViewById(R.id.etLockCode);
        btnLock         = findViewById(R.id.btnLock);
    }

    private void setupTabs() {
        showPage(0);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showPage(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showPage(int index) {
        pageTag.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageWriteEpc.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pageGroupWrite.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        pageLock.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
    }

    private void bindButtons() {
        // Page 0
        btnScan.setOnClickListener(v -> { if (mInventorying) stopScan(); else startScan(); });
        btnSkip.setOnClickListener(v -> skipScan());
        btnModeRecord.setOnClickListener(v -> setRecordMode(true));
        btnModeRead.setOnClickListener(v -> setRecordMode(false));
        btnCopyRecords.setOnClickListener(v -> copyRecords());
        btnExportCsv.setOnClickListener(v -> exportCsv());
        btnClearAll.setOnClickListener(v -> clearAll());

        // Page 1
        btnWrite.setOnClickListener(v -> writeEpc());

        // Page 3
        btnWritePwd.setOnClickListener(v -> writePwd());
        btnLock.setOnClickListener(v -> lockTag());
    }

    // ── SDK init ──────────────────────────────────────────────────────────

    private void initReader() {
        new Thread(() -> {
            try {
                mReader = RFIDWithUHFUART.getInstance();
                boolean ok = mReader.init(MainActivity.this);
                mHandler.post(() -> {
                    if (ok) {
                        setStatus("● Připojeno — Chainway C5", "#4CAF50");
                        btnScan.setEnabled(true);
                        Log.d(TAG, "SDK inicializováno OK");
                    } else {
                        setStatus("● Chyba inicializace", "#F44336");
                        Log.e(TAG, "init() vrátil false");
                    }
                });
            } catch (Exception e) {
                mHandler.post(() -> {
                    setStatus("● SDK chyba: " + e.getMessage(), "#F44336");
                    Log.e(TAG, "SDK výjimka: " + e.getMessage());
                });
            }
        }).start();
    }

    // ── Scan (Page 0) ─────────────────────────────────────────────────────

    private void startScan() {
        if (mReader == null) return;
        mInventorying = true;
        btnScan.setText("⏹  ZASTAVIT");
        btnScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")));
        setStatus("● Skenování...", "#00BCD4");

        mReader.setInventoryCallback(new IUHFInventoryCallback() {
            @Override
            public void callback(UHFTAGInfo tag) {
                if (tag == null) return;
                String epc = tag.getEPC();
                String tid = tag.getTid();
                mHandler.post(() -> onTagFound(epc, tid));
            }
        });

        mReader.setEPCAndTIDMode();
        mReader.startInventoryTag();
    }

    private void stopScan() {
        if (mReader == null) return;
        mInventorying = false;
        mReader.stopInventory();
        btnScan.setText("📡  SKENOVAT TAG");
        btnScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#00BCD4")));
        setStatus("● Připojeno — Chainway C5", "#4CAF50");
    }

    private void onTagFound(String epc, String tid) {
        stopScan();
        String epcStr = epc != null ? epc : "";
        String tidStr = tid != null ? tid : "";

        tvScanEpc.setText(epcStr.isEmpty() ? "— žádné EPC —" : epcStr);
        tvScanTid.setText(tidStr.isEmpty() ? "— žádné TID —" : tidStr);

        Log.d(TAG, "TAG nalezen — EPC: " + epc + " TID: " + tid);

        if (mRecordMode) {
            savePair(epcStr, tidStr);
        } else {
            tvReadEpc.setText(epcStr.isEmpty() ? "—" : epcStr);
            tvReadTid.setText(tidStr.isEmpty() ? "—" : tidStr);
        }
    }

    private void skipScan() {
        savePair("", "");
    }

    // ── Record mode / Read mode ───────────────────────────────────────────

    private void setRecordMode(boolean record) {
        mRecordMode = record;
        int activeColor   = Color.parseColor("#00BCD4");
        int inactiveColor = Color.parseColor("#2E2E2E");
        btnModeRecord.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(record ? activeColor : inactiveColor));
        btnModeRead.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(record ? inactiveColor : activeColor));

        int recVis = record ? View.VISIBLE : View.GONE;
        llStats.setVisibility(recVis);
        llListHeader.setVisibility(recVis);
        svRecords.setVisibility(recVis);
        llReadResult.setVisibility(record ? View.GONE : View.VISIBLE);
    }

    // ── Record management ─────────────────────────────────────────────────

    private void savePair(String epc, String tid) {
        String chipNum = etChipNum.getText().toString().trim();
        String time    = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        boolean complete = !epc.isEmpty() && !tid.isEmpty();

        // Duplicate detection
        boolean isDup = false;
        ScanRecord dupRecord = null;
        if (!epc.isEmpty()) {
            for (ScanRecord r : mRecords) {
                if (epc.equals(r.epc)) { isDup = true; dupRecord = r; break; }
            }
        }
        if (!isDup && !tid.isEmpty()) {
            for (ScanRecord r : mRecords) {
                if (tid.equals(r.tid)) { isDup = true; dupRecord = r; break; }
            }
        }

        ScanRecord rec = new ScanRecord();
        rec.seq      = mRecords.size() + 1;
        rec.num      = chipNum;
        rec.epc      = epc;
        rec.tid      = tid;
        rec.time     = time;
        rec.complete = complete;
        rec.dup      = isDup;
        mRecords.add(rec);

        // Auto-increment chip number
        try {
            int n = Integer.parseInt(chipNum);
            String next = String.valueOf(n + 1);
            etChipNum.setText(next);
            etChipNum.setSelection(next.length());
        } catch (NumberFormatException ignored) {}

        if (isDup) {
            String prev = dupRecord != null && !dupRecord.num.isEmpty()
                    ? "ID_RFID: " + dupRecord.num : "záznam č. " + (dupRecord != null ? dupRecord.seq : "?");
            toast("⚠️ Duplikát — " + prev);
            Log.w(TAG, "DUPLIKÁT EPC=" + epc + " TID=" + tid);
        } else {
            toast(complete
                    ? "✅ #" + rec.seq + " — ID_RFID " + chipNum + " uložen"
                    : "⚠️ #" + rec.seq + " uložen — chybí EPC nebo TID");
        }

        saveRecords();
        renderRecordList();
        updateStats();
    }

    private void deleteRecord(int index) {
        if (index < 0 || index >= mRecords.size()) return;
        mRecords.remove(index);
        for (int i = 0; i < mRecords.size(); i++) mRecords.get(i).seq = i + 1;
        saveRecords();
        renderRecordList();
        updateStats();
    }

    private void clearAll() {
        if (mRecords.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Smazat záznamy?")
                .setMessage("Opravdu smazat všech " + mRecords.size() + " záznamů?")
                .setPositiveButton("Smazat", (d, w) -> {
                    mRecords.clear();
                    saveRecords();
                    renderRecordList();
                    updateStats();
                })
                .setNegativeButton("Zrušit", null)
                .show();
    }

    // ── Render list ───────────────────────────────────────────────────────

    private void renderRecordList() {
        // Remove all except the empty-state view (index 0)
        while (llRecords.getChildCount() > 1) {
            llRecords.removeViewAt(1);
        }

        if (mRecords.isEmpty()) {
            llEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        llEmptyState.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = mRecords.size() - 1; i >= 0; i--) {
            ScanRecord r   = mRecords.get(i);
            View row       = inflater.inflate(R.layout.item_scan_record, llRecords, false);

            ((TextView) row.findViewById(R.id.tvItemSeq)).setText(String.valueOf(r.seq));
            ((TextView) row.findViewById(R.id.tvItemNum)).setText(r.num.isEmpty() ? "—" : r.num);
            ((TextView) row.findViewById(R.id.tvItemTime)).setText(r.time);
            ((TextView) row.findViewById(R.id.tvItemDup)).setText(r.dup ? "⚠ DUP" : "");

            TextView tvEpc = row.findViewById(R.id.tvItemEpc);
            tvEpc.setText(r.epc.isEmpty() ? "—" : r.epc);
            tvEpc.setTextColor(Color.parseColor(r.epc.isEmpty() ? "#e3b341" : "#00BCD4"));

            TextView tvTid = row.findViewById(R.id.tvItemTid);
            tvTid.setText(r.tid.isEmpty() ? "—" : r.tid);
            tvTid.setTextColor(Color.parseColor(r.tid.isEmpty() ? "#e3b341" : "#4CAF50"));

            row.setBackgroundColor(Color.parseColor(r.dup ? "#2a1015" : "#0d1117"));

            // Divider line between rows
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(Color.parseColor("#21262d"));

            final int origIndex = i;
            row.findViewById(R.id.btnItemDel).setOnClickListener(v -> deleteRecord(origIndex));

            llRecords.addView(row);
            llRecords.addView(divider);
        }

        svRecords.post(() -> svRecords.scrollTo(0, 0));
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    private void updateStats() {
        int ok = 0;
        for (ScanRecord r : mRecords) if (r.complete) ok++;
        tvStatPairs.setText(String.valueOf(mRecords.size()));
        tvStatOk.setText(String.valueOf(ok));
        tvStatBad.setText(String.valueOf(mRecords.size() - ok));
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private void saveRecords() {
        try {
            JSONArray arr = new JSONArray();
            for (ScanRecord r : mRecords) {
                JSONObject obj = new JSONObject();
                obj.put("seq",      r.seq);
                obj.put("num",      r.num);
                obj.put("epc",      r.epc);
                obj.put("tid",      r.tid);
                obj.put("time",     r.time);
                obj.put("complete", r.complete);
                obj.put("dup",      r.dup);
                arr.put(obj);
            }
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_RECORDS, arr.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "saveRecords: " + e.getMessage());
        }
    }

    private void loadRecords() {
        try {
            String json = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_RECORDS, "[]");
            JSONArray arr = new JSONArray(json);
            mRecords.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                ScanRecord r = new ScanRecord();
                r.seq      = obj.getInt("seq");
                r.num      = obj.getString("num");
                r.epc      = obj.getString("epc");
                r.tid      = obj.getString("tid");
                r.time     = obj.getString("time");
                r.complete = obj.getBoolean("complete");
                r.dup      = obj.getBoolean("dup");
                mRecords.add(r);
            }
            if (!mRecords.isEmpty()) {
                String lastNum = mRecords.get(mRecords.size() - 1).num;
                try {
                    int n = Integer.parseInt(lastNum);
                    String next = String.valueOf(n + 1);
                    etChipNum.setText(next);
                    etChipNum.setSelection(next.length());
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "loadRecords: " + e.getMessage());
        }
    }

    // ── Export CSV ────────────────────────────────────────────────────────

    private void exportCsv() {
        if (mRecords.isEmpty()) { toast("Žádné záznamy!"); return; }

        StringBuilder sb = new StringBuilder("\uFEFF"); // BOM for Excel UTF-8
        sb.append("Seq,ID_RFID,EPC,TID,Stav,Cas,Poznamka\n");
        for (ScanRecord r : mRecords) {
            sb.append(r.seq).append(",")
              .append(escCsv(r.num)).append(",")
              .append(escCsv(r.epc)).append(",")
              .append(escCsv(r.tid)).append(",")
              .append(r.complete ? "OK" : "NEUPLNE").append(",")
              .append(escCsv(r.time)).append(",")
              .append(r.dup ? "DUPLIKAT" : "").append("\n");
        }

        try {
            String stamp    = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
            String filename = "rfid_" + stamp + ".csv";
            File dir  = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(sb.toString().getBytes("UTF-8"));
            }
            toast("✅ CSV: " + filename);
            Log.d(TAG, "Export: " + file.getAbsolutePath());
        } catch (Exception e) {
            toast("Chyba exportu: " + e.getMessage());
            Log.e(TAG, "exportCsv: " + e.getMessage());
        }
    }

    private String escCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ── Copy to clipboard ─────────────────────────────────────────────────

    private void copyRecords() {
        if (mRecords.isEmpty()) { toast("Žádné záznamy!"); return; }
        StringBuilder sb = new StringBuilder("Seq\tID_RFID\tEPC\tTID\tStav\tCas\n");
        for (ScanRecord r : mRecords) {
            sb.append(r.seq).append("\t")
              .append(r.num).append("\t")
              .append(r.epc).append("\t")
              .append(r.tid).append("\t")
              .append(r.complete ? "OK" : "NEUPLNE").append("\t")
              .append(r.time).append("\n");
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("RFID záznamy", sb.toString()));
            toast("📋 Zkopírováno — vložte do Excelu");
        }
    }

    // ── Write EPC (Page 1) ────────────────────────────────────────────────

    private void writeEpc() {
        if (mReader == null) return;

        String newEpc = etNewEpc.getText().toString().trim().toUpperCase().replaceAll("\\s", "");
        if (newEpc.isEmpty()) { toast("Zadejte nový EPC"); return; }
        if (!newEpc.matches("[0-9A-F]+")) { toast("EPC smí obsahovat pouze hex znaky 0–9, A–F"); return; }
        if (newEpc.length() % 4 != 0 || newEpc.length() < 4) {
            toast("Délka EPC musí být dělitelná 4 (každé slovo = 4 hex znaky)");
            return;
        }

        int prt, len;
        try {
            prt = Integer.parseInt(etWritePrt.getText().toString().trim());
            len = Integer.parseInt(etWriteLen.getText().toString().trim());
        } catch (NumberFormatException e) {
            toast("Neplatné hodnoty Prt / Len"); return;
        }

        String accessPwd = readPassword(etWriteAccessPwd);

        Log.d(TAG, "Zapisuji EPC: " + newEpc + " (bank=EPC, prt=" + prt + ", len=" + len + ")");
        setStatus("● Zapisuji EPC...", "#FF9800");
        btnWrite.setEnabled(false);

        final int finalPrt = prt;
        final int finalLen = len;
        new Thread(() -> {
            boolean ok = mReader.writeData(accessPwd, 1, finalPrt, finalLen, newEpc);
            mHandler.post(() -> {
                btnWrite.setEnabled(true);
                if (ok) {
                    tvScanEpc.setText(newEpc);
                    setStatus("● EPC zapsáno OK", "#4CAF50");
                    toast("EPC zapsáno!");
                    Log.d(TAG, "EPC zapsáno: " + newEpc);
                } else {
                    setStatus("● Zápis EPC selhal", "#F44336");
                    toast("Zápis selhal");
                    Log.w(TAG, "Zápis EPC selhal");
                }
            });
        }).start();
    }

    // ── Write Password (Page 3 — sekce Zápis hesla) ───────────────────────

    private void writePwd() {
        if (mReader == null) return;

        String currentPwd = readPassword(etPwdCurrentPwd);
        String newPwd = etPwdNewPwd.getText().toString().trim().toUpperCase();

        if (newPwd.isEmpty() || newPwd.length() != 8 || !newPwd.matches("[0-9A-F]+")) {
            toast("Zadejte platné nové heslo (přesně 8 hex znaků)");
            return;
        }

        Log.d(TAG, "Zapisuji heslo: " + newPwd + " (bank=Reserved, prt=2, len=2)");
        setStatus("● Zapisuji heslo...", "#FF9800");
        btnWritePwd.setEnabled(false);

        new Thread(() -> {
            boolean ok = mReader.writeData(currentPwd, 0, 2, 2, newPwd);
            mHandler.post(() -> {
                btnWritePwd.setEnabled(true);
                if (ok) {
                    setStatus("● Heslo zapsáno", "#4CAF50");
                    etLockAccessPwd.setText(newPwd);
                    toast("Heslo zapsáno!");
                    Log.d(TAG, "Heslo zapsáno: " + newPwd);
                } else {
                    setStatus("● Zápis hesla selhal", "#F44336");
                    toast("Zápis hesla selhal");
                    Log.w(TAG, "Zápis hesla selhal");
                }
            });
        }).start();
    }

    // ── Lock (Page 3 — sekce Zamčení) ─────────────────────────────────────

    private void lockTag() {
        if (mReader == null) return;

        String accessPwd = readPassword(etLockAccessPwd);
        String lockCode  = etLockCode.getText().toString().trim().toUpperCase();
        if (lockCode.isEmpty()) lockCode = "008020";

        Log.d(TAG, "Zamykám tag — kód: " + lockCode);
        setStatus("● Zamykám...", "#FF9800");
        btnLock.setEnabled(false);

        final String finalCode = lockCode;
        new Thread(() -> {
            boolean ok = mReader.lockMem(accessPwd, finalCode);
            mHandler.post(() -> {
                btnLock.setEnabled(true);
                if (ok) {
                    setStatus("● Tag zamčen", "#4CAF50");
                    toast("Tag zamčen!");
                    Log.d(TAG, "Tag zamčen (kód: " + finalCode + ")");
                } else {
                    setStatus("● Lock selhal", "#F44336");
                    toast("Lock selhal");
                    Log.w(TAG, "Zamčení selhalo");
                }
            });
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String readPassword(EditText field) {
        String val = field.getText().toString().trim().toUpperCase();
        if (val.length() == 8 && val.matches("[0-9A-F]+")) return val;
        return "00000000";
    }

    private void setStatus(String text, String hexColor) {
        tvStatus.setText(text);
        tvStatus.setTextColor(Color.parseColor(hexColor));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
