package cn.xingce.timer;

import android.app.Activity;
import android.os.*;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.util.UUID;

public class MainActivity extends Activity {
    private WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Bridge bridge;
    private ToneGenerator tone;
    private int ticks;
    private long previousTotal=Long.MAX_VALUE;
    private long[] previousModules=new long[6];
    private final Runnable tick = new Runnable() { public void run() {
        synchronized(bridge) {
            long[] times=bridge.engine.times();
            long total=0; for(long t:times)total+=t;
            long remain=bridge.engine.totalBudget()-total;
            boolean alert=previousTotal>0&&remain<=0;
            for(int i=0;i<6;i++) {
                long r=bridge.engine.budgets[i]-times[i];
                if(previousModules[i]>0&&r<=0)alert=true;
                previousModules[i]=r;
            }
            previousTotal=remain;
            if(bridge.engine.running&&alert&&tone!=null)tone.startTone(ToneGenerator.TONE_PROP_BEEP2,600);
            if(bridge.engine.running&&++ticks%5==0)bridge.persist();
        }
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        bridge=new Bridge();
        try { tone=new ToneGenerator(AudioManager.STREAM_NOTIFICATION,85); } catch(RuntimeException ignored){}
        web=new WebView(this);
        web.setBackgroundColor(Color.rgb(245,247,251));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setTextZoom(100);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
        });
        web.addJavascriptInterface(bridge,"Native");
        setContentView(web);
        // Target 35 uses edge-to-edge. Explicit insets preserve status/navigation areas.
        web.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        web.loadUrl("file:///android_asset/index.html");
        updateScreen(); handler.post(tick);
    }
    private void updateScreen(){runOnUiThread(()->{
        if(bridge.engine.running)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    });}
    @Override protected void onStop(){super.onStop(); if(bridge!=null)bridge.persist();}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);if(tone!=null)tone.release();if(web!=null){web.removeJavascriptInterface("Native");web.destroy();}super.onDestroy();}
    @Override public void onBackPressed(){web.evaluateJavascript("window.handleBack && window.handleBack()",null);}

    public final class Bridge {
        final SharedPreferences prefs=getSharedPreferences("xingce_local_v1",MODE_PRIVATE);
        final TimerEngine engine=new TimerEngine(SystemClock::elapsedRealtime);
        JSONArray history;
        int boot(){return Settings.Global.getInt(getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
        Bridge(){
            try { history=new JSONArray(prefs.getString("history","[]")); }catch(JSONException ex){history=new JSONArray();}
            try {
                JSONObject s=new JSONObject(prefs.getString("session","{}"));
                if(s.has("budgets"))engine.budgets=readTimes(s.getJSONArray("budgets"));
                if(s.has("spent"))engine.spent=readTimes(s.getJSONArray("spent"));
                engine.active=Math.max(0,Math.min(5,s.optInt("active",0)));
                engine.startedAt=s.optLong("startedAt",0);
                engine.anchor=s.optLong("anchor",SystemClock.elapsedRealtime());
                engine.running=s.optBoolean("running",false)&&boot()!=-1&&s.optInt("boot",-2)==boot()&&SystemClock.elapsedRealtime()>=engine.anchor;
            }catch(Exception ignored){engine.reset();}
        }
        long[] readTimes(JSONArray a)throws JSONException {if(a.length()!=6)throw new JSONException("模块数量不正确");long[] t=new long[6];for(int i=0;i<6;i++)t[i]=Math.max(0,a.getLong(i));return t;}
        JSONArray arr(long[] values){JSONArray a=new JSONArray();for(long v:values)a.put(v);return a;}
        JSONObject session()throws JSONException {
            JSONObject s=new JSONObject();s.put("spent",arr(engine.times()));s.put("budgets",arr(engine.budgets));s.put("anchor",SystemClock.elapsedRealtime());s.put("active",engine.active);s.put("running",engine.running);s.put("startedAt",engine.startedAt);s.put("boot",boot());return s;
        }
        synchronized void persist(){try {if(!prefs.edit().putString("session",session().toString()).commit())throw new IllegalStateException("存储空间不足");}catch(JSONException e){throw new IllegalStateException(e);}}
        @JavascriptInterface public synchronized String snapshot(){try{return session().toString();}catch(Exception e){return "{}";}}
        @JavascriptInterface public synchronized String history(){return history.toString();}
        @JavascriptInterface public void minimize(){runOnUiThread(()->moveTaskToBack(true));}
        @JavascriptInterface public synchronized String action(String name,String payload){
            try {
                if(name.equals("start"))engine.start(System.currentTimeMillis());
                else if(name.equals("pause"))engine.pause();
                else if(name.equals("select"))engine.select(Integer.parseInt(payload));
                else if(name.equals("configure"))engine.configure(readTimes(new JSONArray(payload)));
                else if(name.equals("finish")) {
                    if(engine.startedAt==0)throw new IllegalStateException("请先开始计时");
                    JSONObject row=new JSONObject();row.put("id",UUID.randomUUID().toString());row.put("title",payload.trim().isEmpty()?"行测模考":payload.trim().substring(0,Math.min(payload.trim().length(),40)));row.put("date",engine.startedAt);row.put("endedAt",System.currentTimeMillis());row.put("spent",arr(engine.times()));row.put("budgets",arr(engine.budgets));
                    JSONArray next=new JSONArray();next.put(row);for(int i=0;i<history.length();i++)next.put(history.get(i));
                    JSONObject cleared=session();cleared.put("spent",arr(new long[6]));cleared.put("running",false);cleared.put("startedAt",0);cleared.put("active",0);
                    if(!prefs.edit().putString("history",next.toString()).putString("session",cleared.toString()).commit())throw new IllegalStateException("保存失败，请检查存储空间");
                    history=next;engine.reset();updateScreen();return "";
                } else if(name.equals("discard"))engine.reset();
                else if(name.equals("delete")) {
                    JSONArray next=new JSONArray();for(int i=0;i<history.length();i++)if(!history.getJSONObject(i).getString("id").equals(payload))next.put(history.get(i));
                    if(!prefs.edit().putString("history",next.toString()).commit())throw new IllegalStateException("删除失败");history=next;return "";
                } else throw new IllegalArgumentException("未知操作");
                persist();updateScreen();return "";
            }catch(Exception e){return e.getMessage()==null?"操作失败":e.getMessage();}
        }
    }
}
