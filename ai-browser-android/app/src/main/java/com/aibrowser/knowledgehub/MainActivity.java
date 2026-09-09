package com.aibrowser.knowledgehub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io=Executors.newFixedThreadPool(4);
    private KnowledgeDb db; private LinearLayout body; private TextView status; private EditText query;
    private final int BG=Color.rgb(11,15,20), PANEL=Color.rgb(18,24,32), TEXT=Color.rgb(244,247,251), MUTED=Color.rgb(154,168,183), ACCENT=Color.rgb(124,92,255);

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new KnowledgeDb(this);showHome();}
    @Override protected void onDestroy(){super.onDestroy();io.shutdownNow();db.close();}

    private TextView text(String s,int sp){TextView v=new TextView(this);v.setText(s);v.setTextColor(TEXT);v.setTextSize(sp);v.setPadding(dp(4),dp(6),dp(4),dp(6));return v;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setBackgroundColor(ACCENT);return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setSingleLine(true);return e;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}

    private void showHome(){ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(20),dp(16),dp(40));sc.addView(body);setContentView(sc);
        TextView title=text("AI Browser",30);title.setTypeface(null,1);body.addView(title);body.addView(text("One answer from all the sites and knowledge your AI has learned.",14));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER_VERTICAL);Button update=button("Update All");Button upload=button("Upload Data");Button add=button("Learn Site");actions.addView(update,new LinearLayout.LayoutParams(0,dp(52),1));actions.addView(upload,new LinearLayout.LayoutParams(0,dp(52),1));actions.addView(add,new LinearLayout.LayoutParams(0,dp(52),1));body.addView(actions);update.setOnClickListener(v->updateAll());upload.setOnClickListener(v->pickFile());add.setOnClickListener(v->showAddSite());
        status=text("",13);status.setTextColor(MUTED);body.addView(status);
        query=input("Ask across everything you have taught me…");body.addView(query,new LinearLayout.LayoutParams(-1,dp(58)));Button search=button("Search All + Combine");body.addView(search,new LinearLayout.LayoutParams(-1,dp(54)));search.setOnClickListener(v->searchAll());
        body.addView(text("SITE KNOWLEDGE",13));renderSites();body.addView(text("STORED KNOWLEDGE",13));renderDocuments();
    }

    private void renderSites(){List<KnowledgeDb.Site> sites=db.sites();if(sites.isEmpty()){TextView none=text("No learned sites yet. Tap Learn Site.",14);none.setTextColor(MUTED);body.addView(none);return;}for(KnowledgeDb.Site s:sites){LinearLayout card=card();TextView n=text(s.name,18);n.setTypeface(null,1);card.addView(n);card.addView(text(s.url,12));String state="NEEDS ATTENTION".equals(s.status)?s.status:Freshness.state(s.lastChecked,System.currentTimeMillis());TextView st=text(state+(s.reason!=null?" • "+s.reason:""),12);st.setTextColor(state.equals("FRESH")?Color.rgb(43,213,118):state.contains("ATTENTION")?Color.rgb(255,107,107):Color.rgb(255,189,69));card.addView(st);LinearLayout row=new LinearLayout(this);Button u=button("Update");Button teach=button("Teach Site");row.addView(u,new LinearLayout.LayoutParams(0,dp(48),1));row.addView(teach,new LinearLayout.LayoutParams(0,dp(48),1));card.addView(row);u.setOnClickListener(v->inspectOne(s));teach.setOnClickListener(v->openTeach(s));body.addView(card);}}
    private void renderDocuments(){List<KnowledgeDb.Document> docs=db.documents();if(docs.isEmpty()){TextView none=text("No uploaded data yet.",14);none.setTextColor(MUTED);body.addView(none);return;}for(KnowledgeDb.Document d:docs){LinearLayout c=card();c.addView(text(d.name,16));c.addView(text(Math.min(d.content.length(),500)+" characters stored",12));body.addView(c);}}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(12),dp(14),dp(12));c.setBackgroundColor(PANEL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,dp(8));c.setLayoutParams(p);return c;}

    private void showAddSite(){LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);int pad=dp(20);form.setPadding(pad,pad,pad,pad);EditText name=input("Site name");EditText url=input("https://example.com");form.addView(name);form.addView(url);new AlertDialog.Builder(this).setTitle("Learn a site").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Learn",(d,w)->{String n=name.getText().toString().trim(),u=url.getText().toString().trim();if(!u.startsWith("https://")&&!u.startsWith("http://"))u="https://"+u;final String fu=u;status.setText("Learning "+fu+"…");io.execute(()->{SiteInspector.Result r=new SiteInspector().inspect(fu);db.saveSite(n.isEmpty()?fu:n,fu,r.template,r.status,r.reason,r.notes,System.currentTimeMillis());runOnUiThread(this::showHome);});}).show();}
    private void inspectOne(KnowledgeDb.Site s){status.setText("Updating "+s.name+"…");io.execute(()->{SiteInspector.Result r=new SiteInspector().inspect(s.url);db.updateSite(s.id,r.template,r.status,r.reason,r.notes,System.currentTimeMillis());runOnUiThread(this::showHome);});}
    private void updateAll(){List<KnowledgeDb.Site> sites=db.sites();status.setText("Updating "+sites.size()+" learned sites…");io.execute(()->{for(KnowledgeDb.Site s:sites){SiteInspector.Result r=new SiteInspector().inspect(s.url);db.updateSite(s.id,r.template,r.status,r.reason,r.notes,System.currentTimeMillis());}runOnUiThread(this::showHome);});}

    private void pickFile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,41);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==41&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();io.execute(()->{try{InputStream in=getContentResolver().openInputStream(uri);BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null&&sb.length()<5_000_000)sb.append(line).append('\n');String name=uri.getLastPathSegment()==null?"uploaded-data":uri.getLastPathSegment();db.addDocument(name,sb.toString());runOnUiThread(this::showHome);}catch(Exception e){runOnUiThread(()->status.setText("Upload failed: "+e.getMessage()));}});}}

    private void searchAll(){String q=query.getText().toString().trim();if(q.isEmpty())return;status.setText("Searching learned sources…");io.execute(()->{StringBuilder out=new StringBuilder();List<KnowledgeDb.Document> local=db.searchDocuments(q);if(!local.isEmpty()){out.append("Stored knowledge\n\n");for(KnowledgeDb.Document d:local){String c=d.content;out.append("• ").append(d.name).append(": ").append(c,0,Math.min(c.length(),700)).append("\n\n");}}
            UnifiedSearch us=new UnifiedSearch();int checked=0;for(KnowledgeDb.Site s:db.sites()){if(!"READY".equals(s.status)||s.searchTemplate==null)continue;List<UnifiedSearch.Hit> hits=us.searchSite(s,q);if(!hits.isEmpty()){checked++;out.append(s.name).append("\n");for(UnifiedSearch.Hit h:hits)out.append("• ").append(h.title).append(" — ").append(h.snippet).append("\n");out.append('\n');}}
            String answer=out.length()==0?"I couldn't find a matching stored result yet. Update your sites, upload data, or teach a site how to search.":"Combined from "+checked+" live learned sites and "+local.size()+" stored knowledge items.\n\n"+out;
            runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Combined AI Browser Result").setMessage(answer).setPositiveButton("Close",null).show());});}

    private void openTeach(KnowledgeDb.Site s){WebView web=new WebView(this);web.setBackgroundColor(BG);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setWebViewClient(new WebViewClient());web.setWebChromeClient(new WebChromeClient());LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);TextView banner=text("Teach Mode: use this site normally. When you reach its search-results page, tap Save Search Page.",14);banner.setPadding(dp(12),dp(12),dp(12),dp(12));root.addView(banner);Button save=button("Save Search Page");root.addView(save,new LinearLayout.LayoutParams(-1,dp(52)));root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);web.loadUrl(s.url);save.setOnClickListener(v->{String current=web.getUrl();if(current==null){Toast.makeText(this,"Open a search results page first",Toast.LENGTH_SHORT).show();return;}String template=guessTemplateFromUrl(current);db.updateSite(s.id,template,template==null?"NEEDS ATTENTION":"READY",template==null?"TEACH QUERY":"",template==null?"Search page saved, but I couldn't identify which URL value was the query.":"Search navigation taught by you.",System.currentTimeMillis());showHome();});}
    private String guessTemplateFromUrl(String url){String[] keys={"q=","query=","search=","keyword=","term="};for(String k:keys){int p=url.indexOf(k);if(p>=0){int start=p+k.length(),end=url.indexOf('&',start);if(end<0)end=url.length();return url.substring(0,start)+"{query}"+url.substring(end);}}return null;}
}
