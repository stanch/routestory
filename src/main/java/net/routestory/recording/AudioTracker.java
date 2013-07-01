package net.routestory.recording;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.todoroo.aacenc.AACEncoder;

public class AudioTracker implements Runnable {
	private Context context;
	private AudioRecord audioRecord;
	private short[] buffer;
	private int status;
	
	private static final int frameSize = 441; // 10ms
	private static final int bufferFrames = 3;
	private static final int bufferSize = frameSize * bufferFrames;
	private static final int currentFrameOffset = frameSize * (bufferFrames / 2);
	
	private static final int fadeLength = (int)(44100 * 1.5);
	private static final int previewLength = 44100 * 30;
	
	private int dumpFrames = 1000; // 10s
	private int restFrames = 5000; // 50s
	private FileOutputStream dumpStream = null;
	private byte[] dumpBuffer;
	private TimedFile dumpInfo;
	
	public static final class TimedFile {
		String filename;
		long timestamp;
		TimedFile(String f, long t) {
			filename = f;
			timestamp = t;
		}
	}
	private List<TimedFile> dumps = null;
	public volatile List<TimedFile> output = null;
	
	private volatile boolean paused;
	
	AudioTracker (Context context) {
		this.context = context;
	}
	
	/* this thread periodically records audio pieces and writes them to the card */
	public void run() {
		audioRecord = new AudioRecord(
			MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
			AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
		);
		audioRecord.startRecording();
		
		/* Audio is read in frames of frameSize bytes. bufferFrames frames are accumulated in the buffer.
		 * The current frame at the center of the buffer. Depending on preceding and following frames, it
		 * is decided whether dumping to a file should be started for the current frame.
		 * Dumping lasts for dumpFrames frames and is followed by a period of restFrames frames, where
		 * dumping can’t be turned on.
		 */
		buffer = new short[bufferSize];
		dumpBuffer = new byte[frameSize * 2];
		
		status = -100; // create a small initial rest
		while (true) {
			readFrame();
			if (status < 0) {
				/* resting */
				status++;
			} else if (status > 0) {
				/* dumping */
				status--;
				dumpCurrentFrame();
				if (status==0) {
					closeDump();
					// make the dumps more sparse
					if (dumps.size() % 5 == 0) {
						restFrames *= 2;
					}
					status = -restFrames; // switch to resting
				}
			} else {
				/* start dumping again */
				openDump();
				status = dumpFrames;
			}
			
			if (paused) {
				Log.v("AudioTracker", "Paused");
				cleanUp();
				while (paused); // don't forget to unpause!
				Log.v("AudioTracker", "Recording");
				status = -50;
				audioRecord = new AudioRecord(
					MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
					AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
				);
				audioRecord.startRecording();
			}
			
			if (Thread.interrupted()) {
				Log.v("AudioTracker", "Finished recording, processing data");
				break;
			}
		}
		cleanUp();
		processAudio();
		context = null; // facilitate GC
	}
	
	public void pause() {
		paused = true;
	}
	public void unpause() {
		paused = false;
	}
	public boolean isPaused() {
		return paused;
	}
	
	private void readFrame() {
		System.arraycopy(buffer, frameSize, buffer, 0, bufferSize-frameSize); // shift one frame left
		for (int offset=bufferSize-frameSize; offset<bufferSize;) {
			offset += audioRecord.read(buffer, offset, bufferSize-offset);
		}
	}
	
	private void openDump() {
		File temp = null;
		try {
			temp = File.createTempFile("audio-sample", ".snd", context.getExternalCacheDir());
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (dumps == null) {
			dumps = new LinkedList<TimedFile>();
		}
		dumpInfo = new TimedFile(temp.getAbsolutePath(), System.currentTimeMillis()/1000L);
		try {
			dumpStream = new FileOutputStream(temp);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	private void dumpCurrentFrame() {
		try {
			ByteBuffer.wrap(dumpBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buffer, currentFrameOffset, frameSize);
			dumpStream.write(dumpBuffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void closeDump() {
		try {
			dumpStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		dumps.add(dumpInfo);
	}
	
	private void cleanUp() {
		audioRecord.stop();
		audioRecord.release();
		if (status > 0) {
			try {
				dumpStream.close();
				new File(dumpInfo.filename).delete(); // we don�t need the chopped file
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void processAudio() {
		output = new LinkedList<TimedFile>();
		if (dumps == null) return;
		
		/* remove stuff that we don't need, as in
		 * ....  .  .  .  .  .    .    .    .    .    .        .        .
		 *  xxx     x  x     x    x         x         x
		 */
		LinkedList<TimedFile> temp = new LinkedList<TimedFile>();
		Long time = null;
		for (TimedFile f : dumps) {
			if (time == null || f.timestamp-time > restFrames/8000*3) {
				time = f.timestamp;
				temp.add(f);
			} else {
				new File(f.filename).delete();
			}
		}
		dumps = temp;
		if (dumps.size() == 0) return;
		
		AACEncoder encoder = new AACEncoder();
		short[] preview = new short[previewLength];
		byte[] previewBytes = new byte[previewLength * 2];
		int index = 0;
		int offset = dumps.size() > 1 ? (previewLength-dumpFrames*frameSize) / (dumps.size()-1) : 0;
		
    	for (TimedFile piece : dumps) {
    		/* read piece */
    		File pcmFile = new File(piece.filename);
    		byte[] data = null;
			try {
				data = FileUtils.readFileToByteArray(pcmFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
			pcmFile.delete();
			
			/* create fade-ins and fade-outs */
			short[] faded = new short[data.length/2];
			ShortBuffer shortBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(faded);
			for (int i=0; i<fadeLength; i++) {
				double factor = Math.pow((double)i/fadeLength, 2); // sounds nicer than linear
				faded[i] *= factor;
				faded[faded.length-1-i] *= factor;
			}
			shortBuffer.clear();
			shortBuffer.put(faded);
			
			/* add to preview */
			for (int i=0, j=index; i<faded.length && j<previewLength; i++, j++) {
				preview[j] = (short)(faded[i] + preview[j] - (((long)faded[i]*preview[j])>>>16)); // see http://www.vttoth.com/CMS/index.php/technical-notes/68
			}
			index += offset;
			
			/* encode to aac */
			File aacFile = new File(piece.filename + ".aac");
    		encoder.init(64000, 1, 44100, 16, aacFile.getAbsolutePath());
            encoder.encode(data);
            encoder.uninit();
            output.add(new TimedFile(aacFile.getAbsolutePath(), piece.timestamp));
            new File(piece.filename).delete();
    	}
    	
    	/* encode preview */
    	File previewAacFile = null;
		try {
			previewAacFile = File.createTempFile("preview", ".aac", context.getExternalCacheDir());
		} catch (IOException e) {
			e.printStackTrace();
		}
		ByteBuffer.wrap(previewBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(preview);
		encoder.init(64000, 1, 44100, 16, previewAacFile.getAbsolutePath());
        encoder.encode(previewBytes);
        encoder.uninit();
        output.add(new TimedFile(previewAacFile.getAbsolutePath(), 0));
	}
}
