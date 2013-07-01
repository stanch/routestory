package net.routestory.model;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;

import net.routestory.parts.Cacher;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.ektorp.CouchDbConnector;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

@SuppressLint({ "DefaultLocale" })
@JsonIgnoreProperties(ignoreUnknown=true)
public class Author extends org.ektorp.support.CouchDbDocument implements CouchDbObject {
	private static final long serialVersionUID = 1L;
	
	@JsonProperty("type")
	public final String type = "author";
	
	@JsonProperty("name")
	public String name;
	
	@JsonProperty("link")
	public String link;
	
	@JsonProperty("picture")
	public String picture;
	
	@JsonIgnore
	public void bind(CouchDbConnector couch) {
		// pass
	}
	
	public Author() {}
	
	@JsonIgnore
	public Cacher<Bitmap> getPicture() {
		return new Cacher<Bitmap>() {
			public File getCacheFile(Context context) {
				return new File(String.format("%s/%s-picture.bin", context.getExternalCacheDir().getAbsolutePath(), getId()));
			}
			public boolean isCached(Context context) {
				return getCacheFile(context).exists();
			}
			public synchronized Bitmap get(Context context) {
				return BitmapFactory.decodeFile(getCacheFile(context).getAbsolutePath());
			}
			public synchronized boolean cache(Context context) {
				if (picture == null) return false;
		 		try {
		 			File cache = getCacheFile(context);
	 				InputStream input = (InputStream)new URL(picture).getContent();
	 				cache.setReadable(true, false);
	 				FileOutputStream output = new FileOutputStream(cache);
	 				IOUtils.copy(input, output);
	 				output.close();
	 				Log.v("Author", "Cached avatar for " + getId());
		 		} catch (Exception e) {
		 			return false;
		 		}
		 		return true;
			}
		};
	}
}
