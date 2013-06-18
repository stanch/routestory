package net.routestory.parts;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;

public class TabListener<T extends Fragment> implements ActionBar.TabListener {
    private Fragment mFragment;
    private final SherlockFragmentActivity mActivity;
    private final String mTag;
    private final Class<T> mClass;

    public TabListener(SherlockFragmentActivity activity, String tag, Class<T> clz) {
        mActivity = activity;
        mTag = tag;
        mClass = clz;
    }

    public void onTabSelected(Tab tab, FragmentTransaction ft) {
    	Fragment preInitializedFragment = (Fragment)mActivity.getSupportFragmentManager().findFragmentByTag(mTag);
	    if (mFragment == null && preInitializedFragment == null) {
	        mFragment = Fragment.instantiate(mActivity, mClass.getName());
	        ft.add(android.R.id.content, mFragment, mTag);
	    } else if (mFragment != null) {
	        ft.attach(mFragment);
	    } else if (preInitializedFragment != null) {
	        ft.attach(preInitializedFragment);
	        mFragment = preInitializedFragment;
	    }
    }

    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        if (mFragment != null) {
            ft.detach(mFragment);
        }
    }

    public void onTabReselected(Tab tab, FragmentTransaction ft) {}
}
