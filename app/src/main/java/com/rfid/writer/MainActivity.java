package com.rfid.writer;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayout;
import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Chainway C5 physical scan trigger keycode
    private static final int TRIGGER_KEYCODE = 293;

    // ── SDK ───────────────────────────────────────────────────────────────
    private RFIDWithUHFUART mReader;

    // ── Global header ─────────────────────────────────────────────────────
    private TextView tvStatus;

    // ── Tab layout + pages ────────────────────────────────────────────────
    private TabLayout tabLayout;
    private View pageTag, pageWriteEpc, pageGroupWrite, pageLock;

    // ── Page 0: Načítání tagů ─────────────────────────────────────────────
    private Button btnScan;
    private TextView tvCurrentEpc, tvTid, tvLog;
    private ScrollView scrollLog;

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
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupTabs();
        bindButtons();
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
                // skupinový zápis — zatím prázdné
                break;
            case 3:
                // fyzické tlačítko na záložce Zamčení spustí Lock
                lockTag();
                break;
        }
    }

    // ── Binding ───────────────────────────────────────────────────────────

    private void bindViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tabLayout = findViewById(R.id.tabLayout);

        pageTag        = findViewById(R.id.pageTag);
        pageWriteEpc   = findViewById(R.id.pageWriteEpc);
        pageGroupWrite = findViewById(R.id.pageGroupWrite);
        pageLock       = findViewById(R.id.pageLock);

        // Page 0
        btnScan      = findViewById(R.id.btnScan);
        tvCurrentEpc = findViewById(R.id.tvCurrentEpc);
        tvTid        = findViewById(R.id.tvTid);
        tvLog        = findViewById(R.id.tvLog);
        scrollLog    = findViewById(R.id.scrollLog);

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
        btnScan.setOnClickListener(v -> { if (mInventorying) stopScan(); else startScan(); });
        btnWrite.setOnClickListener(v -> writeEpc());
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
                        log("SDK inicializováno OK");
                    } else {
                        setStatus("● Chyba inicializace", "#F44336");
                        log("CHYBA: init() vrátil false");
                    }
                });
            } catch (Exception e) {
                mHandler.post(() -> {
                    setStatus("● SDK chyba: " + e.getMessage(), "#F44336");
                    log("VÝJIMKA: " + e.getMessage());
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
        tvCurrentEpc.setText(epc != null ? epc : "—");
        tvTid.setText(tid != null ? tid : "—");
        log("TAG nalezen — EPC: " + epc);
        if (tid != null) log("              TID: " + tid);
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

        log("Zapisuji EPC: " + newEpc + "  (bank=EPC, prt=" + prt + ", len=" + len + ")");
        setStatus("● Zapisuji EPC...", "#FF9800");
        btnWrite.setEnabled(false);

        final int finalPrt = prt;
        new Thread(() -> {
            boolean ok = mReader.writeData(accessPwd, 1, finalPrt, newEpc);
            mHandler.post(() -> {
                btnWrite.setEnabled(true);
                if (ok) {
                    tvCurrentEpc.setText(newEpc);
                    setStatus("● EPC zapsáno OK", "#4CAF50");
                    log("✓ EPC zapsáno: " + newEpc);
                    toast("EPC zapsáno!");
                } else {
                    setStatus("● Zápis EPC selhal", "#F44336");
                    log("✗ Zápis EPC selhal — zkontrolujte heslo a dosah");
                    toast("Zápis selhal");
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

        log("Zapisuji heslo: " + newPwd + "  (bank=Reserved, prt=2, len=2)");
        setStatus("● Zapisuji heslo...", "#FF9800");
        btnWritePwd.setEnabled(false);

        new Thread(() -> {
            // Reserved bank (0), word pointer 2 = access password location
            boolean ok = mReader.writeData(currentPwd, 0, 2, newPwd);
            mHandler.post(() -> {
                btnWritePwd.setEnabled(true);
                if (ok) {
                    setStatus("● Heslo zapsáno", "#4CAF50");
                    log("✓ Heslo zapsáno: " + newPwd);
                    // auto-fill Lock Access Pwd with the new password
                    etLockAccessPwd.setText(newPwd);
                    toast("Heslo zapsáno!");
                } else {
                    setStatus("● Zápis hesla selhal", "#F44336");
                    log("✗ Zápis hesla selhal — zkontrolujte stávající heslo a dosah");
                    toast("Zápis hesla selhal");
                }
            });
        }).start();
    }

    // ── Lock (Page 3 — sekce Zamčení) ─────────────────────────────────────

    private void lockTag() {
        if (mReader == null) return;

        String accessPwd = readPassword(etLockAccessPwd);
        String lockCode = etLockCode.getText().toString().trim().toUpperCase();
        if (lockCode.isEmpty()) lockCode = "008020";

        log("Zamykám tag — kód: " + lockCode);
        setStatus("● Zamykám...", "#FF9800");
        btnLock.setEnabled(false);

        final String finalCode = lockCode;
        new Thread(() -> {
            boolean ok = mReader.lockMem(accessPwd, finalCode);
            mHandler.post(() -> {
                btnLock.setEnabled(true);
                if (ok) {
                    setStatus("● Tag zamčen", "#4CAF50");
                    log("✓ Tag zamčen (kód: " + finalCode + ")");
                    toast("Tag zamčen!");
                } else {
                    setStatus("● Lock selhal", "#F44336");
                    log("✗ Zamčení selhalo — zkontrolujte heslo a dosah");
                    toast("Lock selhal");
                }
            });
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Returns password from field, falls back to "00000000" if empty or not 8 valid hex chars. */
    private String readPassword(EditText field) {
        String val = field.getText().toString().trim().toUpperCase();
        if (val.length() == 8 && val.matches("[0-9A-F]+")) return val;
        return "00000000";
    }

    private void setStatus(String text, String hexColor) {
        tvStatus.setText(text);
        tvStatus.setTextColor(Color.parseColor(hexColor));
    }

    private void log(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tvLog.append("[" + time + "] " + message + "\n");
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
