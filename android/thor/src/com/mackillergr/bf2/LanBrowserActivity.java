package com.mackillergr.bf2;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.mackillergr.bf2.lan.LanDiscovery;
import com.mackillergr.bf2.lan.LanJoinArgs;
import com.mackillergr.bf2.lan.LanQuery;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Integrated LAN server browser. Finds GameSpy metadata; does not perform BF2 join itself. */
public final class LanBrowserActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout found;
    private TextView status;
    private EditText ipField;
    private int queryPort;
    private Button scan, query;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        queryPort = getIntent().getIntExtra("queryPort", 29900);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(15, 23, 35));
        LinearLayout root = new LinearLayout(this);
        root.setPadding(18,18,18,18); root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        status = text(root, "LAN Server Browser — UDP query " + queryPort);
        ipField = new EditText(this); ipField.setHint("IP PC server");
        ipField.setText("192.168.1.100"); ipField.setSingleLine(true);
        ipField.setInputType(InputType.TYPE_CLASS_PHONE); ipField.setTextColor(Color.WHITE);
        ipField.setHintTextColor(Color.LTGRAY); root.addView(ipField);
        scan = new Button(this); scan.setText("Broadcast LAN scan"); root.addView(scan);
        query = new Button(this); query.setText("Query IP"); root.addView(query);
        found = new LinearLayout(this); found.setOrientation(LinearLayout.VERTICAL); root.addView(found);
        scan.setOnClickListener(v -> runQuery(true));
        query.setOnClickListener(v -> runQuery(false));
        setContentView(scroll);
    }
    private TextView text(LinearLayout root, String content) {
        TextView t = new TextView(this); t.setText(content); t.setTextColor(Color.WHITE);
        t.setTextSize(16); t.setPadding(4,8,4,8); root.addView(t); return t;
    }
    private void busy(boolean b) { scan.setEnabled(!b); query.setEnabled(!b); }
    private void runQuery(boolean broadcast) {
        String ip = ipField.getText().toString().trim();
        if (!broadcast && !LanJoinArgs.isIPv4(ip)) {
            status.setText("Enter a valid IPv4 address."); return;
        }
        found.removeAllViews(); busy(true);
        status.setText(broadcast ? "Searching LAN…" : "Query: " + ip + ":" + queryPort);
        worker.execute(() -> {
            try {
                List<LanQuery.Result> list = broadcast ? LanDiscovery.scan(queryPort, 2200) :
                        Collections.singletonList(LanQuery.query(ip, queryPort, 2200));
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy(false);
                    status.setText("Servers: " + list.size() + "");
                    for (LanQuery.Result server : list) {
                        Button row = new Button(this);
                        row.setAllCaps(false);
                        row.setText(server.hostname + " | " + server.ip + ":" + server.gamePort +
                                "\n" + server.map + " | " + server.players + "/" + server.maxPlayers +
                                " | BF2 " + server.version);
                        found.addView(row);
                        row.setOnClickListener(v -> {
                            Intent selected = new Intent();
                            selected.putExtra("ip", server.ip);
                            selected.putExtra("gamePort", server.gamePort);
                            setResult(RESULT_OK, selected); finish();
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy(false);
                    status.setText("BF2 query failed: " + e.getClass().getSimpleName() +
                            " — " + e.getMessage());
                });
            }
        });
    }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
