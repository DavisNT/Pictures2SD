package lv.id.dm.pictures2sd;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedModule implements IXposedHookLoadPackage {
	private static final String[] patchablePackages = new String[] {"com.android.gallery3d", "com.google.android.gallery3d", "com.android.camera2", "com.google.android.GoogleCamera", "org.cyanogenmod.focal", "fr.xplod.focal", "com.niksoftware.snapseed", "com.instagram.android", "com.moblynx.camerakk", "com.pixtogram.wear.zicam"};
	private static final String LogTag = "Pictures2SD";
	private static Context appContext = null;
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if(!Arrays.asList(patchablePackages).contains(lpparam.packageName)) {
			return;
		}
		Log.i(LogTag, String.format("Loaded camera package %s", lpparam.packageName));

		XposedHelpers.findAndHookMethod("android.os.Environment", lpparam.classLoader, "getExternalStorageDirectory", new ExternalStorageDirectoryHook());
		XposedHelpers.findAndHookMethod("android.os.Environment", lpparam.classLoader, "getExternalStoragePublicDirectory", String.class, new ExternalStorageDirectoryHook());		

		XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onCreate", Bundle.class, new ContextCatcher());		
		XposedHelpers.findAndHookMethod("android.app.Service", lpparam.classLoader, "onCreate", new ContextCatcher());		
	}

	class ContextCatcher extends XC_MethodHook {
		@Override
		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
			if(XposedModule.appContext == null) {
				XposedModule.appContext = ((Context)param.thisObject).getApplicationContext();
			}
		}		
	}

	static class ExternalStorageDirectoryHook extends XC_MethodHook {
		private static StorageManager storageManager = null; 
		private static Method getVolumeList = null;
		private static Method getVolumeState = null;
		private static Class<?> storageVolumeClass = null;
		private static Method getStorageId = null;
		private static Method getPath = null;
		private static Method isPrimary = null;

		@Override
		protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
			String extSD = System.getenv("SECONDARY_STORAGE");
			File fileExtSD;
	
			if(extSD==null || "".equals(extSD)) {
				// Android 6+ should go here
				Log.i(LogTag, "Environment variable SECONDARY_STORAGE not set, attempting dynamic detection");
				if(appContext == null) {
					Log.e(LogTag, "Secondary storage not found (dynamic detection failed - no Context)");
					return;
				}
				extSD = getFirstExternalStorage();
			} else {
				extSD = extSD.split(":", 2)[0];
			}

			if(extSD==null || "".equals(extSD)) {
				Log.e(LogTag, "Secondary storage not found (dynamic detection failed)");
				return;
			}

			if(param.args.length == 0){
				fileExtSD = new File(extSD);
				Log.i(LogTag, String.format("getExternalStorageDirectory() will return %s", fileExtSD.toString()));
			} else {
				fileExtSD = new File(new File(extSD), (String)param.args[0]);
				Log.i(LogTag, String.format("getExternalStoragePublicDirectory(\"%s\") will return %s", param.args[0], fileExtSD.toString()));
			}
			param.setResult(fileExtSD);
		}
		
		private String getFirstExternalStorage() {
			try{
				if(storageManager == null) {
					storageManager = (StorageManager)appContext.getSystemService(Context.STORAGE_SERVICE);

					getVolumeList = storageManager.getClass().getMethod("getVolumeList");
					getVolumeState = storageManager.getClass().getMethod("getVolumeState", String.class);

					storageVolumeClass = Class.forName("android.os.storage.StorageVolume");
					getPath = storageVolumeClass.getMethod("getPath");
					getStorageId = storageVolumeClass.getMethod("getStorageId");
					isPrimary = storageVolumeClass.getMethod("isPrimary");
				}

				Object volumeList = getVolumeList.invoke(storageManager);
				int vlLength = Array.getLength(volumeList);
				
				int volumeId = Integer.MAX_VALUE;
				String volumePath = null;

				for (int i = 0; i < vlLength; i++) {
					Object storageVolume = Array.get(volumeList, i);
					int id = (Integer)getStorageId.invoke(storageVolume);
					boolean primary = (Boolean)isPrimary.invoke(storageVolume);
					if(id < volumeId && !primary) {
						String path = (String)getPath.invoke(storageVolume);
						String state = (String)getVolumeState.invoke(storageManager, path);
						if(Environment.MEDIA_MOUNTED.equals(state)) {
							volumeId = id;
							volumePath = path;
						}
					}
				}
								
				return volumePath;
			}
			catch (Exception e) {
				return null;
			}
		}
	}
}
