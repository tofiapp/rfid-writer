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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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
    private static final int TRIGGER_KEYCODE = 293;
    private static final String PREFS_NAME   = "rfid_records";
    private static final String KEY_RECORDS  = "records";

    // ── SDK ───────────────────────────────────────────────────────────────
    private RFIDWithUHFUART mReader;

    // ── Global header ─────────────────────────────────────────────────────
    private TextView tvStatus;

    // ── Tabs ──────────────────────────────────────────────────────────────
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

    // ── Page 2: Skupinový zápis ───────────────────────────────────────────
    private final EditText[]  etGroups   = new EditText[6];
    private final CheckBox[]  cbGroups   = new CheckBox[6];
    private final boolean[]   groupAutoInc = {false, false, false, false, false, true};
    private TextView  tvEpcPreview;
    private EditText  etGroupAccessPwd, etGroupFileName;
    private Button    btnGroupWrite, btnGroupSetFile;
    private TextView  tvGroupFilePath, tvGroupRecordCount, tvGroupLastEpc;

    // ── Page 3: Uzamčení ──────────────────────────────────────────────────
    private EditText etPwdCurrentPwd, etPwdNewPwd;
    private Button   btnWritePwd;
    private EditText etLockAccessPwd, etLockCode;
    private Button   btnLock;

    // ── State ─────────────────────────────────────────────────────────────
    private boolean mInventorying    = false;
    private boolean mRecordMode      = true;
    private final List<ScanRecord> mRecords = new ArrayList<>();
    private String  mGroupOutputFile = null;
    private int     mGroupRecordCount = 0;
    private String  mLastTid          = "";
    private final Handler mHandler   = new Handler(Looper.getMainLooper());

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
        loadGroupSettings();
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

    // ── Physical trigger ──────────────────────────────────────────────────

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == TRIGGER_KEYCODE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) onTriggerDown();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void onTriggerDown() {
        switch (tabLayout.getSelectedTabPosition()) {
            case 0: if (mInventorying) stopScan(); else startScan(); break;
            case 1: writeEpc();   break;
            case 2: groupWrite(); break;
            case 3: lockTag();    break;
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

        // Page 2
        etGroups[0] = findViewById(R.id.etGroup1);
        etGroups[1] = findViewById(R.id.etGroup2);
        etGroups[2] = findViewById(R.id.etGroup3);
        etGroups[3] = findViewById(R.id.etGroup4);
        etGroups[4] = findViewById(R.id.etGroup5);
        etGroups[5] = findViewById(R.id.etGroup6);

        cbGroups[0] = findViewById(R.id.cbGroup1);
        cbGroups[1] = findViewById(R.id.cbGroup2);
        cbGroups[2] = findViewById(R.id.cbGroup3);
        cbGroups[3] = findViewById(R.id.cbGroup4);
        cbGroups[4] = findViewById(R.id.cbGroup5);
        cbGroups[5] = findViewById(R.id.cbGroup6);

        tvEpcPreview       = findViewById(R.id.tvEpcPreview);
        etGroupAccessPwd   = findViewById(R.id.etGroupAccessPwd);
        etGroupFileName    = findViewById(R.id.etGroupFileName);
        btnGroupWrite      = findViewById(R.id.btnGroupWrite);
        btnGroupSetFile    = findViewById(R.id.btnGroupSetFile);
        tvGroupFilePath    = findViewById(R.id.tvGroupFilePath);
        tvGroupRecordCount = findViewById(R.id.tvGroupRecordCount);
        tvGroupLastEpc     = findViewById(R.id.tvGroupLastEpc);

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

        if (index == 2) {
            // Sync Group 6 (ID_RFID) from TAGY tab when entering this tab
            String chipNum = etChipNum.getText().toString().trim();
            if (!chipNum.isEmpty()) {
                try {
                    int n = Integer.parseInt(chipNum);
                    etGroups[5].setText(String.format("%04d", n % 10000));
                } catch (NumberFormatException ignored) {}
            }
            updateGroupEpcPreview();
        }
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

        // Page 2
        btnGroupWrite.setOnClickListener(v -> groupWrite());
        btnGroupSetFile.setOnClickListener(v -> setGroupOutputFile());

        TextWatcher epcPreviewWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateGroupEpcPreview(); }
        };
        for (EditText et : etGroups) et.addTextChangedListener(epcPreviewWatcher);

        for (int i = 0; i < 6; i++) {
            final int idx = i;
            cbGroups[i].setOnCheckedChangeListener((btn, checked) -> groupAutoInc[idx] = checked);
        }

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
                        Log.d(TAG, "SDK OK");
                    } else {
                        setStatus("● Chyba inicializace", "#F44336");
                        Log.e(TAG, "init() false");
                    }
                });
            } catch (Exception e) {
                mHandler.post(() -> {
                    setStatus("● SDK chyba: " + e.getMessage(), "#F44336");
                    Log.e(TAG, e.getMessage());
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

        mLastTid = tidStr;  // shared with group write CSV

        tvScanEpc.setText(epcStr.isEmpty() ? "— žádné EPC —" : epcStr);
        tvScanTid.setText(tidStr.isEmpty() ? "— žádné TID —" : tidStr);

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

    // ── Record / Read mode ────────────────────────────────────────────────

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

    // ── Record management (Page 0) ────────────────────────────────────────

    private void savePair(String epc, String tid) {
        String chipNum = etChipNum.getText().toString().trim();
        String time    = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        boolean complete = !epc.isEmpty() && !tid.isEmpty();

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
        rec.seq = mRecords.size() + 1; rec.num = chipNum; rec.epc = epc;
        rec.tid = tid; rec.time = time; rec.complete = complete; rec.dup = isDup;
        mRecords.add(rec);

        try {
            int n = Integer.parseInt(chipNum);
            String next = String.valueOf(n + 1);
            etChipNum.setText(next);
            etChipNum.setSelection(next.length());
        } catch (NumberFormatException ignored) {}

        if (isDup) {
            String prev = dupRecord != null && !dupRecord.num.isEmpty()
                    ? "ID_RFID: " + dupRecord.num : "č. " + (dupRecord != null ? dupRecord.seq : "?");
            toast("⚠️ Duplikát — " + prev);
        } else {
            toast(complete
                    ? "✅ #" + rec.seq + " — ID_RFID " + chipNum + " uložen"
                    : "⚠️ #" + rec.seq + " — chybí EPC nebo TID");
        }

        saveRecords(); renderRecordList(); updateStats();
    }

    private void deleteRecord(int index) {
        if (index < 0 || index >= mRecords.size()) return;
        mRecords.remove(index);
        for (int i = 0; i < mRecords.size(); i++) mRecords.get(i).seq = i + 1;
        saveRecords(); renderRecordList(); updateStats();
    }

    private void clearAll() {
        if (mRecords.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Smazat záznamy?")
                .setMessage("Opravdu smazat všech " + mRecords.size() + " záznamů?")
                .setPositiveButton("Smazat", (d, w) -> {
                    mRecords.clear(); saveRecords(); renderRecordList(); updateStats();
                })
                .setNegativeButton("Zrušit", null).show();
    }

    private void renderRecordList() {
        while (llRecords.getChildCount() > 1) llRecords.removeViewAt(1);
        if (mRecords.isEmpty()) { llEmptyState.setVisibility(View.VISIBLE); return; }
        llEmptyState.setVisibility(View.GONE);

        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = mRecords.size() - 1; i >= 0; i--) {
            ScanRecord r = mRecords.get(i);
            View row = inf.inflate(R.layout.item_scan_record, llRecords, false);

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

            View div = new View(this);
            div.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            div.setBackgroundColor(Color.parseColor("#21262d"));

            final int origIndex = i;
            row.findViewById(R.id.btnItemDel).setOnClickListener(v -> deleteRecord(origIndex));
            llRecords.addView(row);
            llRecords.addView(div);
        }
        svRecords.post(() -> svRecords.scrollTo(0, 0));
    }

    private void updateStats() {
        int ok = 0;
        for (ScanRecord r : mRecords) if (r.complete) ok++;
        tvStatPairs.setText(String.valueOf(mRecords.size()));
        tvStatOk.setText(String.valueOf(ok));
        tvStatBad.setText(String.valueOf(mRecords.size() - ok));
    }

    // ── Persistence (Page 0) ──────────────────────────────────────────────

    private void saveRecords() {
        try {
            JSONArray arr = new JSONArray();
            for (ScanRecord r : mRecords) {
                JSONObject o = new JSONObject();
                o.put("seq", r.seq); o.put("num", r.num); o.put("epc", r.epc);
                o.put("tid", r.tid); o.put("time", r.time);
                o.put("complete", r.complete); o.put("dup", r.dup);
                arr.put(o);
            }
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_RECORDS, arr.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "saveRecords: " + e.getMessage()); }
    }

    private void loadRecords() {
        try {
            String json = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_RECORDS, "[]");
            JSONArray arr = new JSONArray(json);
            mRecords.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ScanRecord r = new ScanRecord();
                r.seq = o.getInt("seq"); r.num = o.getString("num");
                r.epc = o.getString("epc"); r.tid = o.getString("tid");
                r.time = o.getString("time"); r.complete = o.getBoolean("complete");
                r.dup = o.getBoolean("dup");
                mRecords.add(r);
            }
            if (!mRecords.isEmpty()) {
                try {
                    int n = Integer.parseInt(mRecords.get(mRecords.size() - 1).num);
                    String next = String.valueOf(n + 1);
                    etChipNum.setText(next);
                    etChipNum.setSelection(next.length());
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) { Log.e(TAG, "loadRecords: " + e.getMessage()); }
    }

    // ── Export / Copy (Page 0) ────────────────────────────────────────────

    private void exportCsv() {
        if (mRecords.isEmpty()) { toast("Žádné záznamy!"); return; }
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("Seq,ID_RFID,EPC,TID,Stav,Cas\n");
        for (ScanRecord r : mRecords)
            sb.append(r.seq).append(",").append(escCsv(r.num)).append(",")
              .append(escCsv(r.epc)).append(",").append(escCsv(r.tid)).append(",")
              .append(r.complete ? "OK" : "NEUPLNE").append(",")
              .append(escCsv(r.time)).append("\n");
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "rfid_" + stamp + ".csv");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(sb.toString().getBytes("UTF-8"));
            }
            toast("✅ CSV: " + file.getName());
        } catch (Exception e) { toast("Chyba exportu: " + e.getMessage()); }
    }

    private void copyRecords() {
        if (mRecords.isEmpty()) { toast("Žádné záznamy!"); return; }
        StringBuilder sb = new StringBuilder("Seq\tID_RFID\tEPC\tTID\tStav\tCas\n");
        for (ScanRecord r : mRecords)
            sb.append(r.seq).append("\t").append(r.num).append("\t")
              .append(r.epc).append("\t").append(r.tid).append("\t")
              .append(r.complete ? "OK" : "NEUPLNE").append("\t").append(r.time).append("\n");
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("RFID záznamy", sb.toString()));
            toast("📋 Zkopírováno — vložte do Excelu");
        }
    }

    // ── Group write — EPC template (Page 2) ──────────────────────────────

    /** Builds the 24-char hex EPC from the 6 group EditTexts. */
    private String buildGroupEpc() {
        StringBuilder sb = new StringBuilder();
        for (EditText et : etGroups) {
            String val = et.getText().toString().toUpperCase(Locale.ROOT).trim();
            while (val.length() < 4) val = "0" + val;
            if (val.length() > 4) val = val.substring(0, 4);
            sb.append(val);
        }
        return sb.toString();
    }

    private void updateGroupEpcPreview() {
        if (tvEpcPreview == null) return;
        String epc = buildGroupEpc();
        if (epc.length() == 24) {
            tvEpcPreview.setText(
                    epc.substring(0, 4) + "-" + epc.substring(4, 8) + "-" +
                    epc.substring(8, 12) + "-" + epc.substring(12, 16) + "-" +
                    epc.substring(16, 20) + "-" + epc.substring(20, 24));
        }
    }

    /** Increments groups that have auto-increment enabled, then syncs Group 6 back to TAGY tab. */
    private void autoIncrementGroups() {
        for (int i = 0; i < 6; i++) {
            if (!groupAutoInc[i]) continue;
            String val = etGroups[i].getText().toString().toUpperCase(Locale.ROOT).trim();
            String next;
            if (i == 5) {
                // Group 6 (ID_RFID): decimal 0000–0999, wraps at 1000
                try { next = String.format("%04d", (Integer.parseInt(val) + 1) % 1000); }
                catch (NumberFormatException e) { next = "0001"; }
                // Sync to TAGY tab
                etChipNum.setText(next);
            } else {
                // Other groups: hex
                try { next = String.format("%04X", (Integer.parseInt(val, 16) + 1) & 0xFFFF); }
                catch (NumberFormatException e) { next = "0001"; }
            }
            etGroups[i].setText(next);
        }
        updateGroupEpcPreview();
        saveGroupSettings();
    }

    private void groupWrite() {
        if (mReader == null) return;

        String epc = buildGroupEpc();
        if (!epc.matches("[0-9A-F]{24}")) {
            toast("EPC obsahuje neplatné znaky (povoleno 0-9, A-F)");
            return;
        }

        String accessPwd = readPassword(etGroupAccessPwd);

        setStatus("● Skupinový zápis...", "#FF9800");
        btnGroupWrite.setEnabled(false);

        new Thread(() -> {
            boolean ok = mReader.writeData(accessPwd, 1, 2, 6, epc);
            mHandler.post(() -> {
                btnGroupWrite.setEnabled(true);
                if (ok) {
                    setStatus("● EPC zapsáno OK", "#4CAF50");
                    toast("✅ EPC zapsáno");
                    Log.d(TAG, "Group EPC: " + epc);
                    appendToGroupCsv(epc, mLastTid);
                    autoIncrementGroups();
                } else {
                    setStatus("● Skupinový zápis selhal", "#F44336");
                    toast("Zápis selhal");
                }
            });
        }).start();
    }

    // ── Group write — output file ─────────────────────────────────────────

    private void setGroupOutputFile() {
        String name = etGroupFileName.getText().toString().trim();
        if (name.isEmpty()) { toast("Zadejte název souboru"); return; }

        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, name + ".csv");
            if (!file.exists()) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write("\uFEFFSeq,Cas,EPC,TID\n".getBytes("UTF-8"));
                }
                mGroupRecordCount = 0;
            }

            mGroupOutputFile = file.getAbsolutePath();
            tvGroupFilePath.setText(file.getName() + "\n" + file.getParent());
            tvGroupRecordCount.setText(String.valueOf(mGroupRecordCount));
            saveGroupSettings();
            toast("✅ Soubor: " + file.getName());
            Log.d(TAG, "Group file: " + mGroupOutputFile);
        } catch (Exception e) {
            toast("Chyba: " + e.getMessage());
        }
    }

    private void appendToGroupCsv(String epc, String tid) {
        if (mGroupOutputFile == null) {
            toast("⚠️ Nastavte výstupní soubor");
            return;
        }
        mGroupRecordCount++;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = mGroupRecordCount + "," + escCsv(time) + "," + epc + "," + tid + "\n";

        try (FileOutputStream fos = new FileOutputStream(mGroupOutputFile, true)) {
            fos.write(line.getBytes("UTF-8"));
        } catch (Exception e) {
            toast("Chyba zápisu do souboru: " + e.getMessage());
            Log.e(TAG, "appendToGroupCsv: " + e.getMessage());
            mGroupRecordCount--;
            return;
        }

        String formatted = epc.substring(0, 4) + "-" + epc.substring(4, 8) + "-" +
                           epc.substring(8, 12) + "-" + epc.substring(12, 16) + "-" +
                           epc.substring(16, 20) + "-" + epc.substring(20, 24);
        tvGroupRecordCount.setText(String.valueOf(mGroupRecordCount));
        tvGroupLastEpc.setText(formatted);
        saveGroupSettings();
    }

    // ── Group settings persistence ────────────────────────────────────────

    private void loadGroupSettings() {
        mGroupOutputFile  = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("group_file", null);
        mGroupRecordCount = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("group_count", 0);

        try {
            String vJson = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString("group_values", null);
            if (vJson != null) {
                JSONArray arr = new JSONArray(vJson);
                for (int i = 0; i < 6 && i < arr.length(); i++)
                    etGroups[i].setText(arr.getString(i));
            }
        } catch (Exception ignored) {}

        try {
            String iJson = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString("group_autoinc", null);
            if (iJson != null) {
                JSONArray arr = new JSONArray(iJson);
                for (int i = 0; i < 6 && i < arr.length(); i++) {
                    groupAutoInc[i] = arr.getBoolean(i);
                    cbGroups[i].setChecked(groupAutoInc[i]);
                }
            }
        } catch (Exception ignored) {}

        if (mGroupOutputFile != null) {
            File f = new File(mGroupOutputFile);
            tvGroupFilePath.setText(f.getName() + "\n" + f.getParent());
            tvGroupRecordCount.setText(String.valueOf(mGroupRecordCount));
            etGroupFileName.setText(f.getName().replace(".csv", ""));
        }

        updateGroupEpcPreview();
    }

    private void saveGroupSettings() {
        try {
            JSONArray vArr = new JSONArray();
            JSONArray iArr = new JSONArray();
            for (int i = 0; i < 6; i++) {
                vArr.put(etGroups[i].getText().toString());
                iArr.put(groupAutoInc[i]);
            }
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString("group_file",   mGroupOutputFile)
                    .putInt("group_count",      mGroupRecordCount)
                    .putString("group_values",  vArr.toString())
                    .putString("group_autoinc", iArr.toString())
                    .apply();
        } catch (Exception e) { Log.e(TAG, "saveGroupSettings: " + e.getMessage()); }
    }

    // ── Write EPC (Page 1) ────────────────────────────────────────────────

    private void writeEpc() {
        if (mReader == null) return;

        String newEpc = etNewEpc.getText().toString().trim().toUpperCase().replaceAll("\\s", "");
        if (newEpc.isEmpty()) { toast("Zadejte nový EPC"); return; }
        if (!newEpc.matches("[0-9A-F]+")) { toast("EPC smí obsahovat pouze hex znaky 0–9, A–F"); return; }
        if (newEpc.length() % 4 != 0 || newEpc.length() < 4) {
            toast("Délka EPC musí být dělitelná 4");
            return;
        }

        int prt, len;
        try {
            prt = Integer.parseInt(etWritePrt.getText().toString().trim());
            len = Integer.parseInt(etWriteLen.getText().toString().trim());
        } catch (NumberFormatException e) { toast("Neplatné hodnoty Prt / Len"); return; }

        String accessPwd = readPassword(etWriteAccessPwd);

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
                } else {
                    setStatus("● Zápis EPC selhal", "#F44336");
                    toast("Zápis selhal");
                }
            });
        }).start();
    }

    // ── Write Password (Page 3) ───────────────────────────────────────────

    private void writePwd() {
        if (mReader == null) return;

        String currentPwd = readPassword(etPwdCurrentPwd);
        String newPwd = etPwdNewPwd.getText().toString().trim().toUpperCase();
        if (newPwd.isEmpty() || newPwd.length() != 8 || !newPwd.matches("[0-9A-F]+")) {
            toast("Zadejte platné nové heslo (přesně 8 hex znaků)");
            return;
        }

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
                } else {
                    setStatus("● Zápis hesla selhal", "#F44336");
                    toast("Zápis hesla selhal");
                }
            });
        }).start();
    }

    // ── Lock (Page 3) ─────────────────────────────────────────────────────

    private void lockTag() {
        if (mReader == null) return;

        String accessPwd = readPassword(etLockAccessPwd);
        String lockCode  = etLockCode.getText().toString().trim().toUpperCase();
        if (lockCode.isEmpty()) lockCode = "008020";

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
                } else {
                    setStatus("● Lock selhal", "#F44336");
                    toast("Lock selhal");
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

    private String escCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
