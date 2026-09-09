package com.aibrowser.knowledgehub;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SiteInspector {
    public static class Result { public String template,status,reason,notes; public Result(String t,String s,String r,String n){template=t;status=s;reason=r;notes=n;} }
    private static final Pattern FORM = Pattern.compile("<form[^>]*action=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</form>", Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    private static final Pattern INPUT = Pattern.compile("<input[^>]*name=[\\\"']([^\\\"']+)[\\\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    public Result inspect(String baseUrl) {
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(baseUrl).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Mozilla/5.0 AI Browser Android");
            int code=c.getResponseCode();BufferedReader br=new BufferedReader(new InputStreamReader(code>=400?c.getErrorStream():c.getInputStream(),StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null && sb.length()<500_000)sb.append(line).append('\n');String html=sb.toString();String low=html.toLowerCase(Locale.US);
            if(code==403||code==429||low.contains("captcha")||low.contains("verify you are human")||low.contains("security challenge")) return new Result(null,"NEEDS ATTENTION","HUMAN CHALLENGE","Open the site and complete the human check; AI Browser will resume afterward.");
            Matcher fm=FORM.matcher(html);while(fm.find()){Matcher im=INPUT.matcher(fm.group(2));while(im.find()){String name=im.group(1);if(name.equalsIgnoreCase("q")||name.equalsIgnoreCase("query")||name.equalsIgnoreCase("search")||name.equalsIgnoreCase("keyword")){URI base=URI.create(baseUrl);URI action=base.resolve(fm.group(1));String template=action.toString()+(action.toString().contains("?")?"&":"?")+URLEncoder.encode(name,StandardCharsets.UTF_8)+"={query}";return new Result(template,"READY",null,"Search form learned automatically.");}}}
            return new Result(null,"NEEDS ATTENTION","TEACH SEARCH","No reusable search form was found. Use Teach Site to show the app how you search this site.");
        } catch(Exception e){return new Result(null,"NEEDS ATTENTION","NETWORK ERROR",e.getClass().getSimpleName()+": "+e.getMessage());} finally {if(c!=null)c.disconnect();}
    }
}
