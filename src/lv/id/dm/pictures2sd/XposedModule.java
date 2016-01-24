package lv.id.dm.pictures2sd;

import java.io.File;
import java.util.Arrays;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedModule implements IXposedHookLoadPackage {
	private static final String[] patchablePackages = new String[] {"com.android.gallery3d", "org.cyanogenmod.focal"};
	private static final String LogTag = "Pictures2SD";
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if(!Arrays.asList(patchablePackages).contains(lpparam.packageName)) {
			return;
		}
		Log.i(LogTag, String.format("Loaded camera package %s", lpparam.packageName));

		XposedHelpers.findAndHookMethod("android.os.Environment", lpparam.classLoader, "getExternalStorageDirectory", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				String extSD = System.getenv("SECONDARY_STORAGE");
				if(extSD==null || "".equals(extSD)) {
					Log.e(LogTag, "Secondary storage not found (environment variable SECONDARY_STORAGE not set)");
					return;
				}
				File fileExtSD = new File(extSD);
				Log.d(LogTag, String.format("getExternalStorageDirectory() will return %s", fileExtSD.toString()));
				param.setResult(fileExtSD);
			}
		});

		XposedHelpers.findAndHookMethod("android.os.Environment", lpparam.classLoader, "getExternalStoragePublicDirectory", String.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				String extSD = System.getenv("SECONDARY_STORAGE");
				if(extSD==null || "".equals(extSD)) {
					Log.e(LogTag, "Secondary storage not found (environment variable SECONDARY_STORAGE not set)");
					return;
				}
				File fileExtSD = new File(new File(extSD), (String)param.args[0]);
				Log.d(LogTag, String.format("getExternalStoragePublicDirectory(\"%s\") will return %s", param.args[0], fileExtSD.toString()));
				param.setResult(fileExtSD);
			}
		});

		
/*		if(XposedHelpers.findClass("com.android.camera.Storage", lpparam.classLoader)!=null) {
			XposedBridge.hookAllConstructors()
			XposedBridge.log("Camera package: "+lpparam.packageName);
		}
		if(!"com.android.mms".equals(lpparam.packageName))
            return;*/
		// TODO Auto-generated method stub
		
	}
}
