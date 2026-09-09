package com.aibrowser.knowledgehub;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnifiedSearch {
    public static class Hit { public String source,title,snippet,url; public Hit(String s,String t,String sn,String u){source=s;title=t;snippet=sn;url=u;} }
    private static final Pattern TITLE = Pattern.compile("<(h1|h2|h3|a)[^>]*>(.*?)</\\1>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    public List<Hit> searchSite(KnowledgeDb.Site site,String query){ArrayList<Hit> out=new ArrayList<>();if(site.searchTemplate==null||site.searchTemplate.isEmpty())return out;HttpURLConnection c=null;try{String u=site.searchTemplate.replace("{query}", URLEncoder.encode(query,StandardCharsets.UTF_8));c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","Mozilla/5.0 AI Browser Android");BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null&&sb.length()<600_000)sb.append(line).append('\n');String html=sb.toString();Matcher m=TITLE.matcher(html);int count=0;while(m.find()&&count<5){String text=clean(m.group(2));if(text.length()<12)continue;if(!text.toLowerCase(Locale.US).contains(query.toLowerCase(Locale.US).split(" ")[0]))continue;out.add(new Hit(site.name,text,nearbyText(html,m.end()),u));count++;}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}return out;}
    private String clean(String s){return s.replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim();}
    private String nearbyText(String html,int start){int end=Math.min(html.length(),start+500);return clean(html.substring(start,end));}
}
