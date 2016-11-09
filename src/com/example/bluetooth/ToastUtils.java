package com.example.bluetooth;

import android.content.Context;
import android.os.Handler;
import android.view.Gravity;
import android.widget.Toast;

/**
 * Toast工具类
 */
public class ToastUtils {

	/**
	 * 显示时长（短）
	 */
	public static final int LENGTH_SHORT = Toast.LENGTH_SHORT;

	/**
	 * 显示时长（长）
	 */
	public static final int LENGTH_LONG = Toast.LENGTH_LONG;

	/**
	 * 显示位置（居中）
	 */
	public static final int CENTER = 2;

	/**
	 * 显示位置（上方）
	 */
	public static final int TOP_CENTER = 3;

	/**
	 * 显示位置（下方）
	 */
	public static final int BOTTOM_CENTER = 4;

	/**
	 * 系统Toast
	 */
	private static Toast mToast;

	private static Handler mHandler = new Handler();

	/**
	 * 用于取消Toast
	 */
	private static Runnable r = new Runnable() {
		public void run() {
			if (mToast != null)
				mToast.cancel();
		}
	};

	/**
	 * 显示一个Toast
	 * @param mContext
	 * @param text
	 * @param duration
	 */
	public static void showToast(Context mContext, String text) {
		showToast(mContext, text, LENGTH_SHORT);
	}

	/**
	 * 显示一个Toast
	 * @param mContext
	 * @param text
	 * @param duration
	 */
	public static void showToast(Context mContext, String text, int duration) {

		mHandler.removeCallbacks(r);
		if (mToast != null)
			mToast.setText(text);
		else
			mToast = Toast.makeText(mContext, text, duration);
		mHandler.postDelayed(r, 2000);

		mToast.show();
	}

	/**
	 * 显示一个Toast
	 * @param mContext
	 * @param resId
	 * @param duration
	 */
	public static void showToast(Context mContext, int resId, int duration) {
		showToast(mContext, mContext.getResources().getString(resId), duration);
	}

	/**
	 * 显示一个Toast
	 * @param mContext
	 * @param text
	 * @param duration
	 * @param grivity
	 */
	public static void showToast(Context mContext, String text, int duration, int grivity) {

		mHandler.removeCallbacks(r);
		if (mToast != null)
			mToast.setText(text);
		else
			mToast = Toast.makeText(mContext, text, duration);
		mHandler.postDelayed(r, 2000);

		if (grivity == TOP_CENTER) {
			mToast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 20);
		} else if (grivity == CENTER) {
			mToast.setGravity(Gravity.CENTER, 0, 0);
		} else {
			mToast.setGravity(Gravity.BOTTOM | Gravity.CENTER, 0, 20);
		}

		mToast.show();
	}

	/**
	 * 显示一个Toast
	 * @param mContext
	 * @param resId
	 * @param duration
	 * @param grivity
	 */
	public static void showToast(Context mContext, int resId, int duration, int grivity) {
		showToast(mContext, mContext.getResources().getString(resId), duration, grivity);
	}
}
