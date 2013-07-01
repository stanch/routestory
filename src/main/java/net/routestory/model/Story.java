package net.routestory.model;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

import net.routestory.parts.BitmapUtils;
import net.routestory.parts.Cacher;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.ektorp.Attachment;
import org.ektorp.AttachmentInputStream;
import org.ektorp.CouchDbConnector;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

@SuppressLint({ "DefaultLocale" })
@JsonIgnoreProperties(ignoreUnknown=true)
public class Story extends org.ektorp.support.CouchDbDocument implements CouchDbObject {
	private static final long serialVersionUID = 1L;
	
	@JsonIgnore
	private WeakReference<CouchDbConnector> couchRef = null;
	
	@JsonProperty("type")
	public final String type = "story";
	
	@JsonProperty("private")
	public boolean isPrivate;
	
	@JsonProperty("author")
	public String authorId;
	
	@JsonIgnore
	public Author author;
	
	@JsonProperty("starttime")
	public long starttime;
	
	@JsonProperty("duration")
	public int duration;
	
	@JsonProperty("title")
	public String title;
	
	@JsonProperty("description")
	public String description;
	
	@JsonProperty("tags")
	public String[] tags;

	@JsonProperty("locations")
	public List<LocationData> locations;
	@JsonProperty("audio")
	public List<AudioData> audio;
	@JsonProperty("audio_preview")
	public AudioData audioPreview;
	@JsonProperty("photos")
	public List<ImageData> photos;
	@JsonProperty("notes")
	public List<TextData> notes;
	@JsonProperty("voice")
	public List<AudioData> voice;
	@JsonProperty("heartbeat")
	public List<HeartbeatData> heartbeat;
	
	public Story() {
		starttime = System.currentTimeMillis()/1000L;
		locations = new LinkedList<LocationData>();
		audio = new LinkedList<AudioData>();
		photos = new LinkedList<ImageData>();
		notes = new LinkedList<TextData>();
		voice = new LinkedList<AudioData>();
		heartbeat = new LinkedList<HeartbeatData>();
	}
	
	@JsonIgnore
	// Init weak circular references in all children.
	public void bind(CouchDbConnector couch) {
		this.couchRef = new WeakReference<CouchDbConnector>(couch);
		@SuppressWarnings("serial")
		List<List<? extends TimedData>> fields = new LinkedList<List<? extends TimedData>>() {{
			this.add(audio);
			this.add(photos);
			this.add(notes);
			this.add(voice);
			this.add(heartbeat);
		}};
		for (List<? extends TimedData> field : fields) {
			for (TimedData data : field) {
				data.bind(this);
			}
		}
		if (audioPreview != null) {
			audioPreview.bind(this);
		}
	}
	
	@JsonIgnore
	public void start() {
		starttime = System.currentTimeMillis()/1000L;
		setId("story-" + Shortuuid.uuid());
		setRevision(null);
	}
	
	@JsonIgnore
	public void end() {
		duration = (int)(System.currentTimeMillis()/1000L-starttime);
	}
	
	@JsonIgnore
	public LocationData addLocation(long time, Location l) {
		LocationData f = new LocationData((int)(time-starttime), l.getLatitude(), l.getLongitude());
		locations.add(f);
		return f;
	}
	
	@JsonIgnore
	protected <T extends MediaData> T getMedia(long time, String id, String contentType, Class<T> clazz) {
		this.addInlineAttachment(new Attachment(id, "stub", contentType));
		try {
			return clazz.getConstructor(Integer.TYPE, String.class).newInstance((int)(time-starttime), id);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	@JsonIgnore
	public String addAudio(long time, String contentType, String ext) {
		String id = String.format("audio/%d.%s", audio.size()+1, ext);
		audio.add(getMedia(time, id, contentType, AudioData.class));
		return id;
	}
	
	@JsonIgnore
	public String addAudioPreview(String contentType, String ext) {
		String id = "audio/preview." + ext;
		audioPreview = getMedia(0, id, contentType, AudioData.class);
		return id;
	}
	
	@JsonIgnore
	public String addVoice(long time, String contentType, String ext) {
		String id = String.format("voice/%d.%s", voice.size()+1, ext);
		voice.add(getMedia(time, id, contentType, AudioData.class));
		return id;
	}	
	
	@JsonIgnore
	public String addPhoto(long time, String contentType, String ext) {
		String id = String.format("images/%d.%s", photos.size()+1, ext);
		photos.add(getMedia(time, id, contentType, ImageData.class));
		return id;
	}
	
	@JsonIgnore
	public void addNote(long time, String note) {	
		notes.add(new TextData((int)(time-starttime), note));
	}
	
	@JsonIgnore
	public void addHeartbeat(long time, int bpm) {	
		this.heartbeat.add(new HeartbeatData((int)(time-starttime), bpm));
	}
	
	@JsonIgnore
	public void setTags(String tags) {
		if (tags.trim().length() > 0) {
			this.tags = tags.split(",");
			for (int i=0; i<this.tags.length; i++) this.tags[i] = this.tags[i].trim();
		}
	}
	
	@JsonIgnore
	public LatLng getLocation(double time) {
		// TODO: fix 0-longitude issue
		if (locations.size()==0) return null;
		LocationData l1 = null, l2 = null;
		for (LocationData i : locations) {
			if (i.timestamp < time) {
				l1 = i;
			} else {
				l2 = i;
				break;
			}
		}
		if (l1==null || (l2!=null && l2.timestamp==time)) return l2.asLatLng();
		else if (l2==null) return l1.asLatLng();
		double t = (time-l1.timestamp)/(l2.timestamp-l1.timestamp);
		return new LatLng(
			l1.coordinates[0] + t*(l2.coordinates[0]-l1.coordinates[0]),
			l1.coordinates[1] + t*(l2.coordinates[1]-l1.coordinates[1])
		);
	}
	
	public static class TimedData {
		@JsonProperty("timestamp")
		public int timestamp;
		
		@JsonIgnore
		protected WeakReference<Story> storyRef;
		
		@JsonIgnore
		public void bind(Story story) {
			this.storyRef = new WeakReference<Story>(story);
		}
		
		@JsonIgnore
		public LatLng getLocation() {
			return storyRef.get().getLocation(timestamp);
		}
	}
	
	public static class LocationData {
		@JsonProperty("type")
		protected final String type = "Point";
		
		@JsonProperty("timestamp")
		public int timestamp;
		
		@JsonProperty("coordinates")
		public double[] coordinates;
		
		public LocationData() {}
		public LocationData(int time, double latitude, double longitude) {
			timestamp = time;
			coordinates = new double[2];
			coordinates[0] = latitude;
			coordinates[1] = longitude;
		}
		
		@JsonIgnore
		public LatLng asLatLng() {
			return new LatLng(coordinates[0], coordinates[1]);
		}
	}
	
	public static class MediaData extends TimedData {
		@JsonProperty("attachment_id")
		public String attachment_id;
		
		public MediaData() {}
		public MediaData(int time, String aid) {
			timestamp = time;
			attachment_id = aid;
		}
		
		@JsonIgnore
		protected File getCacheFile(Context context) {
			return new File(String.format("%s/%s-%s.bin", context.getExternalCacheDir().getAbsolutePath(), storyRef.get().getId(), attachment_id.replace("/", "_")));
		}
		@JsonIgnore
		protected boolean isCached(Context context) {
			return getCacheFile(context).exists();
		}
		@JsonIgnore
		protected boolean doCache(Context context) {
			File cache = getCacheFile(context);
			try {
				AttachmentInputStream input = storyRef.get().couchRef.get().getAttachment(storyRef.get().getId(), attachment_id);
				cache.setReadable(true, false);
				FileOutputStream output = new FileOutputStream(cache);
				IOUtils.copy(input, output);
				output.close();
				Log.v("Story", "Cached media " + storyRef.get().getId() + "/" + attachment_id);
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			return true;
		}
	}
	
	public static class AudioData extends MediaData {
		public AudioData() {}
		public AudioData(int time, String aid) {
			super(time, aid);
		}
		
		@JsonIgnore
		public Cacher<File> get() {
			return new Cacher<File>() {
				public boolean isCached(Context context) {
					return AudioData.this.isCached(context);
				}
				public synchronized boolean cache(Context context) {
					return AudioData.this.doCache(context);
				}
				public synchronized File get(Context context) {
					return AudioData.this.getCacheFile(context);
				}
			};
		}
	}
	
	public static class ImageData extends MediaData {
		public ImageData() {}
		public ImageData(int time, String aid) {
			super(time, aid);
		}
		
		@JsonIgnore
		public Cacher<Bitmap> get(final int maxSize) {
			return new Cacher<Bitmap>() {
				public boolean isCached(Context context) {
					return ImageData.this.isCached(context);
				}
				public synchronized boolean cache(Context context) {
					return ImageData.this.doCache(context);
				}
				public synchronized Bitmap get(Context context) {
					if (maxSize > 0) {
						return BitmapUtils.decodeFile(ImageData.this.getCacheFile(context), maxSize);
					}
					return BitmapFactory.decodeFile(ImageData.this.getCacheFile(context).getAbsolutePath());
				}
			};
		}
	}
	
	public static class TextData extends TimedData {
		@JsonProperty("text")
		public String text;
		
		public TextData() {}
		public TextData(int time, String text) {
			timestamp = time;
			this.text = text;
		}
	}
	
	public static class HeartbeatData extends TimedData {
		@JsonProperty("bpm")
		public int bpm;
		
		public HeartbeatData() {}
		public HeartbeatData(int time, int bpm) {
			timestamp = time;
			this.bpm = bpm;
		}
		
		public long[] getVibrationPattern(int times) {
			int p_wave = 80; // length of a p_wave - first stronger beat
			int t_wave = 100; // length of a t_wave - second weaker beat    
		    int short_interval = 150; // interval between the two beats
		    int beat_interval = 60 * 1000 / bpm; // calculate the interval in ms
		    int long_interval = Math.max(beat_interval-short_interval-p_wave-t_wave, 0);
		    long[] pattern = {
	    		// start right away
		        0,
		        // a strong beat
		        p_wave,
		        // pause
		        short_interval,
		        // a weak beat (t_wave/2 micro-beats)
		        1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		        // pause between pulses
		        long_interval
		    };
		    if (times < 2) return pattern;
	    	long[] repeated = new long[1 + times*(pattern.length-1)];
	    	repeated[0] = 0;
	    	for (int i=0; i<times; i++) {
	    		System.arraycopy(pattern, 1, repeated, i*(pattern.length-1)+1, pattern.length-1);
	    	}
	    	return repeated;
		}
	}
}
