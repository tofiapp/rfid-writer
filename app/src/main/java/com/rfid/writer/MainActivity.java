package com.rfid.writer;

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
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RFIDWriter";
    private static final int    TRIGGER_KEYCODE = 293;
    private static final String PREFS           = "rfid_records";

    // scan context: which tab triggered the scan
    private static final int SCAN_CTX_LUPA     = 0;
    private static final int SCAN_CTX_LUPA_CSV = 1;
    private static final int SCAN_CTX_GROUP    = 2;

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
    private View      pageLupa, pageLupaCsv, pageWriteEpc, pageGroupWrite, pageLock;
    private android.widget.ScrollView svGroupPage;

    // ── Page 0: Lupa (info o čipu) ──────────────────────────────────────
    private Button btnScanLupa;
    private LinearLayout llChipInfoContainer;

    // ── Page 1: Lupa .CSV ─────────────────────────────────────────────────
    private Button btnScanCsv;
    private TextView tvReadEpc, tvReadTid;
    private EditText etLupaFileName;
    private Button   btnLupaSetFile;
    private TextView tvLupaFilePath;
    private View     llLupaFileInput;
    private View     llLupaGroups;
    private LinearLayout llLupaGroupsContainer;
    private String   mLupaInputFile  = null;
    private final java.util.HashMap<String, String[]> mLupaCsvData = new java.util.HashMap<>();
    private String[] mLupaColumnNames = new String[0];
    private int      mLupaAttrColumnCount = 0;

    private TextView tvScanEpc, tvScanTid;

    // ── Page 2: Zápis EPC — template ──────────────────────────────────────
    private final EditText[]  etWrtGroups    = new EditText[6];
    private final EditText[]  etWrtGroupNames = new EditText[6]; // groups 1-6, all editable
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
    private static final int GRP_ROW_COUNT = 8;
    private static final int GRP_PRESET_STANDARD = 0;
    private static final int GRP_PRESET_1 = 1;
    private static final int GRP_PRESET_2 = 2;
    private static final int GRP_PRESET_ROW_COUNT = 7;
    private static final int[] PRESET_FIELD_LENS = {4, 4, 2, 2, 3, 1, 8};
    private static final String[] PRESET1_NAMES = {
            "Rok", "TUDU 1", "TUDU 2 ASCII", "TUDU 2", "Výhybka", "Část", "ID_RFID"};
    private static final String[] PRESET2_NAMES = {
            "Rok", "TUDU 1", "TUDU 2 ASCII", "TUDU 2", "Výhybka", "", "ID_RFID"};

    private final EditText[]  etGroups      = new EditText[GRP_ROW_COUNT];
    private final EditText[]  etGroupNames  = new EditText[GRP_ROW_COUNT];
    private final CheckBox[]  cbGroups      = new CheckBox[GRP_ROW_COUNT];
    private final boolean[]   groupAutoInc  = {false, false, false, false, false, true, true, false};
    private final View[]      llGrpRows     = new View[GRP_ROW_COUNT];
    private final TextView[]  tvVerifyLbl   = new TextView[GRP_ROW_COUNT];
    private final TextView[]  tvVerifyG     = new TextView[GRP_ROW_COUNT];
    private final View[]      llVerifyRows  = new View[GRP_ROW_COUNT];
    private int     mGroupPresetMode = GRP_PRESET_STANDARD;
    private boolean mPresetAuto56    = false;
    private Button  btnGrpPresetStd, btnGrpPreset1, btnGrpPreset2;
    private CheckBox cbPresetAuto56;
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
    private TextView tvAppVersion;

    // ── State ─────────────────────────────────────────────────────────────
    private boolean mInventorying    = false;
    private int     mScanContext     = SCAN_CTX_LUPA;
    private int     mGroupStep       = STEP_WRITE;
    // Sub-steps within ZAMČENÍ tab (0=ZÁPIS HESLA, 1=ZAMČENÍ)
    private int     mLockSubStep     = 0;
    // Sub-steps within SKUPINOVÝ STEP_LOCK (0=ZÁPIS HESLA, 1=LOCK)
    private int     mGroupLockSubStep = 0;
    private String  mGroupEpcWritten = "";   // EPC just written, needed for verify
    private String  mGroupOutputFile = null;
    private int     mGroupRecordCount = 0;
    private String  mLastTid         = "";
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupTabs();
        bindButtons();
        loadSettings();
        loadGroupSettings();
        loadWrtGroupSettings();
        loadLupaSettings();
        cleanupLegacyTuduPrefs();
        initReader();
    }

    /** Odstraní uložená nastavení ze zrušené záložky/karty TUDU. */
    private void cleanupLegacyTuduPrefs() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("tudu_source")
                .remove("tudu_file_name")
                .remove("tudu_selected")
                .remove("tudu_vyhybka_idx")
                .remove("tudu_wf_values")
                .remove("tudu_wf_autoinc")
                .remove("tudu_wf_file")
                .remove("tudu_wf_count")
                .apply();
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
            case 0: if (mInventorying) stopScan(); else startLupaScan(); break;
            case 1: if (mInventorying) stopScan(); else startCsvScan(); break;
            case 2: writeEpc(); break;
            case 3: // Zamčení: sub-step 0 = ZÁPIS HESLA, sub-step 1 = ZAMČENÍ
                if (mLockSubStep == 0) writePwd(); else lockTag(); break;
            case 4: onGroupTrigger(); break;  // Skupinový
        }
    }

    private void onGroupTrigger() {
        switch (mGroupStep) {
            case STEP_WRITE: groupWrite(); break;
            case STEP_SCAN:
                if (mInventorying) stopScan(); else startGroupScan(); break;
            case STEP_LOCK:
                // Sub-step 0 = ZÁPIS HESLA, sub-step 1 = LOCK
                if (mGroupLockSubStep == 0) groupWritePwd(); else groupLock(); break;
        }
    }

    // ── View binding ──────────────────────────────────────────────────────

    private void bindViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tabLayout = findViewById(R.id.tabLayout);
        pageLupa = findViewById(R.id.pageLupa);
        pageLupaCsv = findViewById(R.id.pageLupaCsv);
        pageWriteEpc = findViewById(R.id.pageWriteEpc);
        pageGroupWrite = findViewById(R.id.pageGroupWrite);
        pageLock = findViewById(R.id.pageLock);
        svGroupPage = (android.widget.ScrollView) pageGroupWrite;

        // Page 0 — Lupa
        btnScanLupa = findViewById(R.id.btnScanLupa);
        llChipInfoContainer = findViewById(R.id.llChipInfoContainer);
        tvScanEpc = findViewById(R.id.tvScanEpc);
        tvScanTid = findViewById(R.id.tvScanTid);

        // Page 1 — Lupa .CSV
        btnScanCsv = findViewById(R.id.btnScanCsv);
        tvReadEpc = findViewById(R.id.tvReadEpc);
        tvReadTid = findViewById(R.id.tvReadTid);
        etLupaFileName   = findViewById(R.id.etLupaFileName);
        btnLupaSetFile   = findViewById(R.id.btnLupaSetFile);
        tvLupaFilePath   = findViewById(R.id.tvLupaFilePath);
        llLupaFileInput  = findViewById(R.id.llLupaFileInput);
        llLupaGroups            = findViewById(R.id.llLupaGroups);
        llLupaGroupsContainer   = findViewById(R.id.llLupaGroupsContainer);

        // Page 1 template
        etWrtGroups[0] = findViewById(R.id.etWrt1); etWrtGroups[1] = findViewById(R.id.etWrt2);
        etWrtGroups[2] = findViewById(R.id.etWrt3); etWrtGroups[3] = findViewById(R.id.etWrt4);
        etWrtGroups[4] = findViewById(R.id.etWrt5); etWrtGroups[5] = findViewById(R.id.etWrt6);
        etWrtGroupNames[0] = findViewById(R.id.etWrtGrpName1);
        etWrtGroupNames[1] = findViewById(R.id.etWrtGrpName2);
        etWrtGroupNames[2] = findViewById(R.id.etWrtGrpName3);
        etWrtGroupNames[3] = findViewById(R.id.etWrtGrpName4);
        etWrtGroupNames[4] = findViewById(R.id.etWrtGrpName5);
        etWrtGroupNames[5] = findViewById(R.id.etWrtGrpName6);
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
        etGroups[6] = findViewById(R.id.etGroup7); etGroups[7] = findViewById(R.id.etGroup8);
        etGroupNames[0] = findViewById(R.id.etGrpName1);
        etGroupNames[1] = findViewById(R.id.etGrpName2);
        etGroupNames[2] = findViewById(R.id.etGrpName3);
        etGroupNames[3] = findViewById(R.id.etGrpName4);
        etGroupNames[4] = findViewById(R.id.etGrpName5);
        etGroupNames[5] = findViewById(R.id.etGrpName6);
        etGroupNames[6] = findViewById(R.id.etGrpName7);
        etGroupNames[7] = findViewById(R.id.etGrpName8);
        cbGroups[0] = findViewById(R.id.cbGroup1); cbGroups[1] = findViewById(R.id.cbGroup2);
        cbGroups[2] = findViewById(R.id.cbGroup3); cbGroups[3] = findViewById(R.id.cbGroup4);
        cbGroups[4] = findViewById(R.id.cbGroup5); cbGroups[5] = findViewById(R.id.cbGroup6);
        cbGroups[6] = findViewById(R.id.cbGroup7); cbGroups[7] = findViewById(R.id.cbGroup8);
        llGrpRows[0] = findViewById(R.id.llGrpRow1); llGrpRows[1] = findViewById(R.id.llGrpRow2);
        llGrpRows[2] = findViewById(R.id.llGrpRow3); llGrpRows[3] = findViewById(R.id.llGrpRow4);
        llGrpRows[4] = findViewById(R.id.llGrpRow5); llGrpRows[5] = findViewById(R.id.llGrpRow6);
        llGrpRows[6] = findViewById(R.id.llGrpRow7); llGrpRows[7] = findViewById(R.id.llGrpRow8);
        btnGrpPresetStd = findViewById(R.id.btnGrpPresetStd);
        btnGrpPreset1   = findViewById(R.id.btnGrpPreset1);
        btnGrpPreset2   = findViewById(R.id.btnGrpPreset2);
        cbPresetAuto56  = findViewById(R.id.cbPresetAuto56);
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
        tvVerifyG[0] = findViewById(R.id.tvVerifyG1); tvVerifyG[1] = findViewById(R.id.tvVerifyG2);
        tvVerifyG[2] = findViewById(R.id.tvVerifyG3); tvVerifyG[3] = findViewById(R.id.tvVerifyG4);
        tvVerifyG[4] = findViewById(R.id.tvVerifyG5); tvVerifyG[5] = findViewById(R.id.tvVerifyG6);
        tvVerifyG[6] = findViewById(R.id.tvVerifyG7); tvVerifyG[7] = findViewById(R.id.tvVerifyG8);
        tvVerifyLbl[0] = findViewById(R.id.tvVerifyLbl1); tvVerifyLbl[1] = findViewById(R.id.tvVerifyLbl2);
        tvVerifyLbl[2] = findViewById(R.id.tvVerifyLbl3); tvVerifyLbl[3] = findViewById(R.id.tvVerifyLbl4);
        tvVerifyLbl[4] = findViewById(R.id.tvVerifyLbl5); tvVerifyLbl[5] = findViewById(R.id.tvVerifyLbl6);
        tvVerifyLbl[6] = findViewById(R.id.tvVerifyLbl7); tvVerifyLbl[7] = findViewById(R.id.tvVerifyLbl8);
        llVerifyRows[0] = findViewById(R.id.llVerifyRow1); llVerifyRows[1] = findViewById(R.id.llVerifyRow2);
        llVerifyRows[2] = findViewById(R.id.llVerifyRow3); llVerifyRows[3] = findViewById(R.id.llVerifyRow4);
        llVerifyRows[4] = findViewById(R.id.llVerifyRow5); llVerifyRows[5] = findViewById(R.id.llVerifyRow6);
        llVerifyRows[6] = findViewById(R.id.llVerifyRow7); llVerifyRows[7] = findViewById(R.id.llVerifyRow8);
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
        tvAppVersion = findViewById(R.id.tvAppVersion);
        if (tvAppVersion != null) {
            tvAppVersion.setText("Verze " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        }
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
        pageLupa.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageLupaCsv.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pageWriteEpc.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        pageLock.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        pageGroupWrite.setVisibility(index == 4 ? View.VISIBLE : View.GONE);

        if (index == 2) updateWrtEpcPreview();
        if (index == 4) updateGroupEpcPreview();
    }

    private int colorRes(int resId) {
        return ContextCompat.getColor(this, resId);
    }

    private void applyStepCircle(TextView tv, int state) {
        int bg = state == 2 ? R.drawable.step_circle_done
                : state == 1 ? R.drawable.step_circle_active
                : R.drawable.step_circle_pending;
        tv.setBackgroundResource(bg);
        tv.setTextColor(state == 0 ? colorRes(R.color.text_muted) : Color.WHITE);
    }

    private void bindButtons() {
        // Nastavení toggle
        btnToggleSettings.setOnClickListener(v -> {
            boolean visible = llSettings.getVisibility() == View.VISIBLE;
            llSettings.setVisibility(visible ? View.GONE : View.VISIBLE);
            int tint = visible ? colorRes(R.color.text_muted) : colorRes(R.color.primary);
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

        // Lupa + Lupa .CSV
        btnScanLupa.setOnClickListener(v -> { if (mInventorying) stopScan(); else startLupaScan(); });
        btnScanCsv.setOnClickListener(v -> { if (mInventorying) stopScan(); else startCsvScan(); });
        btnLupaSetFile.setOnClickListener(v -> setLupaInputFile());

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
        for (EditText et : etWrtGroupNames) if (et != null) et.addTextChangedListener(wrtNameWatcher);

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

        btnGrpPresetStd.setOnClickListener(v -> setGroupPresetMode(GRP_PRESET_STANDARD));
        btnGrpPreset1.setOnClickListener(v -> setGroupPresetMode(GRP_PRESET_1));
        btnGrpPreset2.setOnClickListener(v -> setGroupPresetMode(GRP_PRESET_2));
        cbPresetAuto56.setOnCheckedChangeListener((b, checked) -> {
            mPresetAuto56 = checked;
            if (checked && mGroupPresetMode == GRP_PRESET_1) {
                groupAutoInc[4] = false;
                groupAutoInc[5] = false;
                if (cbGroups[4] != null) cbGroups[4].setChecked(false);
                if (cbGroups[5] != null) cbGroups[5].setChecked(false);
                if (etGroups[5] != null) {
                    String cast = etGroups[5].getText().toString().trim();
                    if (cast.isEmpty() || cast.equals("0")) etGroups[5].setText("1");
                }
            }
            saveGroupSettings();
        });

        TextWatcher grpWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateGroupEpcPreview(); }
        };
        for (int i = 0; i < GRP_ROW_COUNT; i++) {
            if (etGroups[i] != null) etGroups[i].addTextChangedListener(grpWatcher);
        }

        TextWatcher grpNameWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                onGroupNameChanged();
                updateGroupEpcPreview();
                updateVerifyLabels();
            }
        };
        for (int i = 0; i < GRP_ROW_COUNT; i++) {
            if (etGroupNames[i] != null) etGroupNames[i].addTextChangedListener(grpNameWatcher);
        }

        for (int i = 0; i < GRP_ROW_COUNT; i++) {
            final int idx = i;
            if (cbGroups[i] != null)
                cbGroups[i].setOnCheckedChangeListener((b, checked) -> {
                    groupAutoInc[idx] = checked;
                    saveGroupSettings();
                });
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
                        setStatus("● Připojeno — Chainway C5", colorRes(R.color.success));
                        btnScanLupa.setEnabled(true);
                        btnScanCsv.setEnabled(true);
                        applyOutputPower(sbOutputPower.getProgress());
                    } else {
                        setStatus("● Chyba inicializace", colorRes(R.color.err));
                        Log.e(TAG, "init() false");
                    }
                });
            } catch (Exception e) {
                mHandler.post(() -> setStatus("● SDK chyba: " + e.getMessage(), colorRes(R.color.err)));
            }
        }).start();
    }

    // ── Nastavení (output power) ──────────────────────────────────────────

    private static final String KEY_OUTPUT_POWER = "output_power";

    private void loadSettings() {
        int power = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_OUTPUT_POWER, 1);
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
    private static final String[] DEFAULT_WRT_NAMES = {"-", "-", "-", "-", "-", "ID_RFID"};
    private static final String[] DEFAULT_GRP_NAMES = {"-", "-", "-", "-", "-", "ID_RFID", "ID_RFID", "ID_RFID"};

    private int getActiveGroupRowCount() {
        return isGroupPresetMode() ? GRP_PRESET_ROW_COUNT : 6;
    }

    private boolean isGroupPresetMode() {
        return mGroupPresetMode == GRP_PRESET_1 || mGroupPresetMode == GRP_PRESET_2;
    }

    /** Which template rows show the +1 checkbox in preset mode. */
    private boolean isPresetPlusOneRow(int rowIdx) {
        if (mGroupPresetMode == GRP_PRESET_1) return rowIdx == 6;
        if (mGroupPresetMode == GRP_PRESET_2) return rowIdx == 4 || rowIdx == 6;
        return false;
    }

    private void loadWrtGroupNames() {
        try {
            String json = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_WRT_GROUP_NAMES, null);
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < 6 && i < arr.length(); i++)
                    if (etWrtGroupNames[i] != null) etWrtGroupNames[i].setText(arr.getString(i));
            } else {
                for (int i = 0; i < 6; i++)
                    if (etWrtGroupNames[i] != null) etWrtGroupNames[i].setText(DEFAULT_WRT_NAMES[i]);
            }
        } catch (Exception ignored) {}
    }

    private void saveWrtGroupNames() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < 6; i++)
                arr.put(etWrtGroupNames[i] != null ? etWrtGroupNames[i].getText().toString() : DEFAULT_WRT_NAMES[i]);
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
                for (int i = 0; i < GRP_ROW_COUNT && i < arr.length(); i++)
                    if (etGroupNames[i] != null) etGroupNames[i].setText(arr.getString(i));
            } else {
                for (int i = 0; i < GRP_ROW_COUNT; i++)
                    if (etGroupNames[i] != null) etGroupNames[i].setText(DEFAULT_GRP_NAMES[i]);
            }
        } catch (Exception ignored) {}
    }

    private void saveGrpNames() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < GRP_ROW_COUNT; i++)
                arr.put(etGroupNames[i] != null ? etGroupNames[i].getText().toString() : DEFAULT_GRP_NAMES[i]);
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
        if (isGroupPresetMode()) {
            String castName = (mGroupPresetMode == GRP_PRESET_2) ? "" : "Část";
            return "\uFEFFID_RFID;EPC;TID;Rok;TUDU 1;TUDU 2;Výhybka;" + castName + "\n";
        }
        String[] names = new String[6];
        String[] defaults = {"Skupina_1","Skupina_2","Skupina_3","Skupina_4","Skupina_5","ID_RFID"};
        for (int i = 0; i < 6; i++) {
            names[i] = etGroupNames[i] != null ? etGroupNames[i].getText().toString().trim() : "";
            if (names[i].isEmpty() || names[i].equals("—") || names[i].equals("-"))
                names[i] = defaults[i];
        }
        return "\uFEFFID_RFID;EPC;TID;"
                + names[0] + ";" + names[1] + ";" + names[2] + ";"
                + names[3] + "\n";
    }

    // ── Scan helpers ──────────────────────────────────────────────────────

    private void startLupaScan() {
        if (mReader == null) return;
        mScanContext = SCAN_CTX_LUPA;
        mInventorying = true;
        btnScanLupa.setText("⏹  ZASTAVIT");
        btnScanLupa.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(colorRes(R.color.err)));
        setStatus("● Skenování...", colorRes(R.color.primary));
        startInventory();
    }

    private void startCsvScan() {
        if (mReader == null) return;
        mScanContext = SCAN_CTX_LUPA_CSV;
        mInventorying = true;
        btnScanCsv.setText("⏹  ZASTAVIT");
        btnScanCsv.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(colorRes(R.color.err)));
        setStatus("● Skenování...", colorRes(R.color.primary));
        startInventory();
    }

    private void startGroupScan() {
        if (mReader == null) return;
        mScanContext = SCAN_CTX_GROUP;
        mInventorying = true;
        btnGroupScan.setText("⏹  ZASTAVIT");
        btnGroupScan.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(colorRes(R.color.err)));
        setStatus("● Ověřuji tag...", colorRes(R.color.primary));
        startInventory();
    }

    private void startInventory() {
        final int ctx = mScanContext;
        new Thread(() -> {
            try {
                mReader.setInventoryCallback(new IUHFInventoryCallback() {
                    @Override
                    public void callback(UHFTAGInfo tag) {
                        if (tag == null) return;
                        mHandler.post(() -> {
                            if (mScanContext == SCAN_CTX_GROUP) {
                                onGroupTagFound(tag.getEPC(), tag.getTid());
                            } else if (mScanContext == SCAN_CTX_LUPA) {
                                onLupaTagFound(tag);
                            } else {
                                onCsvTagFound(tag.getEPC(), tag.getTid());
                            }
                        });
                    }
                });
                // USER bank is read after scan via readTagBank() in collectChipInfo().
                // setEPCAndTIDUserMode() breaks inventory on Chainway C5.
                mReader.setEPCAndTIDMode();
                boolean started = mReader.startInventoryTag();
                if (!started) {
                    mHandler.post(() -> onInventoryStartFailed(ctx));
                }
            } catch (Exception e) {
                Log.e(TAG, "startInventory: " + e.getMessage());
                mHandler.post(() -> onInventoryStartFailed(ctx));
            }
        }).start();
    }

    private void onInventoryStartFailed(int ctx) {
        if (!mInventorying) return;
        mInventorying = false;
        if (ctx == SCAN_CTX_GROUP) {
            btnGroupScan.setText("📡  NAČÍST TAG");
            btnGroupScan.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(colorRes(R.color.step_pending)));
        } else if (ctx == SCAN_CTX_LUPA) {
            resetScanButton(btnScanLupa);
        } else {
            resetScanButton(btnScanCsv);
        }
        setStatus("● Skenování selhalo", colorRes(R.color.err));
        toast("Nepodařilo se spustit skenování");
    }

    private void resetScanButton(Button btn) {
        btn.setText("📡  SKENOVAT TAG");
        btn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(colorRes(R.color.primary)));
    }

    private void stopScan() {
        if (mReader == null) return;
        mInventorying = false;
        mReader.stopInventory();
        if (mScanContext == SCAN_CTX_GROUP) {
            btnGroupScan.setText("📡  NAČÍST TAG");
            btnGroupScan.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(colorRes(R.color.step_pending)));
        } else if (mScanContext == SCAN_CTX_LUPA) {
            resetScanButton(btnScanLupa);
        } else if (mScanContext == SCAN_CTX_LUPA_CSV) {
            resetScanButton(btnScanCsv);
        }
        setStatus("● Připojeno — Chainway C5", colorRes(R.color.success));
    }

    // ── Tag found — Lupa (chip info) ──────────────────────────────────────

    private void onLupaTagFound(UHFTAGInfo tag) {
        stopScan();
        String epcStr = tag.getEPC() != null ? tag.getEPC() : "";
        String tidStr = tag.getTid() != null ? tag.getTid() : "";
        mLastTid = tidStr;
        tvScanEpc.setText(epcStr.isEmpty() ? "— žádné EPC —" : epcStr);
        tvScanTid.setText(tidStr.isEmpty() ? "— žádné TID —" : tidStr);

        displayChipInfoPlaceholder("Načítám data z čipu…");

        new Thread(() -> {
            java.util.LinkedHashMap<String, String> info = collectChipInfo(tag, epcStr, tidStr);
            mHandler.post(() -> displayChipInfo(info));
        }).start();
    }

    // ── Tag found — Lupa .CSV ─────────────────────────────────────────────

    private void onCsvTagFound(String epc, String tid) {
        stopScan();
        String epcStr = epc != null ? epc : "";
        String tidStr = tid != null ? tid : "";
        mLastTid = tidStr;
        tvScanEpc.setText(epcStr.isEmpty() ? "— žádné EPC —" : epcStr);
        tvScanTid.setText(tidStr.isEmpty() ? "— žádné TID —" : tidStr);

        tvReadEpc.setText(epcStr.isEmpty() ? "—" : formatHexWithDashes(epcStr));
        tvReadTid.setText(tidStr.isEmpty() ? "—" : formatHexWithDashes(tidStr));

        if (!tidStr.isEmpty() && !mLupaCsvData.isEmpty()) {
            lookupAndDisplayFromCsv(tidStr);
        } else {
            llLupaGroups.setVisibility(View.GONE);
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

        if (isGroupPresetMode()) {
            String[] vals = parsePresetFieldsFromEpc(epc);
            displayPresetVerify(epc, tid);
            appendToGroupCsv(vals[6], epc, tid,
                    vals[0], vals[1], vals[2], vals[3], vals[4], vals[5]);
        } else {
            String g1 = epc.substring(0, 4);
            String g2 = epc.substring(4, 8);
            String g3 = epc.substring(8, 12);
            String g4 = epc.substring(12, 16);
            String g5 = epc.substring(16, 20);
            String g6 = epc.substring(20, 24);

            tvVerifyG[0].setText(g1); tvVerifyG[1].setText(g2);
            tvVerifyG[2].setText(g3); tvVerifyG[3].setText(g4);
            tvVerifyG[4].setText(g5); tvVerifyG[5].setText(g6);
            updateVerifyLabels();

            String fmtEpc = g1+"-"+g2+"-"+g3+"-"+g4+"-"+g5+"-"+g6;
            tvVerifyEpc.setText(fmtEpc);
            tvVerifyTid.setText(tid.isEmpty() ? "—" : tid);

            appendToGroupCsv(g5 + g6, epc, tid, g1, g2, g3, g4, g5, g6);
        }

        llVerifyDisplay.setVisibility(View.VISIBLE);

        String writePwd = readPassword(etGroupAccessPwd);
        if (!writePwd.equals("00000000")) etGroupLockPwd.setText(writePwd);

        setGroupStep(STEP_LOCK);
        toast("✅ Uloženo do CSV — pokračujte LOCK");
    }

    private void displayPresetVerify(String epc, String tid) {
        String[] vals = parsePresetFieldsFromEpc(epc);
        int rows = getActiveGroupRowCount();
        for (int i = 0; i < rows; i++) {
            if (tvVerifyG[i] != null) tvVerifyG[i].setText(vals[i]);
        }
        updateVerifyLabels();
        tvVerifyEpc.setText(formatEpcDashed(epc));
        tvVerifyTid.setText(tid.isEmpty() ? "—" : tid);
    }

    private String[] parsePresetFieldsFromEpc(String epc) {
        String[] vals = new String[GRP_PRESET_ROW_COUNT];
        vals[0] = epc.substring(0, 4);
        vals[1] = epc.substring(4, 8);
        vals[2] = epc.substring(8, 10);
        vals[3] = epc.substring(10, 12);
        vals[4] = epc.substring(12, 15);
        vals[5] = epc.substring(15, 16);
        vals[6] = epc.substring(16, 24);
        return vals;
    }

    // ── Group workflow steps ───────────────────────────────────────────────

    private void setGroupStep(int step) {
        mGroupStep = step;
        if (step == STEP_LOCK) mGroupLockSubStep = 0;

        int primary = colorRes(R.color.primary);
        int success = colorRes(R.color.success);
        int accent  = colorRes(R.color.accent);
        int lock    = colorRes(R.color.lock);
        int muted   = colorRes(R.color.text_muted);
        int pending = colorRes(R.color.step_pending);

        applyStepCircle(tvStep1Num, step > STEP_WRITE ? 2 : (step == STEP_WRITE ? 1 : 0));
        applyStepCircle(tvStep2Num, step > STEP_SCAN ? 2 : (step == STEP_SCAN ? 1 : 0));
        applyStepCircle(tvStep3Num, step == STEP_LOCK ? 1 : 0);

        int c1 = (step == STEP_WRITE) ? primary : (step > STEP_WRITE ? success : muted);
        tvStep1Label.setTextColor(c1);
        btnGroupWrite.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                step == STEP_WRITE ? accent : pending));

        int c2 = (step == STEP_SCAN) ? primary : (step > STEP_SCAN ? success : muted);
        tvStep2Label.setTextColor(c2);
        btnGroupScan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                step == STEP_SCAN ? primary : pending));

        int c3 = (step == STEP_LOCK) ? lock : muted;
        tvStep3Label.setTextColor(c3);
        btnGroupWritePwd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                step == STEP_LOCK ? success : pending));
        btnGroupWritePwd.setTextColor(step == STEP_LOCK
                ? colorRes(R.color.text_on_primary) : colorRes(R.color.text_muted));
        btnGroupLock.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                step == STEP_LOCK ? lock : pending));

        scrollToGroupStep(step);
    }

    private void scrollToGroupStep(int step) {
        if (svGroupPage == null) return;
        if (step == STEP_LOCK) return; // no autoscroll when advancing to step 3
        View target = (step == STEP_SCAN) ? llStep2Header : llStep1Header;
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
        int active   = colorRes(R.color.primary);
        int inactive = colorRes(R.color.step_pending);
        btnBankEpc.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 1 ? active : inactive));
        btnBankEpc.setTextColor(bank == 1
                ? colorRes(R.color.text_on_primary)
                : colorRes(R.color.text));
        btnBankUser.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 3 ? active : inactive));
        btnBankUser.setTextColor(bank == 3
                ? colorRes(R.color.text_on_primary)
                : colorRes(R.color.text));
        btnBankReserved.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 0 ? active : inactive));
        btnBankReserved.setTextColor(bank == 0
                ? colorRes(R.color.text_on_primary)
                : colorRes(R.color.text));
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
        btnToggleWrtTemplate.setText(visible ? "▼  " + label : "▶  " + label);
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
                String g5 = padWrtField(4, etWrtGroups[4].getText().toString());
                String g6 = padWrtField(5, etWrtGroups[5].getText().toString());
                next = incrementMergedIdRfid(g5 + g6);
                etWrtGroups[4].setText(next.substring(0, 4));
                etWrtGroups[5].setText(next.substring(4, 8));
            } else {
                try { next = String.format("%04X", (Integer.parseInt(val, 16) + 1) & 0xFFFF); }
                catch (NumberFormatException e) { next = "0001"; }
                etWrtGroups[i].setText(next);
            }
        }
        updateWrtEpcPreview();
        saveWrtGroupSettings();
    }

    private String padWrtField(int rowIdx, String raw) {
        String v = raw.toUpperCase(Locale.ROOT).trim();
        if (rowIdx == 5) {
            try { return String.format("%04d", Integer.parseInt(v)); }
            catch (NumberFormatException e) { return "0001"; }
        }
        while (v.length() < 4) v = "0" + v;
        if (v.length() > 4) v = v.substring(0, 4);
        return v;
    }

    /** +1 on the last digit of the 8-char merged ID_RFID, with carry (00000009 -> 00000010). */
    private String incrementMergedIdRfid(String raw) {
        String val = raw.toUpperCase(Locale.ROOT).trim();
        while (val.length() < 8) val = "0" + val;
        if (val.length() > 8) val = val.substring(val.length() - 8);
        long n;
        try { n = Long.parseLong(val); } catch (NumberFormatException e) { n = 0; }
        n = (n + 1) % 100_000_000L;
        return String.format("%08d", n);
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
        setStatus("● Zapisuji " + bankName + "...", colorRes(R.color.accent));
        btnWrite.setEnabled(false);

        final int fp = prt, fl = len, fb = mWriteBank;
        final String fd = data, fe = templateEpc;
        new Thread(() -> {
            boolean ok = mReader.writeData(accessPwd, fb, fp, fl, fd);
            mHandler.post(() -> {
                btnWrite.setEnabled(true);
                if (ok) {
                    if (fb == 1) tvScanEpc.setText(fe);
                    setStatus("● " + bankName + " zapsáno OK", colorRes(R.color.success));
                    toast(bankName + " zapsáno!");
                    if (fb == 1) autoIncrementWrtGroups();
                } else {
                    setStatus("● Zápis " + bankName + " selhal", colorRes(R.color.error_runtime));
                    toast("Zápis selhal");
                }
            });
        }).start();
    }

    // ── Page 2 — EPC template ─────────────────────────────────────────────

    private String padGroupField(int rowIdx, String raw) {
        int len = isGroupPresetMode() ? PRESET_FIELD_LENS[rowIdx] : 4;
        String v = raw.toUpperCase(Locale.ROOT).trim();
        if (isGroupPresetMode() && rowIdx == 5) {
            if (v.isEmpty()) return "0";
            return v.substring(0, 1);
        }
        if (!isGroupPresetMode() && rowIdx == 5) {
            try { return String.format("%04d", Integer.parseInt(v)); }
            catch (NumberFormatException e) { return "0001"; }
        }
        while (v.length() < len) v = "0" + v;
        if (v.length() > len) v = v.substring(v.length() - len);
        return v;
    }

    private String buildGroupEpc() {
        if (isGroupPresetMode()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < GRP_PRESET_ROW_COUNT; i++)
                sb.append(padGroupField(i, etGroups[i].getText().toString()));
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            String v = etGroups[i].getText().toString().toUpperCase(Locale.ROOT).trim();
            if (i == 5) {
                try { v = String.format("%04d", Integer.parseInt(v)); }
                catch (NumberFormatException e) { v = "0001"; }
            } else {
                while (v.length() < 4) v = "0" + v;
                if (v.length() > 4) v = v.substring(0, 4);
            }
            sb.append(v);
        }
        return sb.toString();
    }

    private String formatTudu2Csv(String asciiHex, String numPart) {
        try {
            char c = (char) Integer.parseInt(asciiHex, 16);
            int n = Integer.parseInt(numPart, 16);
            return "" + c + n;
        } catch (NumberFormatException e) {
            return asciiHex + numPart;
        }
    }

    private String formatEpcDashed(String epc) {
        if (epc.length() < 24) return epc;
        return epc.substring(0,4)+"-"+epc.substring(4,8)+"-"+
                epc.substring(8,12)+"-"+epc.substring(12,16)+"-"+
                epc.substring(16,20)+"-"+epc.substring(20,24);
    }

    private void updateGroupEpcPreview() {
        if (tvEpcPreview == null) return;
        String epc = buildGroupEpc();
        if (epc.length() != 24) return;

        if (mGroupWriteBank == 1) {
            if (tvGrpPreviewLabel != null) {
                if (isGroupPresetMode()) {
                    tvGrpPreviewLabel.setText("náhled EPC (preset)");
                } else {
                    tvGrpPreviewLabel.setText("náhled EPC");
                }
            }
            tvEpcPreview.setText(formatEpcDashed(epc));
            tvEpcPreview.setTextSize(16);
        } else {
            String label = (mGroupWriteBank == 3) ? "náhled USER" : "náhled RESERVED";
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
            tvEpcPreview.setTextSize(16);
        }
    }

    private String getGroupRowLabel(int idx) {
        if (etGroupNames[idx] == null) return "Řádek " + (idx + 1);
        String name = etGroupNames[idx].getText().toString().trim();
        if (name.isEmpty() || name.equals("-") || name.equals("—")) return "Řádek " + (idx + 1);
        return name;
    }

    private void updateVerifyLabels() {
        int rows = getActiveGroupRowCount();
        for (int i = 0; i < GRP_ROW_COUNT; i++) {
            if (llVerifyRows[i] != null)
                llVerifyRows[i].setVisibility(i < rows ? View.VISIBLE : View.GONE);
            if (tvVerifyLbl[i] != null && i < rows) {
                String label = getGroupRowLabel(i);
                boolean isIdRow = isGroupPresetMode() ? (i == 6) : (i == 5);
                tvVerifyLbl[i].setText(label.isEmpty() ? ("(" + (i + 1) + "):") : (label + " (" + (i + 1) + "):"));
                int color = isIdRow ? colorRes(R.color.vyhybka_accent) : colorRes(R.color.text_muted);
                tvVerifyLbl[i].setTextColor(color);
                if (tvVerifyG[i] != null) {
                    tvVerifyG[i].setTextColor(color);
                    tvVerifyG[i].setTypeface(null, isIdRow ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                }
            }
        }
    }

    private void setGroupPresetMode(int mode) {
        if (mGroupPresetMode == mode) return;
        mGroupPresetMode = mode;
        applyGroupPresetUi(true);
        if (mGroupOutputFile != null && mGroupRecordCount == 0) {
            try {
                File file = new File(mGroupOutputFile);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(buildGroupCsvHeader().getBytes("UTF-8"));
                }
            } catch (Exception e) {
                Log.e(TAG, "rewrite CSV header on preset change: " + e.getMessage());
            }
        }
        saveGroupSettings();
    }

    private void applyGroupPresetUi() {
        applyGroupPresetUi(false);
    }

    private void applyGroupPresetUi(boolean resetValues) {
        int active = colorRes(R.color.primary);
        int inactive = colorRes(R.color.step_pending);
        int activeFg = colorRes(R.color.text_on_primary);
        int inactiveFg = colorRes(R.color.text);

        btnGrpPresetStd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                mGroupPresetMode == GRP_PRESET_STANDARD ? active : inactive));
        btnGrpPresetStd.setTextColor(mGroupPresetMode == GRP_PRESET_STANDARD ? activeFg : inactiveFg);
        btnGrpPreset1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                mGroupPresetMode == GRP_PRESET_1 ? active : inactive));
        btnGrpPreset1.setTextColor(mGroupPresetMode == GRP_PRESET_1 ? activeFg : inactiveFg);
        btnGrpPreset2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                mGroupPresetMode == GRP_PRESET_2 ? active : inactive));
        btnGrpPreset2.setTextColor(mGroupPresetMode == GRP_PRESET_2 ? activeFg : inactiveFg);

        cbPresetAuto56.setVisibility(mGroupPresetMode == GRP_PRESET_1 ? View.VISIBLE : View.GONE);

        String[] presetNames = (mGroupPresetMode == GRP_PRESET_2) ? PRESET2_NAMES : PRESET1_NAMES;
        boolean preset = isGroupPresetMode();

        for (int i = 0; i < GRP_ROW_COUNT; i++) {
            if (llGrpRows[i] != null) {
                boolean hide = (!preset && i >= 6) || (preset && i >= GRP_PRESET_ROW_COUNT);
                llGrpRows[i].setVisibility(hide ? View.GONE : View.VISIBLE);
            }
            if (etGroups[i] != null) {
                int maxLen;
                if (preset) {
                    maxLen = (i < GRP_PRESET_ROW_COUNT) ? PRESET_FIELD_LENS[i] : 4;
                } else {
                    maxLen = 4;
                }
                if (!preset && i == 5) {
                    etGroups[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                } else {
                    etGroups[i].setInputType(android.text.InputType.TYPE_CLASS_TEXT
                            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
                }
                etGroups[i].setFilters(new android.text.InputFilter[]{
                        new android.text.InputFilter.LengthFilter(maxLen)});
                if (preset && i == 6) {
                    android.view.ViewGroup.LayoutParams lp = etGroups[i].getLayoutParams();
                    lp.width = (int) (120 * getResources().getDisplayMetrics().density);
                    etGroups[i].setLayoutParams(lp);
                } else if (!preset || i < GRP_PRESET_ROW_COUNT) {
                    android.view.ViewGroup.LayoutParams lp = etGroups[i].getLayoutParams();
                    lp.width = (int) (76 * getResources().getDisplayMetrics().density);
                    etGroups[i].setLayoutParams(lp);
                }
            }
            if (preset && etGroupNames[i] != null && i < GRP_PRESET_ROW_COUNT) {
                etGroupNames[i].setText(presetNames[i]);
                etGroupNames[i].setEnabled(mGroupPresetMode != GRP_PRESET_2 || i != 5);
            } else if (!preset && etGroupNames[i] != null) {
                etGroupNames[i].setEnabled(true);
            }
            if (preset && resetValues) {
                if (etGroups[i] != null) {
                    if (i == 5 && mGroupPresetMode == GRP_PRESET_1) etGroups[i].setText("1");
                    else etGroups[i].setText("");
                }
                if (i < GRP_PRESET_ROW_COUNT) {
                    boolean plusOne = isPresetPlusOneRow(i);
                    groupAutoInc[i] = plusOne;
                    if (cbGroups[i] != null) {
                        cbGroups[i].setChecked(plusOne);
                        cbGroups[i].setEnabled(plusOne);
                        cbGroups[i].setVisibility(plusOne ? View.VISIBLE : View.INVISIBLE);
                    }
                } else if (cbGroups[i] != null) {
                    cbGroups[i].setVisibility(View.GONE);
                }
            } else if (preset) {
                if (cbGroups[i] != null) {
                    if (i < GRP_PRESET_ROW_COUNT) {
                        boolean plusOne = isPresetPlusOneRow(i);
                        if (!plusOne) groupAutoInc[i] = false;
                        cbGroups[i].setEnabled(plusOne);
                        cbGroups[i].setVisibility(plusOne ? View.VISIBLE : View.INVISIBLE);
                        if (!plusOne) cbGroups[i].setChecked(false);
                    } else {
                        cbGroups[i].setVisibility(View.GONE);
                    }
                }
            } else if (cbGroups[i] != null) {
                cbGroups[i].setEnabled(true);
                cbGroups[i].setVisibility(View.VISIBLE);
            }
        }

        if (preset && resetValues) {
            mPresetAuto56 = false;
            cbPresetAuto56.setChecked(false);
        }

        if (preset && mGroupPresetMode == GRP_PRESET_1 && mPresetAuto56 && etGroups[5] != null) {
            String cast = etGroups[5].getText().toString().trim();
            if (cast.isEmpty() || cast.equals("0")) etGroups[5].setText("1");
        }

        updateVerifyLabels();
        updateGroupEpcPreview();
        onGroupNameChanged();
    }

    private void selectGroupWriteBank(int bank) {
        mGroupWriteBank = bank;
        int active   = colorRes(R.color.primary);
        int inactive = colorRes(R.color.step_pending);
        btnGrpBankEpc.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 1 ? active : inactive));
        btnGrpBankEpc.setTextColor(bank == 1
                ? colorRes(R.color.text_on_primary)
                : colorRes(R.color.text));
        btnGrpBankUser.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 3 ? active : inactive));
        btnGrpBankUser.setTextColor(bank == 3
                ? colorRes(R.color.text_on_primary)
                : colorRes(R.color.text));
        btnGrpBankReserved.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bank == 0 ? active : inactive));
        btnGrpBankReserved.setTextColor(bank == 0
                ? colorRes(R.color.text_on_primary)
                : colorRes(R.color.text));

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
        btnToggleTemplate.setText(visible ? "▼  " + label : "▶  " + label);
    }

    private void autoIncrementGroups() {
        if (isGroupPresetMode() && mGroupPresetMode == GRP_PRESET_1 && mPresetAuto56) {
            autoIncrementPreset56();
            if (groupAutoInc[6]) incrementGroupRow(6);
            updateGroupEpcPreview();
            saveGroupSettings();
            return;
        }

        int count = getActiveGroupRowCount();
        for (int i = 0; i < count; i++) {
            if (isGroupPresetMode() && !isPresetPlusOneRow(i)) continue;
            if (!groupAutoInc[i]) continue;
            incrementGroupRow(i);
        }
        updateGroupEpcPreview();
        saveGroupSettings();
    }

    private void autoIncrementPreset56() {
        String val6 = etGroups[5].getText().toString().trim();
        int cur;
        try { cur = Integer.parseInt(val6); } catch (NumberFormatException e) { cur = 0; }
        if (cur < 1 || cur > 3) cur = 1;
        if (cur >= 3) {
            etGroups[5].setText("1");
            incrementVhybkaRow();
        } else {
            etGroups[5].setText(String.valueOf(cur + 1));
        }
    }

    private void incrementVhybkaRow() {
        String val5 = etGroups[4].getText().toString().trim();
        try {
            int v5 = Integer.parseInt(val5);
            etGroups[4].setText(String.format("%03d", v5 + 1));
        } catch (NumberFormatException e) {
            etGroups[4].setText("001");
        }
    }

    private void incrementGroupRow(int i) {
        String val = etGroups[i].getText().toString().toUpperCase(Locale.ROOT).trim();
        String next;
        if (!isGroupPresetMode() && i == 5) {
            String g5 = padGroupField(4, etGroups[4].getText().toString());
            String g6 = padGroupField(5, etGroups[5].getText().toString());
            next = incrementMergedIdRfid(g5 + g6);
            etGroups[4].setText(next.substring(0, 4));
            etGroups[5].setText(next.substring(4, 8));
            return;
        } else if (isGroupPresetMode() && i == 6) {
            next = incrementMergedIdRfid(val);
        } else if (isGroupPresetMode() && i == 5) {
            int cur;
            try { cur = Integer.parseInt(val); } catch (NumberFormatException e) { cur = 0; }
            if (cur < 1 || cur > 3) cur = 1;
            next = (cur >= 3) ? "1" : String.valueOf(cur + 1);
        } else if (isGroupPresetMode() && i == 4) {
            try {
                int v5 = Integer.parseInt(val);
                next = String.format("%03d", v5 + 1);
            } catch (NumberFormatException e) {
                next = "001";
            }
        } else {
            try { next = String.format("%0" + (isGroupPresetMode() ? PRESET_FIELD_LENS[i] : 4) + "X",
                    (Integer.parseInt(val, 16) + 1) & 0xFFFF); }
            catch (NumberFormatException e) { next = "0001"; }
        }
        etGroups[i].setText(next);
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
        setStatus("● Skupinový zápis " + bankName + "...", colorRes(R.color.accent));
        btnGroupWrite.setEnabled(false);

        final int fp = prt, fl = len, fb = mGroupWriteBank;
        final String fd = data, fe = templateEpc;
        new Thread(() -> {
            boolean ok = mReader.writeData(accessPwd, fb, fp, fl, fd);
            mHandler.post(() -> {
                btnGroupWrite.setEnabled(true);
                if (ok) {
                    setStatus("● " + bankName + " zapsáno — naskenujte tag", colorRes(R.color.success));
                    toast("✅ " + bankName + " zapsáno — stiskněte NAČÍST TAG");
                    if (fb == 1) setGroupStep(STEP_SCAN);
                    else {
                        // For USER/RESERVED bank, auto-increment and reset to write step
                        autoIncrementGroups();
                        setGroupStep(STEP_WRITE);
                    }
                } else {
                    setStatus("● Skupinový zápis " + bankName + " selhal", colorRes(R.color.error_runtime));
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
        setStatus("● Zapisuji heslo...", colorRes(R.color.accent));
        btnGroupWritePwd.setEnabled(false);
        new Thread(() -> {
            boolean ok = mReader.writeData(cur, 0, 2, 2, np);
            mHandler.post(() -> {
                btnGroupWritePwd.setEnabled(true);
                if (ok) {
                    setStatus("● Heslo zapsáno", colorRes(R.color.success));
                    etGroupLockPwd.setText(np);
                    mGroupLockSubStep = 1; // advance to LOCK sub-step
                    toast("Heslo zapsáno!");
                } else {
                    setStatus("● Zápis hesla selhal", colorRes(R.color.error_runtime));
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

        setStatus("● Zaheslovávám tag...", colorRes(R.color.accent));
        btnGroupLock.setEnabled(false);

        final String fc = lockCode;
        new Thread(() -> {
            boolean ok = mReader.lockMem(accessPwd, fc);
            mHandler.post(() -> {
                btnGroupLock.setEnabled(true);
                if (ok) {
                    setStatus("● Tag zaheslován ✓", colorRes(R.color.success));
                    mGroupLockSubStep = 0; // reset sub-step for next tag
                    toast("✅ Tag zaheslován — připraven další");
                    // Reset for next tag
                    llVerifyDisplay.setVisibility(View.GONE);
                    autoIncrementGroups();
                    setGroupStep(STEP_WRITE);
                } else {
                    setStatus("● Lock selhal", colorRes(R.color.error_runtime));
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
                                   String g1, String g2, String g3, String g4,
                                   String g5, String g6) {
        if (mGroupOutputFile == null) { toast("⚠️ Nastavte výstupní soubor"); return; }

        String strippedId = idRfid.replaceFirst("^0+", "");
        if (strippedId.isEmpty()) strippedId = "0";
        String fmtEpc = formatEpcDashed(epc.length() >= 24 ? epc : epc);
        String fmtTid = tid.isEmpty() ? "" : formatHexWithDashes(tid);

        String line;
        if (isGroupPresetMode()) {
            String tudu2 = formatTudu2Csv(g3, g4);
            String cast = g6;
            line = escCsv(strippedId) + ";" + fmtEpc + ";" + fmtTid + ";"
                    + escCsv(g1) + ";" + escCsv(g2) + ";" + escCsv(tudu2) + ";"
                    + escCsv(g5) + ";" + escCsv(cast) + "\n";
        } else {
            line = escCsv(strippedId) + ";" + fmtEpc + ";" + fmtTid + ";"
                    + g1 + ";" + g2 + ";" + g3 + ";" + g4 + "\n";
        }

        boolean overwritten = upsertGroupCsvLine(fmtTid, line);
        if (!overwritten) mGroupRecordCount++;

        tvGroupRecordCount.setText(String.valueOf(mGroupRecordCount));
        tvGroupLastEpc.setText(fmtEpc);
        saveGroupSettings();
        if (overwritten) toast("✅ CSV řádek přepsán (stejné TID)");
    }

    private boolean upsertGroupCsvLine(String fmtTid, String newLine) {
        if (fmtTid.isEmpty()) return false;
        try {
            java.util.List<String> lines = new java.util.ArrayList<>();
            String header = null;
            boolean found = false;
            String tidKey = fmtTid.replace("-", "").toUpperCase(Locale.ROOT);

            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(mGroupOutputFile), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                if (header == null) {
                    header = line;
                    lines.add(line);
                    continue;
                }
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(";", -1);
                if (cols.length >= 3) {
                    String rowTid = cols[2].trim().replace("-", "").toUpperCase(Locale.ROOT);
                    if (!rowTid.isEmpty() && rowTid.equals(tidKey)) {
                        lines.add(newLine.trim());
                        found = true;
                        continue;
                    }
                }
                lines.add(line);
            }
            br.close();

            if (!found) lines.add(newLine.trim());

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(lines.get(i));
            }
            if (!sb.toString().endsWith("\n")) sb.append("\n");

            try (FileOutputStream fos = new FileOutputStream(mGroupOutputFile)) {
                fos.write(sb.toString().getBytes("UTF-8"));
            }
            return found;
        } catch (Exception e) {
            toast("Chyba zápisu: " + e.getMessage());
            return false;
        }
    }

    // ── Group settings persistence ────────────────────────────────────────

    private void loadGroupSettings() {
        mGroupOutputFile  = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("group_file", null);
        mGroupRecordCount = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("group_count", 0);
        mGroupPresetMode  = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("group_preset", GRP_PRESET_STANDARD);
        mPresetAuto56     = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("group_preset_auto56", false);
        try {
            String vJson = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("group_values", null);
            if (vJson != null) {
                JSONArray arr = new JSONArray(vJson);
                for (int i = 0; i < GRP_ROW_COUNT && i < arr.length(); i++)
                    if (etGroups[i] != null) etGroups[i].setText(arr.getString(i));
                if (isGroupPresetMode() && etGroups[6] != null && etGroups[7] != null) {
                    String g7 = etGroups[6].getText().toString().trim();
                    String g8 = etGroups[7].getText().toString().trim();
                    if (g7.length() <= 4 && !g8.isEmpty()) {
                        String combined = (g7 + g8).toUpperCase(Locale.ROOT);
                        while (combined.length() < 8) combined = "0" + combined;
                        if (combined.length() > 8) combined = combined.substring(combined.length() - 8);
                        etGroups[6].setText(combined);
                    }
                    etGroups[7].setText("");
                }
            }
            String iJson = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("group_autoinc", null);
            if (iJson != null) {
                JSONArray arr = new JSONArray(iJson);
                for (int i = 0; i < GRP_ROW_COUNT && i < arr.length(); i++) {
                    groupAutoInc[i] = arr.getBoolean(i);
                    if (cbGroups[i] != null) cbGroups[i].setChecked(groupAutoInc[i]);
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
        if (cbPresetAuto56 != null) cbPresetAuto56.setChecked(mPresetAuto56);
        applyGroupPresetUi();
        selectGroupWriteBank(mGroupWriteBank);
        updateGrpTemplateBtnText();
        updateVerifyLabels();
        setGroupStep(STEP_WRITE);
    }

    private void saveGroupSettings() {
        try {
            JSONArray vArr = new JSONArray(); JSONArray iArr = new JSONArray();
            for (int i = 0; i < GRP_ROW_COUNT; i++) {
                vArr.put(etGroups[i] != null ? etGroups[i].getText().toString() : "");
                iArr.put(groupAutoInc[i]);
            }
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("group_file",   mGroupOutputFile)
                    .putInt("group_count",      mGroupRecordCount)
                    .putInt("group_preset",     mGroupPresetMode)
                    .putBoolean("group_preset_auto56", mPresetAuto56)
                    .putString("group_values",  vArr.toString())
                    .putString("group_autoinc", iArr.toString()).apply();
        } catch (Exception e) { Log.e(TAG, "saveGroup: " + e.getMessage()); }
    }

    // ── Chip info (Lupa tab) ──────────────────────────────────────────────

    private void displayChipInfoPlaceholder(String message) {
        if (llChipInfoContainer == null) return;
        llChipInfoContainer.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(colorRes(R.color.text_muted));
        tv.setTextSize(14);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(0, (int) (48 * getResources().getDisplayMetrics().density), 0, 0);
        llChipInfoContainer.addView(tv);
    }

    private void displayChipInfo(java.util.LinkedHashMap<String, String> info) {
        if (llChipInfoContainer == null) return;
        llChipInfoContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (12 * density);
        int margin = (int) (8 * density);

        for (java.util.Map.Entry<String, String> entry : info.entrySet()) {
            com.google.android.material.card.MaterialCardView card =
                    new com.google.android.material.card.MaterialCardView(this);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = margin;
            card.setLayoutParams(cardLp);
            card.setCardBackgroundColor(colorRes(R.color.summary_bg));
            card.setRadius(10 * density);
            card.setStrokeColor(colorRes(R.color.divider));
            card.setStrokeWidth((int) density);
            card.setCardElevation(0);

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(pad, pad, pad, pad);

            TextView lbl = new TextView(this);
            lbl.setText(entry.getKey());
            lbl.setTextColor(colorRes(R.color.text_muted));
            lbl.setTextSize(9);
            lbl.setTypeface(null, android.graphics.Typeface.BOLD);
            lbl.setLetterSpacing(0.08f);
            inner.addView(lbl);

            TextView val = new TextView(this);
            val.setText(entry.getValue() != null ? entry.getValue() : "—");
            val.setTextColor(colorRes(R.color.primary));
            val.setTextSize(isLongValue(entry.getKey()) ? 13 : 16);
            val.setTypeface(android.graphics.Typeface.MONOSPACE);
            val.setPadding(0, (int) (4 * density), 0, 0);
            if ("TID".equals(entry.getKey()) || entry.getKey().startsWith("Chip")
                    || "Typ čipu".equals(entry.getKey()) || "Výrobce".equals(entry.getKey())) {
                val.setTextColor(colorRes(R.color.success));
            }
            inner.addView(val);
            card.addView(inner);
            llChipInfoContainer.addView(card);
        }
    }

    private boolean isLongValue(String key) {
        return key.contains("USER") || key.contains("RESERVED") || key.contains("EPC bank")
                || key.contains("password") || key.contains("hex");
    }

    private java.util.LinkedHashMap<String, String> collectChipInfo(UHFTAGInfo tag, String epc, String tid) {
        java.util.LinkedHashMap<String, String> info = new java.util.LinkedHashMap<>();
        info.put("EPC", epc.isEmpty() ? "—" : formatHexWithDashes(epc));
        info.put("TID", tid.isEmpty() ? "—" : formatHexWithDashes(tid));

        String manufacturer = decodeTidManufacturer(tid);
        String chipModel = decodeTidChipModel(tid);
        String memory = decodeTidMemorySize(tid, chipModel);

        if (!manufacturer.isEmpty()) info.put("Výrobce", manufacturer);

        com.rscja.deviceapi.entity.UHFTAGInfo.ChipInfo chipInfo = tag.getChipInfo();
        if (chipInfo != null) {
            String factory = chipInfo.getFactory();
            String chipType = chipInfo.getChipType();
            if (factory != null && !factory.isEmpty()
                    && (manufacturer.isEmpty() || !manufacturerMatches(manufacturer, factory))) {
                info.put("Výrobce (SDK)", factory);
            }
            if (chipModel.isEmpty() && chipType != null && !chipType.isEmpty()) {
                info.put("Typ čipu", chipType);
            }
        }

        if (!chipModel.isEmpty()) info.put("Typ čipu", chipModel);
        if (!memory.isEmpty()) info.put("Paměť EPC", memory);

        if (!tid.isEmpty() && tid.toUpperCase(Locale.ROOT).startsWith("E2")) {
            info.put("Protokol", "EPC Gen2 (ISO 18000-6C)");
        }

        String pc = tag.getPc();
        if (pc != null && !pc.isEmpty()) info.put("PC (Protocol Control)", formatHexWithDashes(pc));

        String rssi = tag.getRssi();
        if (rssi != null && !rssi.isEmpty()) info.put("RSSI", rssi + " dBm");

        String ant = tag.getAnt();
        if (ant != null && !ant.isEmpty()) info.put("Anténa", ant);

        if (tag.getCount() > 0) info.put("Počet čtení", String.valueOf(tag.getCount()));
        if (tag.getPhase() != 0) info.put("Fáze", String.valueOf(tag.getPhase()));
        if (tag.getRemain() != 0) info.put("Zbývá slov", String.valueOf(tag.getRemain()));
        if (tag.getFrequencyPoint() > 0) info.put("Frekvence", tag.getFrequencyPoint() + " MHz");

        if (!epc.isEmpty()) {
            int epcBits = epc.length() * 4;
            int epcWords = (epc.length() + 3) / 4;
            info.put("EPC délka", epcBits + " bit (" + epcWords + " slov)");
        }

        String user = tag.getUser();
        if (user == null || user.isEmpty()) user = readTagBank(epc, 3, 0, 32);
        info.put("USER", (user != null && !user.isEmpty()) ? formatHexWithDashes(user) : "—");

        String reserved = tag.getReserved();
        if (reserved == null || reserved.isEmpty()) reserved = readTagBank(epc, 0, 0, 4);
        if (reserved != null && !reserved.isEmpty()) {
            info.put("RESERVED (raw)", formatHexWithDashes(reserved));
            if (reserved.length() >= 8) {
                info.put("Kill password", formatHexWithDashes(reserved.substring(0, 8)));
            }
            if (reserved.length() >= 16) {
                info.put("Access password", formatHexWithDashes(reserved.substring(8, 16)));
            }
        }

        if (!epc.isEmpty() && mReader != null) {
            int epcWords = Math.max(6, (epc.length() + 3) / 4);
            String epcBank = readTagBank(epc, 1, 0, epcWords + 2);
            if (epcBank != null && !epcBank.isEmpty()) {
                info.put("EPC bank (raw)", formatHexWithDashes(epcBank));
            }
        }

        return info;
    }

    private String readTagBank(String epc, int bank, int ptr, int wordCount) {
        if (mReader == null || epc == null || epc.isEmpty() || wordCount <= 0) return null;
        try {
            return mReader.readData("00000000", bank, ptr, wordCount, epc, 0, 0, 0);
        } catch (Exception e) {
            try {
                return mReader.readData("00000000", bank, ptr, wordCount);
            } catch (Exception e2) {
                Log.w(TAG, "readData bank=" + bank + ": " + e2.getMessage());
                return null;
            }
        }
    }

    private boolean manufacturerMatches(String tidBased, String sdk) {
        if (sdk == null || sdk.isEmpty()) return true;
        String a = tidBased.toLowerCase(Locale.ROOT);
        String b = sdk.toLowerCase(Locale.ROOT);
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    /** ISO/IEC 15963 Tag Mask Manufacturer ID decoded from TID bytes 1–2. */
    private String decodeTidManufacturer(String tid) {
        if (tid == null || tid.length() < 6) return "";
        String t = tid.toUpperCase(Locale.ROOT);
        if (!t.startsWith("E2")) return "";
        String mdid = t.substring(2, 6);
        switch (mdid) {
            case "0034": return "Impinj";
            case "8068": return "NXP Semiconductors";
            case "0016": return "NXP Semiconductors";
            case "8011": return "Alien Technology";
            case "8014": return "Alien Technology";
            case "0060": return "Kiloway";
            case "0041": return "Avery Dennison";
            case "8020": return "ams AG";
            case "8069": return "EM Microelectronic";
            case "0050": return "Quanray Electronics";
            case "0033": return "Atmel";
            case "0042": return "Confidex";
            case "8010": return "Intelleflex";
            default:
                return "Neznámý (MDID " + mdid + ")";
        }
    }

    private String decodeTidChipModel(String tid) {
        if (tid == null || tid.length() < 4) return "";
        String t = tid.toUpperCase(Locale.ROOT);
        if (!t.startsWith("E2")) {
            return "Neznámý (TID: " + t.substring(0, Math.min(8, t.length())) + "…)";
        }

        if (t.length() >= 10) {
            String modelKey = t.substring(4, 10);
            switch (modelKey) {
                case "003407": return "Impinj Monza 4D";
                case "003408": return "Impinj Monza 4E";
                case "003409": return "Impinj Monza 4QT";
                case "003412": return "Impinj Monza 5";
                case "003413": return "Impinj Monza 5";
                case "003414": return "Impinj Monza 5";
                case "003415": return "Impinj Monza R6";
                case "003416": return "Impinj Monza R6-P";
                case "003417": return "Impinj M730";
                case "003418": return "Impinj M750";
                case "689400": return "NXP UCODE 7";
                case "689401": return "NXP UCODE 7m";
                case "689402": return "NXP UCODE 7xm";
                case "689403": return "NXP UCODE 7xm-2K";
                case "689404": return "NXP UCODE 8";
                case "689405": return "NXP UCODE 8m";
                case "689406": return "NXP UCODE 8";
                case "689407": return "NXP UCODE 9";
                case "689408": return "NXP UCODE 8 / 9";
                case "801101": return "Alien Higgs-3";
                case "801102": return "Alien Higgs-4";
                case "801103": return "Alien Higgs-EC";
                case "006000": return "Kiloway KX";
                default:
                    return "EPC Gen2 (model " + modelKey + ")";
            }
        }
        return "EPC Gen2";
    }

    private String decodeTidMemorySize(String tid, String chipModel) {
        if (chipModel == null || chipModel.isEmpty()) return "";
        if (chipModel.contains("UCODE 7xm-2K")) return "240 bit EPC";
        if (chipModel.contains("UCODE 8m")) return "128 bit EPC";
        if (chipModel.contains("UCODE 9")) return "128 bit EPC";
        if (chipModel.contains("UCODE 8")) return "128 bit EPC";
        if (chipModel.contains("UCODE 7")) return "128 bit EPC";
        if (chipModel.contains("Monza 4")) return "128 bit EPC";
        if (chipModel.contains("Monza 5")) return "128 bit EPC";
        if (chipModel.contains("Monza R6")) return "128 bit EPC";
        if (chipModel.contains("M730") || chipModel.contains("M750")) return "128 bit EPC";
        if (chipModel.contains("Higgs-3")) return "96 bit EPC";
        if (chipModel.contains("Higgs-4") || chipModel.contains("Higgs-EC")) return "128 bit EPC";
        return "";
    }

    // ── Page 3: Zápis hesla / Zamčení ────────────────────────────────────

    private void writePwd() {
        if (mReader == null) return;
        String cur = readPassword(etPwdCurrentPwd);
        String np  = etPwdNewPwd.getText().toString().trim().toUpperCase();
        if (np.isEmpty() || np.length() != 8 || !np.matches("[0-9A-F]+")) {
            toast("Platné heslo: přesně 8 hex znaků"); return;
        }
        setStatus("● Zapisuji heslo...", colorRes(R.color.accent)); btnWritePwd.setEnabled(false);
        new Thread(() -> {
            boolean ok = mReader.writeData(cur, 0, 2, 2, np);
            mHandler.post(() -> {
                btnWritePwd.setEnabled(true);
                if (ok) {
                    setStatus("● Heslo zapsáno", colorRes(R.color.success));
                    etLockAccessPwd.setText(np);
                    mLockSubStep = 1; // advance to ZAMČENÍ sub-step
                    toast("Heslo zapsáno!");
                } else {
                    setStatus("● Zápis hesla selhal", colorRes(R.color.error_runtime));
                    toast("Zápis hesla selhal");
                }
            });
        }).start();
    }

    private void lockTag() {
        if (mReader == null) return;
        String ap = readPassword(etLockAccessPwd);
        String lc = etLockCode.getText().toString().trim().toUpperCase();
        if (lc.isEmpty()) lc = "008020";
        setStatus("● Zamykám...", colorRes(R.color.lock)); btnLock.setEnabled(false);
        final String fc = lc;
        new Thread(() -> {
            boolean ok = mReader.lockMem(ap, fc);
            mHandler.post(() -> {
                btnLock.setEnabled(true);
                if (ok) {
                    setStatus("● Tag zamčen", colorRes(R.color.success));
                    mLockSubStep = 0; // reset to ZÁPIS HESLA sub-step
                    toast("Tag zamčen!");
                } else {
                    setStatus("● Lock selhal", colorRes(R.color.error_runtime));
                    toast("Lock selhal");
                }
            });
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String readPassword(EditText field) {
        String v = field.getText().toString().trim().toUpperCase();
        if (v.length() == 8 && v.matches("[0-9A-F]+")) return v;
        return "00000000";
    }

    private void setStatus(String text, int color) {
        tvStatus.setText(text); tvStatus.setTextColor(color);
    }

    private void toast(String msg) {
        View root = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(root, msg, Snackbar.LENGTH_SHORT);
        View snackView = snackbar.getView();
        // Color-code by message type
        int bg, fg;
        if (msg.contains("✅") || msg.contains("zapsáno") || msg.contains("uložen")
                || msg.contains("zamčen") || msg.contains("Zkopírováno") || msg.contains("CSV:")) {
            bg = colorRes(R.color.snackbar_success_bg);
            fg = colorRes(R.color.success);
        } else if (msg.contains("⚠") || msg.contains("selhal") || msg.contains("Chyba")
                || msg.contains("Duplikát") || msg.contains("Neplatné") || msg.contains("Platné heslo")) {
            bg = colorRes(R.color.snackbar_error_bg);
            fg = colorRes(R.color.err);
        } else {
            bg = colorRes(R.color.snackbar_info_bg);
            fg = colorRes(R.color.primary);
        }
        snackView.setBackgroundColor(bg);
        TextView tv = snackView.findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextColor(fg);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setMaxLines(3);
        }
        snackbar.show();
    }

    private String escCsv(String s) {
        if (s == null) return "";
        if (s.contains(";") || s.contains("\"") || s.contains("\n"))
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

    // ── Lupa CSV vstup ────────────────────────────────────────────────────

    private void setLupaInputFile() {
        String name = etLupaFileName.getText().toString().trim();
        if (name.isEmpty()) { toast("Zadejte název souboru"); return; }
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            File file = new File(dir, name + ".csv");
            if (!file.exists()) {
                toast("⚠️ Soubor nenalezen: " + file.getName());
                return;
            }
            mLupaInputFile = file.getAbsolutePath();
            loadLupaCsvData();
            saveLupaSettings();
        } catch (Exception e) { toast("Chyba: " + e.getMessage()); }
    }

    private void loadLupaCsvData() {
        mLupaCsvData.clear();
        if (mLupaInputFile == null) return;
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(mLupaInputFile), "UTF-8"));
            String headerLine = br.readLine();
            if (headerLine != null) {
                if (headerLine.startsWith("\uFEFF")) headerLine = headerLine.substring(1);
                String[] headers = headerLine.split(";", -1);
                int attrCount = Math.max(0, headers.length - 3);
                mLupaAttrColumnCount = attrCount;
                mLupaColumnNames = new String[attrCount];
                for (int i = 0; i < attrCount; i++) {
                    String name = headers[3 + i].trim();
                    mLupaColumnNames[i] = name.isEmpty() ? ("Sloupec " + (i + 1)) : name;
                }
            } else {
                mLupaAttrColumnCount = 0;
                mLupaColumnNames = new String[0];
            }
            buildLupaGroupsUi();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(";", -1);
                if (cols.length < 3) continue;
                String tid = cols[2].trim().replace("-", "").toUpperCase();
                if (!tid.isEmpty()) mLupaCsvData.put(tid, cols);
            }
            br.close();
            File f = new File(mLupaInputFile);
            tvLupaFilePath.setText(f.getName() + " (" + mLupaCsvData.size() + " záznamů)");
            toast("✅ Načteno " + mLupaCsvData.size() + " záznamů");
        } catch (Exception e) {
            toast("Chyba načítání: " + e.getMessage());
        }
    }

    private void buildLupaGroupsUi() {
        if (llLupaGroupsContainer == null) return;
        llLupaGroupsContainer.removeAllViews();
        if (mLupaAttrColumnCount <= 0) return;

        int purple = colorRes(R.color.vyhybka_accent);
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (10 * density);
        int margin = (int) (6 * density);

        for (int i = 0; i < mLupaAttrColumnCount; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            if (i + 2 < mLupaAttrColumnCount) {
                ((LinearLayout.LayoutParams) row.getLayoutParams()).bottomMargin = margin;
            }

            for (int j = 0; j < 2 && (i + j) < mLupaAttrColumnCount; j++) {
                int idx = i + j;
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackgroundResource(R.drawable.chip_bar_bg);
                card.setPadding(pad, pad, pad, pad);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (j == 0 && (i + 1) < mLupaAttrColumnCount) cardLp.setMarginEnd(margin / 2);
                if (j == 1) cardLp.setMarginStart(margin / 2);
                card.setLayoutParams(cardLp);

                TextView lbl = new TextView(this);
                lbl.setText(mLupaColumnNames[idx] + ":");
                lbl.setTextColor(purple);
                lbl.setTextSize(10);
                lbl.setTypeface(null, android.graphics.Typeface.BOLD);
                lbl.setLetterSpacing(0.03f);
                card.addView(lbl);

                TextView val = new TextView(this);
                val.setText("—");
                val.setTextColor(purple);
                val.setTextSize(22);
                val.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                val.setPadding(0, (int) (4 * density), 0, 0);
                val.setTag("lupa_val_" + idx);
                card.addView(val);

                row.addView(card);
            }
            if ((i + 2) > mLupaAttrColumnCount && mLupaAttrColumnCount % 2 == 1) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
                row.addView(spacer);
            }
            llLupaGroupsContainer.addView(row);
        }
    }

    private void lookupAndDisplayFromCsv(String tid) {
        String tidKey = tid.replace("-", "").toUpperCase();
        String[] row = mLupaCsvData.get(tidKey);
        if (row == null) {
            llLupaGroups.setVisibility(View.GONE);
            toast("TID nenalezeno v CSV");
            return;
        }
        if (llLupaGroupsContainer != null) {
            for (int i = 0; i < mLupaAttrColumnCount; i++) {
                TextView valTv = llLupaGroupsContainer.findViewWithTag("lupa_val_" + i);
                if (valTv == null) continue;
                int col = 3 + i;
                String val = (col < row.length) ? row[col].trim() : "";
                String stripped = val.replaceFirst("^0+", "");
                if (stripped.isEmpty()) stripped = val.isEmpty() ? "—" : "0";
                valTv.setText(stripped);
            }
        }
        llLupaGroups.setVisibility(View.VISIBLE);
    }

    private void saveLupaSettings() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("lupa_file", mLupaInputFile).apply();
    }

    private void loadLupaSettings() {
        mLupaInputFile = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lupa_file", null);
        if (mLupaInputFile != null) {
            File f = new File(mLupaInputFile);
            if (f.exists()) {
                loadLupaCsvData();
                etLupaFileName.setText(f.getName().replace(".csv", ""));
            } else {
                mLupaInputFile = null;
            }
        }
    }
}
