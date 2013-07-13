package net.routestory.parts;

import net.routestory.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.provider.Settings;

public class GotoDialogFragments {
	public static boolean ensureNetwork(Activity activity) {
	    ConnectivityManager manager = (ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo info = manager.getActiveNetworkInfo();
	    if (info == null) {
	    	new NetworkSettings().show(activity.getFragmentManager(), "net_dialog");
	    	return false;
	    }
	    return true;
	}
	
	public static class NetworkSettings extends DialogFragment {
		@Override
	    public Dialog onCreateDialog(Bundle savedInstanceState) {
	        return new AlertDialog.Builder(getActivity())
	        	.setMessage(R.string.message_neednet)
	    		.setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
	    			public void onClick(DialogInterface dialog, int id) {
	    				Intent intent = new Intent(Settings.ACTION_SETTINGS);
	    				startActivityForResult(intent, 0);
	    			}
	    		})
	    		.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
			      	public void onClick(DialogInterface dialog, int id) {
			      		getActivity().finish();
			      	}
	    		})
	        .create();
	    }
	}
	
	public static boolean ensureGPS(Activity activity) {
    	String provider = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
   		if (!provider.contains("gps")) {
   			new GpsSettings().show(activity.getFragmentManager(), "gps_dialog");
   			return false;
   		}
   		return true;
    }
	
	public static class GpsSettings extends DialogFragment {
		@Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
            	.setMessage(R.string.message_needgps)
            	.setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
            		public void onClick(DialogInterface dialog, int id) {
            			Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            			startActivityForResult(intent, 0);
            		}
            	})
            	.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
            		public void onClick(DialogInterface dialog, int id) {
            			getActivity().finish();
            		}
            	})
            .create();
        }
	}
}
