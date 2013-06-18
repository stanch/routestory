package net.routestory.parts;

import android.content.Context;

public interface Cacher<T> {
	public boolean isCached(Context context);
	public boolean cache(Context context);
	public T get(Context context);
}
