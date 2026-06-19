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
import android.widget.SeekBar;
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
    private static final int    TRIGGER_KEYCODE = 293;
    private static final String PREFS           = "rfid_records";

    // scan context: which tab triggered the scan
    private static final int SCAN_CTX_TAG   = 0;
    private static final int SCAN_CTX_GROUP = 1;

    // workflow steps for Tab 2
    private static final int STEP_WRITE = 0;
    private static final int STEP_SCAN  = 1;
    private static final int STEP_LOCK  = 2;

    // ── SDK ───────────────────────────────────────────────────────────────
    private RFIDWithUHFUART mReader;

    // ── Global header ─────────────────────────────────────────────────────
    private TextView tvStatus;

    // ── Tabs ──────────────────────────────────────────────────────────────
    private TabLayout tabLayout;
    private View      pageTag, pageWriteEpc, pageGroupWrite, pageLock;
    private android.widget.ScrollView svGroupPage;

    // ── Page 0: Načítání tagů ─────────────────────────────────────────────
    private EditText etChipNum;
    private Button btnScan, btnModeRecord, btnModeRead, btnToggleChipNum;
    private boolean mAutoLoadIdRfid = true;
    private Button btnCopyRecords, btnExportCsv, btnClearAll;
    private TextView tvScanEpc, tvScanTid;
    private TextView tvStatPairs, tvStatOk, tvStatBad;
    private LinearLayout llRecords;
    private ScrollView svRecords;
    private View llStats, llListHeader, llEmptyState, llReadResult;
    private TextView tvReadEpc, tvReadTid;

    // ── Page 1: Zápis EPC — template ──────────────────────────────────────
    private final EditText[]  etWrtGroups    = new EditText[6];
    private final EditText[]  etWrtGroupNames = new EditText[5]; // groups 1-5, editable labels
    private final CheckBox[]  cbWrtGroups    = new CheckBox[6];
    private final boolean[]   wrtGroupAutoInc = {false, false, false, false, false, true};
    private TextView tvWrtEpcPreview;
    private TextView tvWrtPreviewLabel;
    private View     llWrtTemplateGroups;
    private Button   btnToggleWrtTemplate;
    private View     llWrtRow1, llWrtRow2, llWrtRow3, llWrtRow4, llWrtRow5, llWrtRow6;
    // (keep existing Prt/Len/Pwd/Write IDs)
    private EditText etWritePrt, etWriteLen, etWriteAccessPwd;
    private Button   btnWrite;
    private Button   btnBankEpc, btnBankUser, btnBankReserved;
    private int      mWriteBank = 1; // 0=RESERVED, 1=EPC, 3=USER

    // ── Page 2: Skupinový zápis (3-step workflow) ─────────────────────────
    private final EditText[]  etGroups      = new EditText[6];
    private final EditText[]  etGroupNames  = new EditText[5]; // groups 1-5, editable labels
    private final CheckBox[]  cbGroups      = new CheckBox[6];
    private final boolean[]   groupAutoInc  = {false, false, false, false, false, true};
    private TextView tvEpcPreview;
    private TextView tvGrpPreviewLabel;
    private Button   btnToggleTemplate;
    private View     llTemplateGroups;
    private Button   btnGrpBankEpc, btnGrpBankUser, btnGrpBankReserved;
    private EditText etGroupPrt, etGroupLen;
    private int      mGroupWriteBank = 1; // 0=RESERVED, 1=EPC, 3=USER
    // step headers
    private View     llStep1Header, llStep2Header, llStep3Header;
    private TextView tvStep1Num, tvStep2Num, tvStep3Num;
    private TextView tvStep1Label, tvStep2Label, tvStep3Label;
    // step 1 controls
    private EditText etGroupAccessPwd;
    private Button   btnGroupWrite;
    // step 2 controls
    private Button   btnGroupScan;
    private View     llVerifyDisplay;
    private TextView tvVerifyG1, tvVerifyG2, tvVerifyG3, tvVerifyG4, tvVerifyG5, tvVerifyG6;
    private TextView tvVerifyEpc, tvVerifyTid;
    private EditText etGroupFileName;
    private Button   btnGroupSetFile;
    private TextView tvGroupFilePath, tvGroupRecordCount, tvGroupLastEpc;
    // step 3a controls: write password
    private EditText etGroupPwdCurrentPwd, etGroupNewPwd;
    private Button   btnGroupWritePwd;
    // step 3 controls
    private EditText etGroupLockPwd, etGroupLockCode;
    private Button   btnGroupLock;

    // ── Page 3: Uzamčení ──────────────────────────────────────────────────
    private EditText etPwdCurrentPwd, etPwdNewPwd;
    private Button   btnWritePwd;
    private EditText etLockAccessPwd, etLockCode;
    private Button   btnLock;
    private Button   btnToggleLockCodes;
    private View     llLockCodes;

    // ── Nastavení ─────────────────────────────────────────────────────────
    private com.google.android.material.button.MaterialButton btnToggleSettings;
    private View     llSettings;
    private SeekBar  sbOutputPower;
    private TextView tvOutputPowerValue;

    // ── State ─────────────────────────────────────────────────────────────
    private boolean mInventorying    = false;
    private boolean mRecordMode      = false;
    private int     mScanContext     = SCAN_CTX_TAG;
    private int     mGroupStep       = STEP_WRITE;
    private String  mGroupEpcWritten = "";   // EPC just written, needed for verify
    private String  mGroupOutputFile = null;
    private int     mGroupRecordCount = 0;
    private String  mLastTid         = "";
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
        loadSettings();
        loadGroupSettings();
        loadWrtGroupSettings();
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
            case 1: writeEpc();        break;
            case 2: lockTag();         break;  // tab 2 = Zamčení
            case 3: onGroupTrigger();  break;  // tab 3 = Skupinový
        }
    }

    private void onGroupTrigger() {
        switch (mGroupStep) {
            case STEP_WRITE: groupWrite();    break;
            case STEP_SCAN:
                if (mInventorying) stopScan(); else startGroupScan(); break;
            case STEP_LOCK:  groupLock();     break;
        }
    }

    // ── View binding ──────────────────────────────────────────────────────

    private void bindViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tabLayout = findViewById(R.id.tabLayout);
        pageTag = findViewById(R.id.pageTag);
        pageWriteEpc = findViewById(R.id.pageWriteEpc);
        pageGroupWrite = findViewById(R.id.pageGroupWrite);
        pageLock = findViewById(R.id.pageLock);
        svGroupPage = (android.widget.ScrollView) pageGroupWrite;

        // Page 0
        etChipNum = findViewById(R.id.etChipNum);
        btnScan = findViewById(R.id.btnScan);
        btnModeRecord = findViewById(R.id.btnModeRecord);
        btnModeRead = findViewById(R.id.btnModeRead);
        btnToggleChipNum = findViewById(R.id.btnToggleChipNum);
        tvScanEpc = findViewById(R.id.tvScanEpc);
        tvScanTid = findViewById(R.id.tvScanTid);
        tvStatPairs = findViewById(R.id.tvStatPairs);
        tvStatOk = findViewById(R.id.tvStatOk);
        tvStatBad = findViewById(R.id.tvStatBad);
        llStats = findViewById(R.id.llStats);
        llListHeader = findViewById(R.id.llListHeader);
        llRecords = findViewById(R.id.llRecords);
        llEmptyState = findViewById(R.id.llEmptyState);
        svRecords = findViewById(R.id.svRecords);
        llReadResult = findViewById(R.id.llReadResult);
        tvReadEpc = findViewById(R.id.tvReadEpc);
        tvReadTid = findViewById(R.id.tvReadTid);
        btnCopyRecords = findViewById(R.id.btnCopyRecords);
        btnExportCsv = findViewById(R.id.btnExportCsv);
        btnClearAll = findViewById(R.id.btnClearAll);

        // Page 1 template
        etWrtGroups[0] = findViewById(R.id.etWrt1); etWrtGroups[1] = findViewById(R.id.etWrt2);
        etWrtGroups[2] = findViewById(R.id.etWrt3); etWrtGroups[3] = findViewById(R.id.etWrt4);
        etWrtGroups[4] = findViewById(R.id.etWrt5); etWrtGroups[5] = findViewById(R.id.etWrt6);
        etWrtGroupNames[0] = findViewById(R.id.etWrtGrpName1);
        etWrtGroupNames[1] = findViewById(R.id.etWrtGrpName2);
        etWrtGroupNames[2] = findViewById(R.id.etWrtGrpName3);
        etWrtGroupNames[3] = findViewById(R.id.etWrtGrpName4);
        etWrtGroupNames[4] = findViewById(R.id.etWrtGrpName5);
        cbWrtGroups[0] = findViewById(R.id.cbWrt1); cbWrtGroups[1] = findViewById(R.id.cbWrt2);
        cbWrtGroups[2] = findViewById(R.id.cbWrt3); cbWrtGroups[3] = findViewById(R.id.cbWrt4);
        cbWrtGroups[4] = findViewById(R.id.cbWrt5); cbWrtGroups[5] = findViewById(R.id.cbWrt6);
        tvWrtEpcPreview    = findViewById(R.id.tvWrtEpcPreview);
        tvWrtPreviewLabel  = findViewById(R.id.tvWrtPreviewLabel);
        llWrtTemplateGroups = findViewById(R.id.llWrtTemplateGroups);
        btnToggleWrtTemplate = findViewById(R.id.btnToggleWrtTemplate);
        llWrtRow1 = findViewById(R.id.llWrtRow1); llWrtRow2 = findViewById(R.id.llWrtRow2);
        llWrtRow3 = findViewById(R.id.llWrtRow3); llWrtRow4 = findViewById(R.id.llWrtRow4);
        llWrtRow5 = findViewById(R.id.llWrtRow5); llWrtRow6 = findViewById(R.id.llWrtRow6);
        etWritePrt         = findViewById(R.id.etWritePrt);
        etWriteLen         = findViewById(R.id.etWriteLen);
        etWriteAccessPwd   = findViewById(R.id.etWriteAccessPwd);
        btnWrite           = findViewById(R.id.btnWrite);
        btnBankEpc         = findViewById(R.id.btnBankEpc);
        btnBankUser        = findViewById(R.id.btnBankUser);
        btnBankReserved    = findViewById(R.id.btnBankReserved);

        // Page 2
        etGroups[0] = findViewById(R.id.etGroup1); etGroups[1] = findViewById(R.id.etGroup2);
        etGroups[2] = findViewById(R.id.etGroup3); etGroups[3] = findViewById(R.id.etGroup4);
        etGroups[4] = findViewById(R.id.etGroup5); etGroups[5] = findViewById(R.id.etGroup6);
        etGroupNames[0] = findViewById(R.id.etGrpName1);
        etGroupNames[1] = findViewById(R.id.etGrpName2);
        etGroupNames[2] = findViewById(R.id.etGrpName3);
        etGroupNames[3] = findViewById(R.id.etGrpName4);
        etGroupNames[4] = findViewById(R.id.etGrpName5);
        cbGroups[0] = findViewById(R.id.cbGroup1); cbGroups[1] = findViewById(R.id.cbGroup2);
        cbGroups[2] = findViewById(R.id.cbGroup3); cbGroups[3] = findViewById(R.id.cbGroup4);
        cbGroups[4] = findViewById(R.id.cbGroup5); cbGroups[5] = findViewById(R.id.cbGroup6);
        tvEpcPreview           = findViewById(R.id.tvEpcPreview);
        tvGrpPreviewLabel      = findViewById(R.id.tvGrpPreviewLabel);
        btnToggleTemplate      = findViewById(R.id.btnToggleTemplate);
        llTemplateGroups       = findViewById(R.id.llTemplateGroups);
        btnGrpBankEpc          = findViewById(R.id.btnGrpBankEpc);
        btnGrpBankUser         = findViewById(R.id.btnGrpBankUser);
        btnGrpBankReserved     = findViewById(R.id.btnGrpBankReserved);
        etGroupPrt             = findViewById(R.id.etGroupPrt);
        etGroupLen             = findViewById(R.id.etGroupLen);
        llStep1Header  = findViewById(R.id.llStep1Header);
        llStep2Header  = findViewById(R.id.llStep2Header);
        llStep3Header  = findViewById(R.id.llStep3Header);
        tvStep1Num = findViewById(R.id.tvStep1Num); tvStep2Num = findViewById(R.id.tvStep2Num);
        tvStep3Num = findViewById(R.id.tvStep3Num);
        tvStep1Label = findViewById(R.id.tvStep1Label); tvStep2Label = findViewById(R.id.tvStep2Label);
        tvStep3Label = findViewById(R.id.tvStep3Label);
        etGroupAccessPwd   = findViewById(R.id.etGroupAccessPwd);
        btnGroupWrite      = findViewById(R.id.btnGroupWrite);
        btnGroupScan       = findViewById(R.id.btnGroupScan);
        llVerifyDisplay    = findViewById(R.id.llVerifyDisplay);
        tvVerifyG1 = findViewById(R.id.tvVerifyG1); tvVerifyG2 = findViewById(R.id.tvVerifyG2);
        tvVerifyG3 = findViewById(R.id.tvVerifyG3); tvVerifyG4 = findViewById(R.id.tvVerifyG4);
        tvVerifyG5 = findViewById(R.id.tvVerifyG5); tvVerifyG6 = findViewById(R.id.tvVerifyG6);
        tvVerifyEpc = findViewById(R.id.tvVerifyEpc); tvVerifyTid = findViewById(R.id.tvVerifyTid);
        etGroupFileName    = findViewById(R.id.etGroupFileName);
        btnGroupSetFile    = findViewById(R.id.btnGroupSetFile);
        tvGroupFilePath    = findViewById(R.id.tvGroupFilePath);
        tvGroupRecordCount = findViewById(R.id.tvGroupRecordCount);
        tvGroupLastEpc     = findViewById(R.id.tvGroupLastEpc);
        etGroupPwdCurrentPwd = findViewById(R.id.etGroupPwdCurrentPwd);
        etGroupNewPwd        = findViewById(R.id.etGroupNewPwd);
        btnGroupWritePwd     = findViewById(R.id.btnGroupWritePwd);
        etGroupLockPwd  = findViewById(R.id.etGroupLockPwd);
        etGroupLockCode = findViewById(R.id.etGroupLockCode);
        btnGroupLock    = findViewById(R.id.btnGroupLock);

        // Page 3
        etPwdCurrentPwd = findViewById(R.id.etPwdCurrentPwd);
        etPwdNewPwd     = findViewById(R.id.etPwdNewPwd);
        btnWritePwd     = findViewById(R.id.btnWritePwd);
        etLockAccessPwd = findViewById(R.id.etLockAccessPwd);
        etLockCode      = findViewById(R.id.etLockCode);
        btnLock         = findViewById(R.id.btnLock);
        btnToggleLockCodes = findViewById(R.id.btnToggleLockCodes);
        llLockCodes        = findViewById(R.id.llLockCodes);

        // Nastavení
        btnToggleSettings  = (com.google.android.material.button.MaterialButton) findViewById(R.id.btnToggleSettings);
        llSettings         = findViewById(R.id.llSettings);
        sbOutputPower      = findViewById(R.id.sbOutputPower);
        tvOutputPowerValue = findViewById(R.id.tvOutputPowerValue);
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
        pageLock.setVisibility(index == 2 ? View.VISIBLE : View.GONE);          // tab 2 = Zamčení
        pageGroupWrite.setVisibility(index == 3 ? View.VISIBLE : View.GONE);   // tab 3 = Skupinový

        if (index == 1) updateWrtEpcPreview();
        if (index == 3) updateGroupEpcPreview();
    }

    private void bindButtons() {
        // Nastavení toggle
        btnToggleSettings.setOnClickListener(v -> {
            boolean visible = llSettings.getVisibility() == View.VISIBLE;
            llSettings.setVisibility(visible ? View.GONE : View.VISIBLE);
            int tint = android.graphics.Color.parseColor(visible ? "#888888" : "#00BCD4");
            ((com.google.android.material.button.MaterialButton) btnToggleSettings)
                    .setIconTint(android.content.res.ColorStateList.valueOf(tint));
        });

        sbOutputPower.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tvOutputPowerValue.setText(progress + " dBm");
                if (fromUser) saveSettings();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {
                applyOutputPower(sb.getProgress());
            }
        });

        // Page 0
        btnScan.setOnClickListener(v -> { if (mInventorying) stopScan(); else startScan(); });
        btnModeRecord.setOnClickListener(v -> setRecordMode(true));
        btnModeRead.setOnClickListener(v -> setRecordMode(false));
        btnToggleChipNum.setOnClickListener(v -> {
            mAutoLoadIdRfid = !mAutoLoadIdRfid;
            btnToggleChipNum.setText(mAutoLoadIdRfid ? "🔄" : "🚫");
            toast(mAutoLoadIdRfid ? "ID_RFID: automatické načítání ZAP" : "ID_RFID: automatické načítání VYP");
        });
        btnCopyRecords.setOnClickListener(v -> copyRecords());
        btnExportCsv.setOnClickListener(v -> exportCsv());
        btnClearAll.setOnClickListener(v -> clearAll());

        // Page 1 — template
        btnToggleWrtTemplate.setOnClickListener(v -> {
            boolean visible = llWrtTemplateGroups.getVisibility() == View.VISIBLE;
            llWrtTemplateGroups.setVisibility(visible ? View.GONE : View.VISIBLE);
            updateWrtTemplateBtnText();
        });

        TextWatcher wrtWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateWrtEpcPreview(); }
        };
        for (EditText et : etWrtGroups) et.addTextChangedListener(wrtWatcher);

        TextWatcher wrtNameWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { saveWrtGroupNames(); }
        };
        for (EditText et : etWrtGroupNames) et.addTextChangedListener(wrtNameWatcher);

        for (int i = 0; i < 6; i++) {
            final int idx = i;
            cbWrtGroups[i].setOnCheckedChangeListener((b, checked) -> wrtGroupAutoInc[idx] = checked);
        }
        btnWrite.setOnClickListener(v -> writeEpc());

        // Bank selection
        btnBankEpc.setOnClickListener(v -> selectWriteBank(1));
        btnBankUser.setOnClickListener(v -> selectWriteBank(3));
        btnBankReserved.setOnClickListener(v -> selectWriteBank(0));

        // Update preview and template rows when Len changes
        etWriteLen.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateWrtEpcPreview();
                updateWrtTemplateRows();
            }
        });

        // Page 2
        btnToggleTemplate.setOnClickListener(v -> {
            boolean visible = llTemplateGroups.getVisibility() == View.VISIBLE;
            llTemplateGroups.setVisibility(visible ? View.GONE : View.VISIBLE);
            updateGrpTemplateBtnText();
        });

        TextWatcher grpWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateGroupEpcPreview(); }
        };
        for (EditText et : etGroups) et.addTextChangedListener(grpWatcher);

        TextWatcher grpNameWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { onGroupNameChanged(); }
        };
        for (EditText et : etGroupNames) et.addTextChangedListener(grpNameWatcher);

        for (int i = 0; i < 6; i++) {
            final int idx = i;
            cbGroups[i].setOnCheckedChangeListener((b, checked) -> groupAutoInc[idx] = checked);
        }
        btnGroupWrite.setOnClickListener(v -> groupWrite());
        btnGroupScan.setOnClickListener(v -> { if (mInventorying) stopScan(); else startGroupScan(); });
        btnGroupSetFile.setOnClickListener(v -> setGroupOutputFile());
        btnGroupWritePwd.setOnClickListener(v -> groupWritePwd());
        btnGroupLock.setOnClickListener(v -> groupLock());

        // Skupinový bank selection
        btnGrpBankEpc.setOnClickListener(v -> selectGroupWriteBank(1));
        btnGrpBankUser.setOnClickListener(v -> selectGroupWriteBank(3));
        btnGrpBankReserved.setOnClickListener(v -> selectGroupWriteBank(0));

        // Update skupinový preview and template rows when Len changes
        etGroupLen.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateGroupEpcPreview();
            }
        });

        // Page 3
        btnWritePwd.setOnClickListener(v -> writePwd());
        btnLock.setOnClickListener(v -> lockTag());
        btnToggleLockCodes.setOnClickListener(v -> {
            boolean visible = llLockCodes.getVisibility() == View.VISIBLE;
            llLockCodes.setVisibility(visible ? View.GONE : View.VISIBLE);
            btnToggleLockCodes.setText(visible ? "▶  PŘEHLED LOCK KÓDŮ" : "▼  PŘEHLED LOCK KÓDŮ");
        });
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
                        applyOutputPower(sbOutputPower.getProgress());
                    } else {
                        setStatus("● Chyba inicializace", "#F44336");
                        Log.e(TAG, "init() false");
                    }
                });
            } catch (Exception e) {
                mHandler.post(() -> setStatus("● SDK chyba: " + e.getMessage(), "#F44336"));
            }
        }).start();
    }

    // ── Nastavení (output power) ──────────────────────────────────────────

    private static final String KEY_OUTPUT_POWER = "output_power";

    private void loadSettings() {
        int power = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_OUTPUT_POWER, 20);
        sbOutputPower.setProgress(power);
        tvOutputPowerValue.setText(power + " dBm");
    }

    private void saveSettings() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_OUTPUT_POWER, sbOutputPower.getProgress()).apply();
    }

    private void applyOutputPower(int power) {
        if (mReader == null) return;
        new Thread(() -> {
            try {
                mReader.setPower(power);
            } catch (Exception e) {
                Log.w(TAG, "setPower: " + e.getMessage());
            }
        }).start();
    }

    // ── EPC template group names ──────────────────────────────────────────

    private static final String KEY_WRT_GROUP_NAMES = "wrt_group_names";
    private static final String KEY_GROUP_NAMES_P2  = "group_names_p2";
    private static final String[] DEFAULT_WRT_NAMES = {"-", "-", "-", "-", "-"};
    private static final String[] DEFAULT_GRP_NAMES = {"-", "-", "-", "-", "-"};

    private void loadWrtGroupNames() {
        try {
            String json = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_WRT_GROUP_NAMES, null);
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < 5 && i < arr.length(); i++)
                    etWrtGroupNames[i].setText(arr.getString(i));
            } else {
                for (int i = 0; i < 5; i++) etWrtGroupNames[i].setText(DEFAULT_WRT_NAMES[i]);
            }
        } catch (Exception ignored) {}
    }

    private void saveWrtGroupNames() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < 5; i++) arr.put(etWrtGroupNames[i].getText().toString());
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_WRT_GROUP_NAMES, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadGrpNames() {
        try {
            String json = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_GROUP_NAMES_P2, null);
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < 5 && i < arr.length(); i++)
                    etGroupNames[i].setText(arr.getString(i));
            } else {
                for (int i = 0; i < 5; i++) etGroupNames[i].setText(DEFAULT_GRP_NAMES[i]);
            }
        } catch (Exception ignored) {}
    }

    private void saveGrpNames() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < 5; i++) arr.put(etGroupNames[i].getText().toString());
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_GROUP_NAMES_P2, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void onGroupNameChanged() {
        saveGrpNames();
        if (mGroupOutputFile != null && mGroupRecordCount == 0) {
            try {
                File file = new File(mGroupOutputFile);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(buildGroupCsvHeader().getBytes("UTF-8"));
                }
            } catch (Exception e) {
                Log.e(TAG, "rewrite CSV header: " + e.getMessage());
            }
        }
    }

    private String buildGroupCsvHeader() {
        String g1 = etGroupNames[0].getText().toString().trim();
        String g2 = etGroupNames[1].getText().toString().trim();
        String g3 = etGroupNames[2].getText().toString().trim();
        String g4 = etGroupNames[3].getText().toString().trim();
        if (g1.isEmpty() || g1.equals("—")) g1 = "Skupina_1";
        if (g2.isEmpty() || g2.equals("—")) g2 = "Skupina_2";
        if (g3.isEmpty() || g3.equals("—")) g3 = "Skupina_3";
        if (g4.isEmpty() || g4.equals("—")) g4 = "Skupina_4";
        return "\uFEFFID_RFID,EPC,TID," + g1 + "," + g2 + "," + g3 + "," + g4 + "\n";
    }

    // ── Scan helpers ──────────────────────────────────────────────────────

    private void startScan() {
        if (mReader == null) return;
        mScanContext = SCAN_CTX_TAG;
        mInventorying = true;
        btnScan.setText("⏹  ZASTAVIT");
        btnScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")));
        setStatus("● Skenování...", "#00BCD4");
        startInventory();
    }

    private void startGroupScan() {
        if (mReader == null) return;
        mScanContext = SCAN_CTX_GROUP;
        mInventorying = true;
        btnGroupScan.setText("⏹  ZASTAVIT");
        btnGroupScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")));
        setStatus("● Ověřuji tag...", "#00BCD4");
        startInventory();
    }

    private void startInventory() {
        mReader.setInventoryCallback(new IUHFInventoryCallback() {
            @Override
            public void callback(UHFTAGInfo tag) {
                if (tag == null) return;
                String epc = tag.getEPC();
                String tid = tag.getTid();
                mHandler.post(() -> {
                    if (mScanContext == SCAN_CTX_GROUP) onGroupTagFound(epc, tid);
                    else onTagFound(epc, tid);
                });
            }
        });
        mReader.setEPCAndTIDMode();
        mReader.startInventoryTag();
    }

    private void stopScan() {
        if (mReader == null) return;
        mInventorying = false;
        mReader.stopInventory();
        if (mScanContext == SCAN_CTX_GROUP) {
            btnGroupScan.setText("📡  NAČÍST TAG");
            btnGroupScan.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2E2E2E")));
        } else {
            btnScan.setText("📡  SKENOVAT TAG");
            btnScan.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#00BCD4")));
        }
        setStatus("● Připojeno — Chainway C5", "#4CAF50");
    }

    // ── Tag found — Tab 0 context ─────────────────────────────────────────

    private void onTagFound(String epc, String tid) {
        stopScan();
        String epcStr = epc != null ? epc : "";
        String tidStr = tid != null ? tid : "";
        mLastTid = tidStr;
        tvScanEpc.setText(epcStr.isEmpty() ? "— žádné EPC —" : epcStr);
        tvScanTid.setText(tidStr.isEmpty() ? "— žádné TID —" : tidStr);
        if (mRecordMode) {
            savePair(epcStr, tidStr);
        } else {
            tvReadEpc.setText(epcStr.isEmpty() ? "—" : formatHexWithDashes(epcStr));
            tvReadTid.setText(tidStr.isEmpty() ? "—" : formatHexWithDashes(tidStr));
            if (mAutoLoadIdRfid && epcStr.length() >= 8) {
                String last8 = epcStr.substring(epcStr.length() - 8);
                String stripped = last8.replaceFirst("^0+", "");
                if (stripped.isEmpty()) stripped = "0";
                etChipNum.setText(stripped);
            }
        }
    }

    // ── Tag found — Tab 2 group workflow context ───────────────────────────

    private void onGroupTagFound(String epc, String tid) {
        stopScan();
        String epcStr = epc != null ? epc : "";
        String tidStr = tid != null ? tid : "";
        mLastTid = tidStr;
        parseAndDisplayGroupEpc(epcStr, tidStr);
    }

    private void parseAndDisplayGroupEpc(String epc, String tid) {
        if (epc.length() < 24) {
            toast("EPC příliš krátké pro analýzu");
            return;
        }
        String g1 = epc.substring(0, 4);
        String g2 = epc.substring(4, 8);
        String g3 = epc.substring(8, 12);
        String g4 = epc.substring(12, 16);
        String g5 = epc.substring(16, 20);
        String g6 = epc.substring(20, 24);

        tvVerifyG1.setText(g1); tvVerifyG2.setText(g2);
        tvVerifyG3.setText(g3); tvVerifyG4.setText(g4);
        tvVerifyG5.setText(g5); tvVerifyG6.setText(g6);

        String fmtEpc = g1+"-"+g2+"-"+g3+"-"+g4+"-"+g5+"-"+g6;
        tvVerifyEpc.setText(fmtEpc);
        tvVerifyTid.setText(tid.isEmpty() ? "—" : tid);

        llVerifyDisplay.setVisibility(View.VISIBLE);

        // Save to CSV
        appendToGroupCsv(g5 + g6, epc, tid, g1, g2, g3, g4);

        // Auto-fill lock pwd from write step
        String writePwd = readPassword(etGroupAccessPwd);
        if (!writePwd.equals("00000000")) etGroupLockPwd.setText(writePwd);

        // Advance to lock step
        setGroupStep(STEP_LOCK);
        toast("✅ Uloženo do CSV — pokračujte LOCK");
    }

    // ── Group workflow steps ───────────────────────────────────────────────

    private void setGroupStep(int step) {
        mGroupStep = step;

        int cyan   = Color.parseColor("#00BCD4");
        int green  = Color.parseColor("#4CAF50");
        int orange = Color.parseColor("#FF9800");
        int gray   = Color.parseColor("#888888");
        int dark   = Color.parseColor("#2E2E2E");

        // Step numbers: white when active/pending, green when completed
        tvStep1Num.setTextColor(step > STEP_WRITE ? green : Color.WHITE);
        tvStep2Num.setTextColor(step > STEP_SCAN  ? green : Color.WHITE);
        tvStep3Num.setTextColor(Color.WHITE);

        // Step 1
        int c1 = (step == STEP_WRITE) ? cyan : (step > STEP_WRITE ? green : gray);
        tvStep1Label.setTextColor(c1);
        btnGroupWrite.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                step == STEP_WRITE ? green : dark));

        // Step 2
        int c2 = (step == STEP_SCAN) ? cyan : (step > STEP_SCAN ? green : gray);
        tvStep2Label.setTextColor(c2);
        btnGroupScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                step == STEP_SCAN ? cyan : dark));

        // Step 3 (ZAHESLOVÁNÍ) + ZÁPIS HESLA sub-button
        int c3 = (step == STEP_LOCK) ? orange : gray;
        tvStep3Label.setTextColor(c3);
        btnGroupWritePwd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                step == STEP_LOCK ? green : dark));
        btnGroupWritePwd.setTextColor(step == STEP_LOCK
                ? Color.parseColor("#121212") : Color.parseColor("#888888"));
        btnGroupLock.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                step == STEP_LOCK ? orange : dark));

        // Auto-scroll to active section
        scrollToGroupStep(step);
    }

    private void scrollToGroupStep(int step) {
        if (svGroupPage == null) return;
        View target = (step == STEP_SCAN) ? llStep2Header
                    : (step == STEP_LOCK) ? llStep3Header
                    : llStep1Header;
        final View t = target;
        svGroupPage.post(() -> {
            int y = (t == llStep1Header) ? 0 : t.getTop();
            svGroupPage.smoothScrollTo(0, y);
        });
    }

    // ── Page 1 — EPC template (Zápis EPC) ────────────────────────────────

    private String buildWrtEpc() {
        StringBuilder sb = new StringBuilder();
        for (EditText et : etWrtGroups) {
            String v = et.getText().toString().toUpperCase(Locale.ROOT).trim();
            while (v.length() < 4) v = "0" + v;
            if (v.length() > 4) v = v.substring(0, 4);
            sb.append(v);
        }
        return sb.toString();
    }

    private void updateWrtEpcPreview() {
        if (tvWrtEpcPreview == null) return;
        String epc = buildWrtEpc();
        if (epc.length() != 24) return;

        if (mWriteBank == 1) {
            if (tvWrtPreviewLabel != null) tvWrtPreviewLabel.setText("náhled EPC");
            tvWrtEpcPreview.setText(
                epc.substring(0,4)+"-"+epc.substring(4,8)+"-"+
                epc.substring(8,12)+"-"+epc.substring(12,16)+"-"+
                epc.substring(16,20)+"-"+epc.substring(20,24));
        } else {
            String label = (mWriteBank == 3) ? "náhled USER" : "náhled RESERVED";
            if (tvWrtPreviewLabel != null) tvWrtPreviewLabel.setText(label);
            int len = 6;
            try { len = Integer.parseInt(etWriteLen.getText().toString().trim()); }
            catch (NumberFormatException ignored) {}
            if (len < 1) len = 1;
            if (len > 6) len = 6;
            StringBuilder preview = new StringBuilder();
            for (int i = 0; i < len; i++) {
                if (i > 0) preview.append("-");
                preview.append(epc, i * 4, i * 4 + 4);
            }
            tvWrtEpcPreview.setText(preview.toString());
        }
    }

    private void selectWriteBank(int bank) {
        mWriteBank = bank;
        int active   = android.graphics.Color.parseColor("#00BCD4");
        int inactive = android.graphics.Color.parseColor("#2E2E2E");
        btnBankEpc.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 1 ? active : inactive));
        btnBankEpc.setTextColor(bank == 1
                ? android.graphics.Color.parseColor("#121212")
                : android.graphics.Color.parseColor("#E0E0E0"));
        btnBankUser.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 3 ? active : inactive));
        btnBankUser.setTextColor(bank == 3
                ? android.graphics.Color.parseColor("#121212")
                : android.graphics.Color.parseColor("#E0E0E0"));
        btnBankReserved.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 0 ? active : inactive));
        btnBankReserved.setTextColor(bank == 0
                ? android.graphics.Color.parseColor("#121212")
                : android.graphics.Color.parseColor("#E0E0E0"));
        updateWrtEpcPreview();
        updateWrtTemplateBtnText();
        updateWrtTemplateRows();
    }

    private String getWrtTemplateLabel() {
        if (mWriteBank == 1) return "EPC šablona";
        if (mWriteBank == 3) return "USER šablona";
        return "RESERVED šablona";
    }

    private void updateWrtTemplateBtnText() {
        if (btnToggleWrtTemplate == null) return;
        boolean visible = llWrtTemplateGroups != null
                && llWrtTemplateGroups.getVisibility() == View.VISIBLE;
        String label = getWrtTemplateLabel();
        btnToggleWrtTemplate.setText(visible ? "▼  " + label : "▶  " + label + "  (rozbalit)");
    }

    private void updateWrtTemplateRows() {
        if (mWriteBank == 1) {
            // EPC bank — always show all 6 rows
            showWrtRows(6);
            return;
        }
        int len = 6;
        try { len = Integer.parseInt(etWriteLen.getText().toString().trim()); }
        catch (NumberFormatException ignored) {}
        if (len < 1) len = 1;
        if (len > 6) len = 6;
        showWrtRows(len);
    }

    private void showWrtRows(int count) {
        View[] rows = {llWrtRow1, llWrtRow2, llWrtRow3, llWrtRow4, llWrtRow5, llWrtRow6};
        for (int i = 0; i < 6; i++) {
            if (rows[i] != null) rows[i].setVisibility(i < count ? View.VISIBLE : View.GONE);
        }
    }

    private void autoIncrementWrtGroups() {
        for (int i = 0; i < 6; i++) {
            if (!wrtGroupAutoInc[i]) continue;
            String val = etWrtGroups[i].getText().toString().toUpperCase(Locale.ROOT).trim();
            String next;
            if (i == 5) {
                try { next = String.format("%04d", (Integer.parseInt(val) + 1) % 1000); }
                catch (NumberFormatException e) { next = "0001"; }
                etChipNum.setText(next);
            } else {
                try { next = String.format("%04X", (Integer.parseInt(val, 16) + 1) & 0xFFFF); }
                catch (NumberFormatException e) { next = "0001"; }
            }
            etWrtGroups[i].setText(next);
        }
        updateWrtEpcPreview();
        saveWrtGroupSettings();
    }

    private void loadWrtGroupSettings() {
        try {
            String vJson = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("wrt_values", null);
            if (vJson != null) {
                JSONArray arr = new JSONArray(vJson);
                for (int i = 0; i < 6 && i < arr.length(); i++)
                    etWrtGroups[i].setText(arr.getString(i));
            }
            String iJson = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString("wrt_autoinc", null);
            if (iJson != null) {
                JSONArray arr = new JSONArray(iJson);
                for (int i = 0; i < 6 && i < arr.length(); i++) {
                    wrtGroupAutoInc[i] = arr.getBoolean(i);
                    cbWrtGroups[i].setChecked(wrtGroupAutoInc[i]);
                }
            }
        } catch (Exception ignored) {}
        loadWrtGroupNames();
        updateWrtEpcPreview();
        updateWrtTemplateBtnText();
        updateWrtTemplateRows();
    }

    private void saveWrtGroupSettings() {
        try {
            JSONArray vArr = new JSONArray(); JSONArray iArr = new JSONArray();
            for (int i = 0; i < 6; i++) {
                vArr.put(etWrtGroups[i].getText().toString());
                iArr.put(wrtGroupAutoInc[i]);
            }
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("wrt_values", vArr.toString())
                    .putString("wrt_autoinc", iArr.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "saveWrt: " + e.getMessage()); }
    }

    // ── Write EPC (Page 1) ────────────────────────────────────────────────

    private void writeEpc() {
        if (mReader == null) return;
        String templateEpc = buildWrtEpc();
        if (!templateEpc.matches("[0-9A-F]{24}")) { toast("Neplatné hex znaky v šabloně"); return; }

        int prt, len;
        try {
            prt = Integer.parseInt(etWritePrt.getText().toString().trim());
            len = Integer.parseInt(etWriteLen.getText().toString().trim());
        } catch (NumberFormatException e) { toast("Neplatné Prt / Len"); return; }

        // Build write data: exactly len * 4 hex chars
        int dataLen = len * 4;
        String data;
        if (dataLen <= 24) {
            data = templateEpc.substring(0, dataLen);
        } else {
            StringBuilder sb = new StringBuilder(templateEpc);
            while (sb.length() < dataLen) sb.append("0000");
            data = sb.substring(0, dataLen);
        }

        String accessPwd = readPassword(etWriteAccessPwd);
        String bankName = mWriteBank == 1 ? "EPC" : mWriteBank == 3 ? "USER" : "RESERVED";
        setStatus("● Zapisuji " + bankName + "...", "#FF9800");
        btnWrite.setEnabled(false);

        final int fp = prt, fl = len, fb = mWriteBank;
        final String fd = data, fe = templateEpc;
        new Thread(() -> {
            boolean ok = mReader.writeData(accessPwd, fb, fp, fl, fd);
            mHandler.post(() -> {
                btnWrite.setEnabled(true);
                if (ok) {
                    if (fb == 1) tvScanEpc.setText(fe);
                    setStatus("● " + bankName + " zapsáno OK", "#4CAF50");
                    toast(bankName + " zapsáno!");
                    if (fb == 1) autoIncrementWrtGroups();
                } else {
                    setStatus("● Zápis " + bankName + " selhal", "#F44336");
                    toast("Zápis selhal");
                }
            });
        }).start();
    }

    // ── Page 2 — EPC template ─────────────────────────────────────────────

    private String buildGroupEpc() {
        StringBuilder sb = new StringBuilder();
        for (EditText et : etGroups) {
            String v = et.getText().toString().toUpperCase(Locale.ROOT).trim();
            while (v.length() < 4) v = "0" + v;
            if (v.length() > 4) v = v.substring(0, 4);
            sb.append(v);
        }
        return sb.toString();
    }

    private void updateGroupEpcPreview() {
        if (tvEpcPreview == null) return;
        String epc = buildGroupEpc();
        if (epc.length() != 24) return;

        if (mGroupWriteBank == 1) {
            if (tvGrpPreviewLabel != null) tvGrpPreviewLabel.setText("EPC NÁHLED");
            tvEpcPreview.setText(epc.substring(0,4)+"-"+epc.substring(4,8)+"-"+
                epc.substring(8,12)+"-"+epc.substring(12,16)+"-"+
                epc.substring(16,20)+"-"+epc.substring(20,24));
        } else {
            String label = (mGroupWriteBank == 3) ? "USER NÁHLED" : "RESERVED NÁHLED";
            if (tvGrpPreviewLabel != null) tvGrpPreviewLabel.setText(label);
            int len = 6;
            try { len = Integer.parseInt(etGroupLen.getText().toString().trim()); }
            catch (NumberFormatException ignored) {}
            if (len < 1) len = 1;
            if (len > 6) len = 6;
            StringBuilder preview = new StringBuilder();
            for (int i = 0; i < len; i++) {
                if (i > 0) preview.append("-");
                preview.append(epc, i * 4, i * 4 + 4);
            }
            tvEpcPreview.setText(preview.toString());
        }
    }

    private void selectGroupWriteBank(int bank) {
        mGroupWriteBank = bank;
        int active   = android.graphics.Color.parseColor("#00BCD4");
        int inactive = android.graphics.Color.parseColor("#2E2E2E");
        btnGrpBankEpc.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 1 ? active : inactive));
        btnGrpBankEpc.setTextColor(bank == 1
                ? android.graphics.Color.parseColor("#121212")
                : android.graphics.Color.parseColor("#E0E0E0"));
        btnGrpBankUser.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 3 ? active : inactive));
        btnGrpBankUser.setTextColor(bank == 3
                ? android.graphics.Color.parseColor("#121212")
                : android.graphics.Color.parseColor("#E0E0E0"));
        btnGrpBankReserved.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 0 ? active : inactive));
        btnGrpBankReserved.setTextColor(bank == 0
                ? android.graphics.Color.parseColor("#121212")
                : android.graphics.Color.parseColor("#E0E0E0"));

        // Update Prt/Len defaults for bank
        if (bank == 1) {
            etGroupPrt.setText("2");
            etGroupLen.setText("6");
        }
        updateGroupEpcPreview();
        updateGrpTemplateBtnText();
    }

    private String getGrpTemplateLabel() {
        if (mGroupWriteBank == 1) return "EPC šablona";
        if (mGroupWriteBank == 3) return "USER šablona";
        return "RESERVED šablona";
    }

    private void updateGrpTemplateBtnText() {
        if (btnToggleTemplate == null) return;
        boolean visible = llTemplateGroups != null
                && llTemplateGroups.getVisibility() == View.VISIBLE;
        String label = getGrpTemplateLabel();
        btnToggleTemplate.setText(visible ? "▼  " + label + "  (sbalit)" : "▶  " + label + "  (rozbalit)");
    }

    private void autoIncrementGroups() {
        for (int i = 0; i < 6; i++) {
            if (!groupAutoInc[i]) continue;
            String val = etGroups[i].getText().toString().toUpperCase(Locale.ROOT).trim();
            String next;
            if (i == 5) {
                try { next = String.format("%04d", (Integer.parseInt(val) + 1) % 1000); }
                catch (NumberFormatException e) { next = "0001"; }
                etChipNum.setText(next);
            } else {
                try { next = String.format("%04X", (Integer.parseInt(val, 16) + 1) & 0xFFFF); }
                catch (NumberFormatException e) { next = "0001"; }
            }
            etGroups[i].setText(next);
        }
        updateGroupEpcPreview();
        saveGroupSettings();
    }

    // ── Group Write — Step 1 ──────────────────────────────────────────────

    private void groupWrite() {
        if (mReader == null) return;
        String templateEpc = buildGroupEpc();
        if (!templateEpc.matches("[0-9A-F]{24}")) { toast("EPC obsahuje neplatné znaky"); return; }

        int prt, len;
        try {
            prt = Integer.parseInt(etGroupPrt.getText().toString().trim());
            len = Integer.parseInt(etGroupLen.getText().toString().trim());
        } catch (NumberFormatException e) { toast("Neplatné Prt / Len"); return; }

        // Build write data: exactly len * 4 hex chars
        int dataLen = len * 4;
        String data;
        if (dataLen <= 24) {
            data = templateEpc.substring(0, dataLen);
        } else {
            StringBuilder sb = new StringBuilder(templateEpc);
            while (sb.length() < dataLen) sb.append("0000");
            data = sb.substring(0, dataLen);
        }

        String accessPwd = readPassword(etGroupAccessPwd);
        mGroupEpcWritten = templateEpc;

        String bankName = mGroupWriteBank == 1 ? "EPC" : mGroupWriteBank == 3 ? "USER" : "RESERVED";
        setStatus("● Skupinový zápis " + bankName + "...", "#FF9800");
        btnGroupWrite.setEnabled(false);

        final int fp = prt, fl = len, fb = mGroupWriteBank;
        final String fd = data, fe = templateEpc;
        new Thread(() -> {
            boolean ok = mReader.writeData(accessPwd, fb, fp, fl, fd);
            mHandler.post(() -> {
                btnGroupWrite.setEnabled(true);
                if (ok) {
                    setStatus("● " + bankName + " zapsáno — naskenujte tag", "#4CAF50");
                    toast("✅ " + bankName + " zapsáno — stiskněte NAČÍST TAG");
                    if (fb == 1) setGroupStep(STEP_SCAN);
                    else {
                        // For USER/RESERVED bank, auto-increment and reset to write step
                        autoIncrementGroups();
                        setGroupStep(STEP_WRITE);
                    }
                } else {
                    setStatus("● Skupinový zápis " + bankName + " selhal", "#F44336");
                    toast("Zápis selhal");
                }
            });
        }).start();
    }

    // ── Group Write Password — Step 3a ────────────────────────────────────

    private void groupWritePwd() {
        if (mReader == null) return;
        String cur = readPassword(etGroupPwdCurrentPwd);
        String np  = etGroupNewPwd.getText().toString().trim().toUpperCase();
        if (np.isEmpty() || np.length() != 8 || !np.matches("[0-9A-F]+")) {
            toast("Platné heslo: přesně 8 hex znaků"); return;
        }
        setStatus("● Zapisuji heslo...", "#FF9800");
        btnGroupWritePwd.setEnabled(false);
        new Thread(() -> {
            boolean ok = mReader.writeData(cur, 0, 2, 2, np);
            mHandler.post(() -> {
                btnGroupWritePwd.setEnabled(true);
                if (ok) {
                    setStatus("● Heslo zapsáno", "#4CAF50");
                    etGroupLockPwd.setText(np);
                    toast("Heslo zapsáno!");
                } else {
                    setStatus("● Zápis hesla selhal", "#F44336");
                    toast("Zápis hesla selhal");
                }
            });
        }).start();
    }

    // ── Group Verify — Step 2 ─────────────────────────────────────────────

    // (see startGroupScan / onGroupTagFound / parseAndDisplayGroupEpc above)

    // ── Group Lock — Step 3 ───────────────────────────────────────────────

    private void groupLock() {
        if (mReader == null) return;
        String accessPwd = readPassword(etGroupLockPwd);
        String lockCode  = etGroupLockCode.getText().toString().trim().toUpperCase();
        if (lockCode.isEmpty()) lockCode = "008020";

        setStatus("● Zaheslovávám tag...", "#FF9800");
        btnGroupLock.setEnabled(false);

        final String fc = lockCode;
        new Thread(() -> {
            boolean ok = mReader.lockMem(accessPwd, fc);
            mHandler.post(() -> {
                btnGroupLock.setEnabled(true);
                if (ok) {
                    setStatus("● Tag zaheslován ✓", "#4CAF50");
                    toast("✅ Tag zaheslován — připraven další");
                    // Reset for next tag
                    llVerifyDisplay.setVisibility(View.GONE);
                    autoIncrementGroups();
                    setGroupStep(STEP_WRITE);
                } else {
                    setStatus("● Lock selhal", "#F44336");
                    toast("Lock selhal");
                }
            });
        }).start();
    }

    // ── Group output file ─────────────────────────────────────────────────

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
                    fos.write(buildGroupCsvHeader().getBytes("UTF-8"));
                }
                mGroupRecordCount = 0;
            }
            mGroupOutputFile = file.getAbsolutePath();
            tvGroupFilePath.setText(file.getName() + "\n" + file.getParent());
            tvGroupRecordCount.setText(String.valueOf(mGroupRecordCount));
            saveGroupSettings();
            toast("✅ " + file.getName());
        } catch (Exception e) { toast("Chyba: " + e.getMessage()); }
    }

    private void appendToGroupCsv(String idRfid, String epc, String tid,
                                   String g1, String g2, String g3, String g4) {
        if (mGroupOutputFile == null) { toast("⚠️ Nastavte výstupní soubor"); return; }
        mGroupRecordCount++;
        String line = escCsv(idRfid) + "," + epc + "," + tid + ","
                + g1 + "," + g2 + "," + g3 + "," + g4 + "\n";
        try (FileOutputStream fos = new FileOutputStream(mGroupOutputFile, true)) {
            fos.write(line.getBytes("UTF-8"));
        } catch (Exception e) {
            toast("Chyba zápisu: " + e.getMessage());
            mGroupRecordCount--;
            return;
        }
        String fmtEpc = epc.substring(0,4)+"-"+epc.substring(4,8)+"-"+epc.substring(8,12)
                +"-"+epc.substring(12,16)+"-"+epc.substring(16,20)+"-"+epc.substring(20,24);
        tvGroupRecordCount.setText(String.valueOf(mGroupRecordCount));
        tvGroupLastEpc.setText(fmtEpc);
        saveGroupSettings();
    }

    // ── Group settings persistence ────────────────────────────────────────

    private void loadGroupSettings() {
        mGroupOutputFile  = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("group_file", null);
        mGroupRecordCount = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("group_count", 0);
        try {
            String vJson = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("group_values", null);
            if (vJson != null) {
                JSONArray arr = new JSONArray(vJson);
                for (int i = 0; i < 6 && i < arr.length(); i++) etGroups[i].setText(arr.getString(i));
            }
            String iJson = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("group_autoinc", null);
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
        loadGrpNames();
        selectGroupWriteBank(mGroupWriteBank);
        updateGrpTemplateBtnText();
        setGroupStep(STEP_WRITE);
    }

    private void saveGroupSettings() {
        try {
            JSONArray vArr = new JSONArray(); JSONArray iArr = new JSONArray();
            for (int i = 0; i < 6; i++) { vArr.put(etGroups[i].getText().toString()); iArr.put(groupAutoInc[i]); }
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("group_file",   mGroupOutputFile)
                    .putInt("group_count",      mGroupRecordCount)
                    .putString("group_values",  vArr.toString())
                    .putString("group_autoinc", iArr.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "saveGroup: " + e.getMessage()); }
    }

    // ── Page 0 record management ──────────────────────────────────────────

    private void setRecordMode(boolean record) {
        mRecordMode = record;
        int active = Color.parseColor("#00BCD4"), inactive = Color.parseColor("#2E2E2E");
        btnModeRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(record ? active : inactive));
        btnModeRead.setBackgroundTintList(android.content.res.ColorStateList.valueOf(record ? inactive : active));
        int rv = record ? View.VISIBLE : View.GONE;
        llStats.setVisibility(rv); llListHeader.setVisibility(rv); svRecords.setVisibility(rv);
        llReadResult.setVisibility(record ? View.GONE : View.VISIBLE);
    }

    private void savePair(String epc, String tid) {
        String chipNum = etChipNum.getText().toString().trim();
        String time    = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        boolean complete = !epc.isEmpty() && !tid.isEmpty();
        boolean isDup = false; ScanRecord dupRecord = null;
        if (!epc.isEmpty()) for (ScanRecord r : mRecords) if (epc.equals(r.epc)) { isDup = true; dupRecord = r; break; }
        if (!isDup && !tid.isEmpty()) for (ScanRecord r : mRecords) if (tid.equals(r.tid)) { isDup = true; dupRecord = r; break; }
        ScanRecord rec = new ScanRecord();
        rec.seq = mRecords.size() + 1; rec.num = chipNum; rec.epc = epc;
        rec.tid = tid; rec.time = time; rec.complete = complete; rec.dup = isDup;
        mRecords.add(rec);
        try { int n = Integer.parseInt(chipNum); String nx = String.valueOf(n+1); etChipNum.setText(nx); etChipNum.setSelection(nx.length()); } catch (NumberFormatException ignored) {}
        toast(isDup ? "⚠️ Duplikát" : complete ? "✅ #" + rec.seq + " uložen" : "⚠️ #" + rec.seq + " — chybí data");
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
        new AlertDialog.Builder(this).setTitle("Smazat záznamy?")
                .setMessage("Opravdu smazat všech " + mRecords.size() + " záznamů?")
                .setPositiveButton("Smazat", (d, w) -> { mRecords.clear(); saveRecords(); renderRecordList(); updateStats(); })
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
            TextView tvE = row.findViewById(R.id.tvItemEpc);
            tvE.setText(r.epc.isEmpty() ? "—" : r.epc);
            tvE.setTextColor(Color.parseColor(r.epc.isEmpty() ? "#e3b341" : "#00BCD4"));
            TextView tvT = row.findViewById(R.id.tvItemTid);
            tvT.setText(r.tid.isEmpty() ? "—" : r.tid);
            tvT.setTextColor(Color.parseColor(r.tid.isEmpty() ? "#e3b341" : "#4CAF50"));
            row.setBackgroundColor(Color.parseColor(r.dup ? "#2a1015" : "#0d1117"));
            View div = new View(this);
            div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            div.setBackgroundColor(Color.parseColor("#21262d"));
            final int oi = i;
            row.findViewById(R.id.btnItemDel).setOnClickListener(v -> deleteRecord(oi));
            llRecords.addView(row); llRecords.addView(div);
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

    private void saveRecords() {
        try {
            JSONArray arr = new JSONArray();
            for (ScanRecord r : mRecords) {
                JSONObject o = new JSONObject();
                o.put("seq",r.seq);o.put("num",r.num);o.put("epc",r.epc);o.put("tid",r.tid);
                o.put("time",r.time);o.put("complete",r.complete);o.put("dup",r.dup);arr.put(o);
            }
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RECORDS, arr.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "saveRec: "+e.getMessage()); }
    }

    private static final String KEY_RECORDS = "records";

    private void loadRecords() {
        try {
            String json = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECORDS, "[]");
            JSONArray arr = new JSONArray(json); mRecords.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i); ScanRecord r = new ScanRecord();
                r.seq=o.getInt("seq");r.num=o.getString("num");r.epc=o.getString("epc");
                r.tid=o.getString("tid");r.time=o.getString("time");
                r.complete=o.getBoolean("complete");r.dup=o.getBoolean("dup");mRecords.add(r);
            }
            if (!mRecords.isEmpty()) {
                try { int n=Integer.parseInt(mRecords.get(mRecords.size()-1).num); String nx=String.valueOf(n+1); etChipNum.setText(nx); etChipNum.setSelection(nx.length()); } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) { Log.e(TAG, "loadRec: "+e.getMessage()); }
    }

    private void exportCsv() {
        if (mRecords.isEmpty()) { toast("Žádné záznamy!"); return; }
        StringBuilder sb = new StringBuilder("\uFEFF"); sb.append("Seq,ID_RFID,EPC,TID,Stav,Cas\n");
        for (ScanRecord r : mRecords)
            sb.append(r.seq).append(",").append(escCsv(r.num)).append(",")
              .append(escCsv(r.epc)).append(",").append(escCsv(r.tid)).append(",")
              .append(r.complete?"OK":"NEUPLNE").append(",").append(escCsv(r.time)).append("\n");
        try {
            String stamp=new SimpleDateFormat("yyyyMMdd_HHmm",Locale.getDefault()).format(new Date());
            File dir=getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if(dir==null)dir=getFilesDir(); if(!dir.exists())dir.mkdirs();
            File file=new File(dir,"rfid_"+stamp+".csv");
            try(FileOutputStream fos=new FileOutputStream(file)){fos.write(sb.toString().getBytes("UTF-8"));}
            toast("✅ CSV: "+file.getName());
        } catch (Exception e) { toast("Chyba: "+e.getMessage()); }
    }

    private void copyRecords() {
        if (mRecords.isEmpty()) { toast("Žádné záznamy!"); return; }
        StringBuilder sb=new StringBuilder("Seq\tID_RFID\tEPC\tTID\tStav\tCas\n");
        for (ScanRecord r : mRecords)
            sb.append(r.seq).append("\t").append(r.num).append("\t").append(r.epc).append("\t")
              .append(r.tid).append("\t").append(r.complete?"OK":"NEUPLNE").append("\t").append(r.time).append("\n");
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null){cm.setPrimaryClip(ClipData.newPlainText("RFID",sb.toString()));toast("📋 Zkopírováno");}
    }

    // ── Page 3: Zápis hesla / Zamčení ────────────────────────────────────

    private void writePwd() {
        if (mReader == null) return;
        String cur = readPassword(etPwdCurrentPwd);
        String np  = etPwdNewPwd.getText().toString().trim().toUpperCase();
        if (np.isEmpty() || np.length() != 8 || !np.matches("[0-9A-F]+")) {
            toast("Platné heslo: přesně 8 hex znaků"); return;
        }
        setStatus("● Zapisuji heslo...", "#FF9800"); btnWritePwd.setEnabled(false);
        new Thread(() -> {
            boolean ok = mReader.writeData(cur, 0, 2, 2, np);
            mHandler.post(() -> {
                btnWritePwd.setEnabled(true);
                if (ok) { setStatus("● Heslo zapsáno", "#4CAF50"); etLockAccessPwd.setText(np); toast("Heslo zapsáno!"); }
                else    { setStatus("● Zápis hesla selhal", "#F44336"); toast("Zápis hesla selhal"); }
            });
        }).start();
    }

    private void lockTag() {
        if (mReader == null) return;
        String ap = readPassword(etLockAccessPwd);
        String lc = etLockCode.getText().toString().trim().toUpperCase();
        if (lc.isEmpty()) lc = "008020";
        setStatus("● Zamykám...", "#FF9800"); btnLock.setEnabled(false);
        final String fc = lc;
        new Thread(() -> {
            boolean ok = mReader.lockMem(ap, fc);
            mHandler.post(() -> {
                btnLock.setEnabled(true);
                if (ok) { setStatus("● Tag zamčen", "#4CAF50"); toast("Tag zamčen!"); }
                else    { setStatus("● Lock selhal", "#F44336"); toast("Lock selhal"); }
            });
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String readPassword(EditText field) {
        String v = field.getText().toString().trim().toUpperCase();
        if (v.length() == 8 && v.matches("[0-9A-F]+")) return v;
        return "00000000";
    }

    private void setStatus(String text, String hexColor) {
        tvStatus.setText(text); tvStatus.setTextColor(Color.parseColor(hexColor));
    }

    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }

    private String escCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private String formatHexWithDashes(String hex) {
        if (hex == null || hex.length() < 4) return hex != null ? hex : "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) sb.append("-");
            sb.append(hex, i, Math.min(i + 4, hex.length()));
        }
        return sb.toString();
    }
}
