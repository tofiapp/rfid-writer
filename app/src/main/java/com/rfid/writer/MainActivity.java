package com.rfid.writer;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.rscja.deviceapi.RFIDWithUHFUART;
import com.rscja.deviceapi.entity.UHFTAGInfo;
import com.rscja.deviceapi.interfaces.IUHFInventoryCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    // ── SDK instance ──────────────────────────────────────────────────────────
    private RFIDWithUHFUART mReader;

    // ── UI ───────────────────────────────────────────────────────────────────
    private TextView tvStatus, tvCurrentEpc, tvTid, tvLog;
    private EditText etNewEpc, etPassword;
    private Button btnScan, btnWrite, btnLockEpc, btnLockAll;
    private ScrollView scrollLog;

    // ── State ─────────────────────────────────────────────────────────────────
    private String mCurrentEpc = null;
    private boolean mInventorying = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        initReader();
        bindButtons();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReader != null) {
            if (mInventorying) mReader.stopInventory();
            mReader.free();
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    private void bindViews() {
        tvStatus     = findViewById(R.id.tvStatus);
        tvCurrentEpc = findViewById(R.id.tvCurrentEpc);
        tvTid        = findViewById(R.id.tvTid);
        tvLog        = findViewById(R.id.tvLog);
        etNewEpc     = findViewById(R.id.etNewEpc);
        etPassword   = findViewById(R.id.etPassword);
        btnScan      = findViewById(R.id.btnScan);
        btnWrite     = findViewById(R.id.btnWrite);
        btnLockEpc   = findViewById(R.id.btnLockEpc);
        btnLockAll   = findViewById(R.id.btnLockAll);
        scrollLog    = (ScrollView) tvLog.getParent();
    }

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

    // ── Buttons ───────────────────────────────────────────────────────────────
    private void bindButtons() {
        btnScan.setOnClickListener(v -> {
            if (mInventorying) stopScan();
            else startScan();
        });

        btnWrite.setOnClickListener(v -> writeEpc());
        btnLockEpc.setOnClickListener(v -> lockTag(false));
        btnLockAll.setOnClickListener(v -> lockTag(true));
    }

    // ── Scan ──────────────────────────────────────────────────────────────────
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

        // Use EPC+TID mode so we get TID as well
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
        // Stop after first tag
        stopScan();

        mCurrentEpc = epc;
        tvCurrentEpc.setText(epc != null ? epc : "—");
        tvTid.setText(tid != null ? tid : "—");

        // Enable action buttons
        btnWrite.setEnabled(true);
        btnLockEpc.setEnabled(true);
        btnLockAll.setEnabled(true);

        log("TAG nalezen — EPC: " + epc);
        if (tid != null) log("              TID: " + tid);
    }

    // ── Write EPC ─────────────────────────────────────────────────────────────
    private void writeEpc() {
        String newEpc = etNewEpc.getText().toString().trim().toUpperCase().replaceAll("\\s", "");

        if (newEpc.isEmpty()) {
            toast("Zadejte nový EPC");
            return;
        }
        if (newEpc.length() % 4 != 0 || newEpc.length() < 4) {
            toast("EPC musí mít délku dělitelnou 4 (hex znaky)");
            return;
        }
        if (!newEpc.matches("[0-9A-F]+")) {
            toast("EPC smí obsahovat pouze hex znaky 0-9, A-F");
            return;
        }

        String accessPwd = getPassword();
        log("Zapisuji EPC: " + newEpc);
        setStatus("● Zapisuji...", "#FF9800");
        btnWrite.setEnabled(false);

        new Thread(() -> {
            // writeDataToEpc(accessPassword, newEPC) — writes to EPC bank
            boolean ok = mReader.writeDataToEpc(accessPwd, newEpc);
            mHandler.post(() -> {
                btnWrite.setEnabled(true);
                if (ok) {
                    mCurrentEpc = newEpc;
                    tvCurrentEpc.setText(newEpc);
                    setStatus("● EPC zapsáno OK", "#4CAF50");
                    log("✓ EPC úspěšně zapsáno: " + newEpc);
                    toast("EPC zapsáno!");
                } else {
                    setStatus("● Zápis selhal", "#F44336");
                    log("✗ Zápis EPC selhal — zkontrolujte heslo a dosah");
                    toast("Zápis selhal");
                }
            });
        }).start();
    }

    // ── Lock tag ─────────────────────────────────────────────────────────────
    /**
     * lockAll = false → zamkne pouze EPC bank (přepis EPC vyžaduje heslo)
     * lockAll = true  → zamkne EPC + USER + RESERVED (plný lock)
     *
     * generateLockCode(bank, type):
     *   bank: 1=EPC, 2=TID, 3=USER, 0=ACCESS_PWD, 4=KILL_PWD
     *   type: 1=lock, 2=perma-lock, 3=unlock, 4=perma-unlock
     */
    private void lockTag(boolean lockAll) {
        String accessPwd = getPassword();
        if (accessPwd.equals("00000000")) {
            // Remind user to set a password before locking
            if (TextUtils.isEmpty(etPassword.getText().toString().trim())) {
                toast("Tip: bez hesla bude tag zamčen s výchozím 00000000");
            }
        }

        log("Zamykám tag" + (lockAll ? " (kompletní lock)" : " (jen EPC)") + "...");
        setStatus("● Zamykám...", "#FF9800");
        btnLockEpc.setEnabled(false);
        btnLockAll.setEnabled(false);

        new Thread(() -> {
            boolean ok;
            if (lockAll) {
                // Lock EPC bank + USER bank
                ArrayList<Integer> banksEpc  = new ArrayList<>(); banksEpc.add(1);
                ArrayList<Integer> banksUser = new ArrayList<>(); banksUser.add(3);
                String lockCodeEpc  = mReader.generateLockCode(banksEpc,  1);
                String lockCodeUser = mReader.generateLockCode(banksUser, 1);
                boolean r1 = mReader.lockMem(accessPwd, lockCodeEpc);
                boolean r2 = mReader.lockMem(accessPwd, lockCodeUser);
                ok = r1 && r2;
            } else {
                ArrayList<Integer> banks = new ArrayList<>(); banks.add(1);
                String lockCode = mReader.generateLockCode(banks, 1);
                ok = mReader.lockMem(accessPwd, lockCode);
            }

            boolean finalOk = ok;
            mHandler.post(() -> {
                btnLockEpc.setEnabled(true);
                btnLockAll.setEnabled(true);
                if (finalOk) {
                    setStatus("● Tag zamčen", "#4CAF50");
                    log("✓ Tag úspěšně zamčen" + (lockAll ? " (kompletní)" : " (EPC)"));
                    log("  Heslo: " + accessPwd);
                    toast("Tag zamčen!");
                } else {
                    setStatus("● Lock selhal", "#F44336");
                    log("✗ Zamčení selhalo — zkontrolujte heslo a dosah");
                    toast("Lock selhal");
                }
            });
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String getPassword() {
        String pwd = etPassword.getText().toString().trim().toUpperCase();
        if (pwd.isEmpty() || pwd.length() != 8) return "00000000";
        return pwd;
    }

    private void setStatus(String text, String hexColor) {
        tvStatus.setText(text);
        tvStatus.setTextColor(Color.parseColor(hexColor));
    }

    private void log(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = "[" + time + "] " + message + "\n";
        tvLog.append(line);
        scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
