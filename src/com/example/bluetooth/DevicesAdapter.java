package com.example.bluetooth;

import java.util.ArrayList;
import java.util.List;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class DevicesAdapter extends BaseAdapter {
	private Context mContext;
	private List<BDevice> mConnDevices;

	public DevicesAdapter(Context context) {
		mContext = context;
		mConnDevices = new ArrayList<>();
	}

	@Override
	public int getCount() {
		return mConnDevices.size();
	}

	@Override
	public BDevice getItem(int position) {
		return mConnDevices.get(position);
	}

	@Override
	public long getItemId(int position) {
		return 0;
	}

	public void add(BluetoothDevice bd) {
		add(bd, false, 0);
	}

	public void add(BluetoothDevice bd, int rssi) {
		add(bd, false, rssi);
	}

	public void add(BluetoothDevice bd, boolean connState) {
		add(bd, connState, 0);
	}

	private void add(BluetoothDevice bd, boolean connState, int rssi) {
		if (bd == null && mConnDevices == null)
			return;

		double dis = calcDistByRSSI(rssi);

		if (mConnDevices.size() == 0) {
			mConnDevices.add(new BDevice(bd, connState, rssi, dis));
			notifyDataSetChanged();
			return;
		}

		boolean isExist = false;
		// 地址重复的设备不添加
		for (BDevice bDevice : mConnDevices)
			if (bDevice.mDevice.getAddress().equals(bd.getAddress()))
				isExist = true;

		if (isExist)
			return;
		else
			mConnDevices.add(new BDevice(bd, connState, rssi, dis));
		notifyDataSetChanged();
	}

	public void remove(BluetoothDevice bd) {
		if (bd == null && mConnDevices == null)
			return;

		for (BDevice bDevice : mConnDevices) {
			if (bDevice.mDevice.getAddress().equals(bd.getAddress())) {
				mConnDevices.remove(bDevice);
				notifyDataSetChanged();
				break;
			}
		}
	}

	public void removeAll() {
		if (mConnDevices == null)
			return;
		mConnDevices.clear();
		notifyDataSetChanged();
	}

	/**
	 * 刷新连接状态
	 */
	public void refreshConnState(BluetoothDevice bd, boolean connState) {
		if (bd == null)
			return;

		for (int i = 0; i < mConnDevices.size(); i++) {
			BDevice bDevice = mConnDevices.get(i);
			if (bDevice.mDevice.getAddress().equals(bd.getAddress())) {
				bDevice.connState = connState;
				mConnDevices.set(i, bDevice);
				notifyDataSetChanged();
				break;
			}
		}
	}

	/**
	 * 刷新信号强度
	 */
	public void refreshRssi(String address, int rssi) {
		double dis = calcDistByRSSI(rssi);

		for (int i = 0; i < mConnDevices.size(); i++) {
			BDevice bDevice = mConnDevices.get(i);
			if (bDevice.mDevice.getAddress().equals(address)) {
				bDevice.rssi = rssi;
				bDevice.dis = dis;

				mConnDevices.set(i, bDevice);
				notifyDataSetChanged();
				break;
			}
		}
	}

	private double calcDistByRSSI(int rssi) {
		/*计算公式：
			d = 10^((abs(RSSI) - A) / (10 * n))
		其中：
			d - 计算所得距离
			RSSI - 接收信号强度（负值）
			A - 发射端和接收端相隔1米时的信号强度
			n - 环境衰减因子*/

		if (rssi == 0)
			return 0;

		int iRssi = Math.abs(rssi);
		float power = (float) ((iRssi - 59) / (10 * 2.0));
		double pow = Math.pow(10, power);

		return (int) (pow * 100) / (double) 100;// 取小数点后2位
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		if (convertView == null)
			convertView = LayoutInflater.from(mContext).inflate(R.layout.device_item, parent, false);

		BDevice bDevice = mConnDevices.get(position);

		TextView name = ViewHolder.get(convertView, R.id.tv_name);
		TextView address = ViewHolder.get(convertView, R.id.tv_address);
		TextView connState = ViewHolder.get(convertView, R.id.tv_connState);
		TextView rssi = ViewHolder.get(convertView, R.id.tv_rssi);
		TextView dis = ViewHolder.get(convertView, R.id.tv_dis);

		String deviceName = bDevice.mDevice.getName();
		if (deviceName == null || deviceName == "")
			deviceName = "Unknow";

		name.setText(deviceName);
		address.setText(bDevice.mDevice.getAddress());
		connState.setText("状态：" + (bDevice.connState ? "已连接" : "未连接"));
		rssi.setText("Rssi：" + bDevice.rssi);
		dis.setText("Dis：" + bDevice.dis + "m");

		return convertView;
	}
}

class ViewHolder {
	// I added a generic return type to reduce the casting noise in client code
	@SuppressWarnings("unchecked")
	public static <T extends View> T get(View view, int id) {
		SparseArray<View> viewHolder = (SparseArray<View>) view.getTag();
		if (viewHolder == null) {
			viewHolder = new SparseArray<View>();
			view.setTag(viewHolder);
		}

		View childView = viewHolder.get(id);

		if (childView == null) {
			childView = view.findViewById(id);
			viewHolder.put(id, childView);
		}
		return (T) childView;
	}
}

class BDevice {
	public BluetoothDevice mDevice;
	/** 连接状态		true:已连接*/
	public boolean connState;
	/** 信号强度 */
	public int rssi;
	/** 距离 */
	public double dis;

	@Deprecated
	public BDevice(BluetoothDevice mDevice, boolean mConnState) {
		this.mDevice = mDevice;
		this.connState = mConnState;
	}

	public BDevice(BluetoothDevice mDevice, int rssi) {
		this.mDevice = mDevice;
		this.rssi = rssi;
	}

	public BDevice(BluetoothDevice mDevice, boolean connState, int rssi, double dis) {
		this.mDevice = mDevice;
		this.connState = connState;
		this.rssi = rssi;
		this.dis = dis;
	}

	@Override
	public String toString() {
		return "BDevice [mDevice=" + mDevice + ", connState=" + connState + ", rssi=" + rssi + ", dis=" + dis + "]";
	}

}