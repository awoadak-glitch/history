package com.atheer.shell;
import android.content.*;import android.content.pm.ProviderInfo;import android.database.Cursor;import android.net.Uri;import android.os.*;import java.io.*;
/** Lazy file-provider adapter; preserves the feature module's path policy. */
public final class SourcesFileProvider extends ContentProvider {
    private ContentProvider delegate;private ProviderInfo info;
    @Override public void attachInfo(Context context,ProviderInfo value){info=new ProviderInfo(value);super.attachInfo(context,value);}
    @Override public boolean onCreate(){return true;}
    private synchronized ContentProvider source(){
        if(delegate!=null)return delegate;
        try{ModuleRuntime.prepare();ContentProvider p=(ContentProvider)ModuleRuntime.loader.loadClass("androidx.core.content.FileProvider").getConstructor().newInstance();
            p.attachInfo(ModuleRuntime.context(getContext()),info);delegate=p;return p;
        }catch(Exception e){throw new IllegalStateException("Source file provider",e);}
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return source().query(u,p,s,a,o);}
    @Override public String getType(Uri u){return source().getType(u);}
    @Override public Uri insert(Uri u,ContentValues v){return source().insert(u,v);}
    @Override public int delete(Uri u,String s,String[] a){return source().delete(u,s,a);}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){return source().update(u,v,s,a);}
    @Override public ParcelFileDescriptor openFile(Uri u,String m)throws FileNotFoundException{return source().openFile(u,m);}
}
