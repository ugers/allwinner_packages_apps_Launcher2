package com.android.launcher2;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.R.bool;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.util.Log;

public class DynamicPowerPolicyChangeThread extends Thread {

	//Broadcast
	private final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
	
	// app config file name
	private final String APP_DETECT_CONFIG_FILE_NAME = "app_list.conf";

	// CPU
	private final String CMD_SET_CPU_TO_PERFORMANCE_POLICY = "echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";

	private final String CMD_SET_CPU_TO_FANTASYS_POLICY = "echo fantasys > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";

	private final String CMD_SET_CPU_TO_BENCHMARK_POLICY = "echo benchmark > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";

	private Context mContext;
	private ActivityManager mActivityManager;
	private String mFilterAppString;

	private boolean mIsNeedToSetWorkMode = false;
	private boolean mIsUsbConnected = false;

	public DynamicPowerPolicyChangeThread(Context context) {
		// TODO Auto-generated constructor stub
		mContext = context;
		mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

		try {
			mFilterAppString = readConfigFile(mContext.getAssets().open(APP_DETECT_CONFIG_FILE_NAME));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ACTION_USB_STATE);
		mContext.registerReceiver(mUsbStatusReceiver, intentFilter);

	}

	@Override
	protected void finalize() throws Throwable {
		// TODO Auto-generated method stub
		mContext.unregisterReceiver(mUsbStatusReceiver);
		super.finalize();
	}

	private BroadcastReceiver mUsbStatusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			if (intent.getAction().equals(ACTION_USB_STATE)) {
				
				ComponentName componentName = mActivityManager.getRunningTasks(1).get(0).topActivity;
				String packageName = componentName.getPackageName();
				boolean isInBenchmarkMode = mFilterAppString.indexOf(packageName) == -1 ? false : true;
				boolean isConnected = intent.getExtras().getBoolean("connected");
			
				if (isConnected) {
					//System.out.println("##usb is connected!!!");
					mIsUsbConnected = true;
					if (!isInBenchmarkMode) {
									
						//System.out.println("##usb is connected CMD_SET_CPU_TO_PERFORMANCE_POLICY!!!");
						setCPUWorkMode(CMD_SET_CPU_TO_PERFORMANCE_POLICY);
					} else {
						//System.out.println("##Run in blenmark mode, still in blenmark policy!");
					}

				} else {
					//System.out.println("##usb is disconnected!!!");
					mIsUsbConnected = false;

					if (!isInBenchmarkMode) {
						setCPUWorkMode(CMD_SET_CPU_TO_FANTASYS_POLICY);
					} else {
						//System.out.println("##Run in blenmark mode, still in performance policy!");
					}

				}

			}

		}
	};

	@Override
	public void run() {
		// TODO Auto-generated method stub
		super.run();

		while (true) {
			
			ComponentName componentName = mActivityManager.getRunningTasks(1).get(0).topActivity;
			String packageName = componentName.getPackageName();
			boolean isInBenchmarkMode = mFilterAppString.indexOf(packageName) == -1 ? false : true;

			if (isInBenchmarkMode && !mIsNeedToSetWorkMode) {

				setCPUWorkMode(CMD_SET_CPU_TO_BENCHMARK_POLICY);
				mIsNeedToSetWorkMode = true;
			//	System.out.println("set benchmark policy...........");

			} else if (!isInBenchmarkMode && mIsNeedToSetWorkMode) {

				mIsNeedToSetWorkMode = false;
				if (mIsUsbConnected) {
					setCPUWorkMode(CMD_SET_CPU_TO_PERFORMANCE_POLICY);
				}else {
					//System.out.println("##usb is connected CMD_SET_CPU_TO_FANTASYS_POLICY!!!");
					setCPUWorkMode(CMD_SET_CPU_TO_FANTASYS_POLICY);
				}
			//	System.out.println("resume cpu policy...........");
			}

			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	public void setCPUWorkMode(String cmd) {
		executeRootCmd(cmd);
	}

	public void executeRootCmd(String cmd) {
		DataOutputStream dos = null;
		DataInputStream dis = null;
		try {
			Process p = Runtime.getRuntime().exec("su");
			dos = new DataOutputStream(p.getOutputStream());
			cmd += "\n";
			dos.writeBytes(cmd);
			dos.flush();
			dos.writeBytes("exit\n");
			dos.flush();
			p.waitFor();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			try {
				if (dos != null)
					dos.close();
				if (dis != null)
					dis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public String executeShellCmd(String cmd) {
		Process process = null;
		BufferedReader br = null;
		String s = null;
		String resultStr = "";
		try {
			process = Runtime.getRuntime().exec(
					new String[] { "/system/bin/sh", "-c", cmd });
			br = new BufferedReader(new InputStreamReader(
					process.getInputStream()));
			while ((s = br.readLine()) != null) {
				if (s != null) {
					resultStr += s;
				}
			}
			// //System.out.println(resultStr);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (process != null) {
				process.destroy();
			}
			process = null;
			if (br != null) {
				try {
					br.close();
				} catch (Exception e) {
				}
				br = null;
			}
		}

		return resultStr;
	}

	public String readConfigFile(InputStream inputStream) {

		byte[] buffer = null;

		try {
			buffer = new byte[inputStream.available()];
			inputStream.read(buffer);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}

		return new String(buffer);

	}

}
