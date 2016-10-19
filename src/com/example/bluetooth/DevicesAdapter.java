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
		add(bd, false);
	}

	public void add(BluetoothDevice bd, boolean connState) {
		if (bd == null && mConnDevices == null)
			return;

		if (mConnDevices.size() == 0) {
			mConnDevices.add(new BDevice(bd, connState));
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
			mConnDevices.add(new BDevice(bd, connState));
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

	public void refresh(BluetoothDevice bd, boolean connState) {
		if (bd == null)
			return;

		for (int i = 0; i < mConnDevices.size(); i++) {
			BDevice bDevice = mConnDevices.get(i);
			if (bDevice.mDevice.getAddress().equals(bd.getAddress())) {

				bDevice.mConnState = connState;
				mConnDevices.set(i, bDevice);
				notifyDataSetChanged();
				break;
			}
		}
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent) {
		if (convertView == null)
			convertView = LayoutInflater.from(mContext).inflate(R.layout.device_item, parent, false);

		BDevice bDevice = mConnDevices.get(position);

		TextView name = ViewHolder.get(convertView, R.id.tv_name);
		TextView address = ViewHolder.get(convertView, R.id.tv_address);
		TextView connState = ViewHolder.get(convertView, R.id.tv_connState);

		name.setText(bDevice.mDevice.getName());
		address.setText(bDevice.mDevice.getAddress());
		connState.setText("状态：" + (bDevice.mConnState ? "已连接" : "未连接"));

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
	public boolean mConnState;

	public BDevice(BluetoothDevice mDevice, boolean mConnState) {
		this.mDevice = mDevice;
		this.mConnState = mConnState;
	}

	@Override
	public String toString() {
		return "BDevice [mDevice.name=" + mDevice.getName() + ", address=" + mDevice.getAddress() + ", mConnState="
				+ mConnState + "]";
	}

}