package com.aibrowser.knowledgehub;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeDb extends SQLiteOpenHelper {
    public static class Site {
        public long id; public String name, url, searchTemplate, status, reason, notes; public long lastChecked;
    }
    public static class Document {
        public long id; public String name, content; public long addedAt;
    }

    public KnowledgeDb(Context context) { super(context, "ai_browser.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sites(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,url TEXT NOT NULL UNIQUE,search_template TEXT,status TEXT,reason TEXT,notes TEXT,last_checked INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE documents(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,content TEXT NOT NULL,added_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE navigation_steps(id INTEGER PRIMARY KEY AUTOINCREMENT,site_id INTEGER NOT NULL,position INTEGER NOT NULL,action TEXT NOT NULL,target TEXT,value TEXT,FOREIGN KEY(site_id) REFERENCES sites(id))");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public long saveSite(String name, String url, String template, String status, String reason, String notes, long lastChecked) {
        ContentValues v=new ContentValues(); v.put("name",name);v.put("url",url);v.put("search_template",template);v.put("status",status);v.put("reason",reason);v.put("notes",notes);v.put("last_checked",lastChecked);
        return getWritableDatabase().insertWithOnConflict("sites",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateSite(long id, String template, String status, String reason, String notes, long lastChecked) {
        ContentValues v=new ContentValues();v.put("search_template",template);v.put("status",status);v.put("reason",reason);v.put("notes",notes);v.put("last_checked",lastChecked);
        getWritableDatabase().update("sites",v,"id=?",new String[]{String.valueOf(id)});
    }

    public List<Site> sites() {
        ArrayList<Site> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,url,search_template,status,reason,notes,last_checked FROM sites ORDER BY name",null)){
            while(c.moveToNext()){Site s=new Site();s.id=c.getLong(0);s.name=c.getString(1);s.url=c.getString(2);s.searchTemplate=c.getString(3);s.status=c.getString(4);s.reason=c.getString(5);s.notes=c.getString(6);s.lastChecked=c.getLong(7);out.add(s);} }
        return out;
    }

    public long addDocument(String name,String content){ContentValues v=new ContentValues();v.put("name",name);v.put("content",content);v.put("added_at",System.currentTimeMillis());return getWritableDatabase().insert("documents",null,v);}
    public List<Document> documents(){ArrayList<Document> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,content,added_at FROM documents ORDER BY added_at DESC",null)){while(c.moveToNext()){Document d=new Document();d.id=c.getLong(0);d.name=c.getString(1);d.content=c.getString(2);d.addedAt=c.getLong(3);out.add(d);}}return out;}
    public List<Document> searchDocuments(String q){ArrayList<Document> out=new ArrayList<>();String like="%"+q.replace("%","\\%").replace("_","\\_")+"%";try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,content,added_at FROM documents WHERE content LIKE ? ESCAPE '\\\\' OR name LIKE ? ESCAPE '\\\\' ORDER BY added_at DESC",new String[]{like,like})){while(c.moveToNext()){Document d=new Document();d.id=c.getLong(0);d.name=c.getString(1);d.content=c.getString(2);d.addedAt=c.getLong(3);out.add(d);}}return out;}
}
