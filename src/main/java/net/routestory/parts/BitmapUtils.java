package net.routestory.parts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.List;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Region.Op;

public class BitmapUtils {
	public static Bitmap createScaledTransparentBitmap(Bitmap bitmap, int size, double alpha, boolean border) {
		boolean landscape = bitmap.getWidth() > bitmap.getHeight();
		int width = landscape ? size : size * bitmap.getWidth() / bitmap.getHeight();
		int height = landscape ? size * bitmap.getHeight() / bitmap.getWidth() : size;
		if (width <= 0) width = 1;
		if (height <= 0) height = 1;
		
		int b = border ? 3 : 0;
		Bitmap target = Bitmap.createBitmap(width+2*b, height+2*b, Config.ARGB_8888);
		Canvas canvas = new Canvas(target);
		canvas.save();
		canvas.clipRect(new Rect(b, b, width+b, height+b), Op.XOR);
		//canvas.drawARGB(0xfa, 0x67, 0x3d, 200);
		canvas.restore();
		Paint paint = new Paint();
		paint.setAlpha((int)(alpha*255));
		canvas.drawBitmap(bitmap, null, new Rect(b, b, width+b, height+b), paint);
		
		return target;
	}
	
	// see [http://stackoverflow.com/questions/477572/android-strange-out-of-memory-issue-while-loading-an-image-to-a-bitmap-object]
	public static Bitmap decodeFile(File f, int size) {
	    try {
	        // decode image size
	        BitmapFactory.Options o = new BitmapFactory.Options();
	        o.inJustDecodeBounds = true;
	        BitmapFactory.decodeStream(new FileInputStream(f), null, o);

	        // find scale factor
	        int scale = 1;
	        while (Math.max(o.outWidth, o.outHeight)/scale/2 >= size) scale*=2;

	        // decode with inSampleSize
	        BitmapFactory.Options o2 = new BitmapFactory.Options();
	        o2.inSampleSize = scale;
	        Bitmap temp = BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
	        
	        // scale to requested size
	        Bitmap scaled = createScaledBitmap(temp, size);
	        temp.recycle();
	        return scaled;
	    } catch (FileNotFoundException e) {}
	    return null;
	}
	
	public static Bitmap createScaledBitmap(Bitmap bitmap, int size) {
		boolean landscape = bitmap.getWidth() > bitmap.getHeight();
		int width = landscape ? size : size * bitmap.getWidth() / bitmap.getHeight();
		int height = landscape ? size * bitmap.getHeight() / bitmap.getWidth() : size;
		if (width <= 0) width = 1;
		if (height <= 0) height = 1;
		return Bitmap.createScaledBitmap(bitmap, width, height, true);
	}
	
	public static Bitmap createCountedBitmap(Bitmap bitmap, Integer count) {
		Bitmap target = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
		Canvas canvas = new Canvas(target);
		Paint paint = new Paint();
		paint.setColor(Color.WHITE);
		paint.setTextSize(bitmap.getHeight()/2);
		paint.setTextAlign(Align.RIGHT);
		canvas.drawBitmap(bitmap, 0, 0, null);
		canvas.drawText(count.toString(), bitmap.getWidth(), bitmap.getHeight()-3, paint);
		return target;
	}
	
	public static class MagicGrid {
		public final static int spacing = 2;
		public static class OversizedException extends Exception {
			private static final long serialVersionUID = 1L;
			public double r;
			public OversizedException(double r) { this.r = r; }
		}
		
		public static Bitmap createSquarishGrid(List<Bitmap> bitmaps, int size) {
			int d = (int)Math.sqrt(bitmaps.size());
			Col root = new Col();
			for (int i=0; i<d; i++) {
				Row row = new Row();
				for (int j=0; j<(i==d-1 ? d+bitmaps.size()-d*d : d); j++) {
					row.add(new Cell(bitmaps.get(i*d+j)));
				}
				root.add(row);
			}
			int width = (int)root.getWidth(size), height;
			if (width > size) {
				width = size;
				height = (int)root.getHeight(width);
			} else {
				height = size;
			}
			while(true) {
				Bitmap target = Bitmap.createBitmap(width, height, Config.ARGB_8888);
				Canvas canvas = new Canvas(target);
				//canvas.drawARGB(255, 255, 255, 255);
				try {
					root.draw(canvas, 0, 0, width, null);
					return target;
				} catch (OversizedException e) {
					width /= e.r;
					height /= e.r;
				}
			}
		}
		
		public static abstract class RectObject {
			protected double[] widthToHeightFunction;
			protected double[] heightToWidthFunction;
			public double[] inverse(double[] r) {
				return new double[] {1.0/r[0], -r[1]/r[0]};
			}
			public double getWidth(double height) {
				double[] r = getRatioFunction(false);
				return height*r[0] + r[1];
			}
			public double getHeight(double width) {
				double[] r = getRatioFunction(true);
				return width*r[0] + r[1];
			}
			public double[] getRatioFunction(boolean widthToHeight) {
				if (widthToHeightFunction == null) {
					updateRatioFunctions();
				}
				return widthToHeight ? widthToHeightFunction : heightToWidthFunction;
			}
			protected abstract void updateRatioFunctions();
			public abstract int draw(Canvas canvas, int x, int y, Integer width, Integer height) throws OversizedException;
		}
		public static class Cell extends RectObject {
			private Bitmap bitmap;
			public Cell(Bitmap bitmap) {
				this.bitmap = bitmap;
			}
			protected void updateRatioFunctions() {
				widthToHeightFunction = new double[] {(double)bitmap.getHeight()/bitmap.getWidth(), 0};
				heightToWidthFunction = new double[] {(double)bitmap.getWidth()/bitmap.getHeight(), 0};
			}
			public int draw(Canvas canvas, int x, int y, Integer width, Integer height) throws OversizedException {
				int ret;
				if (width==null) {
					width = (int)getWidth(height);
					ret = width;
				} else {
					height = (int)getHeight(width);
					ret = height;
				}
				int size = Math.max(width, height);
				int maxSize = Math.max(bitmap.getWidth(), bitmap.getHeight());
				if (maxSize + 5 < size) {
					throw new OversizedException((double)size / maxSize);
				}
				canvas.drawBitmap(bitmap, null, new Rect(x, y, x+width, y+height), null);
				return ret;
			}
		}
		public static class Line extends RectObject {
			private List<RectObject> children;
			private boolean horizontal;
			public Line(boolean horizontal) {
				this.children = new LinkedList<RectObject>();
				this.horizontal = horizontal;
			}
			public Line(List<RectObject> children, boolean horizontal) {
				this.children = children;
				this.horizontal = horizontal;
			}
			public void add(RectObject child) {
				children.add(child);
				updateRatioFunctions();
			}
			protected void updateRatioFunctions() {
				double[] r = new double[] {0, spacing*(children.size()-1)};
				for (RectObject child : children) {
					double[] r2 = child.getRatioFunction(!horizontal);
					r[0] += r2[0];
					r[1] += r2[1];
				};
				if (horizontal) {
					heightToWidthFunction = r;
					widthToHeightFunction = inverse(r);
				} else {
					widthToHeightFunction = r;
					heightToWidthFunction = inverse(r);
				}
			}
			public int draw(Canvas canvas, int x, int y, Integer width, Integer height) throws OversizedException {
				int ret;
				if (width==null) {
					width = (int)getWidth(height);
					ret = width;
				} else {
					height = (int)getHeight(width);
					ret = height;
				}
				int d = 0; 
				for (RectObject child : children) {
					d += child.draw(canvas, horizontal ? x+d : x, horizontal ? y : y+d, horizontal ? null : width, horizontal ? height : null) + spacing;
				}
				return ret;
			}
		}
		public static class Row extends Line {
			public Row() { super(true); }
			public Row(List<RectObject> children) { super(children, true); }
		}
		public static class Col extends Line {
			public Col() { super(false); }
			public Col(List<RectObject> children) { super(children, false); }
		}
	}
}
