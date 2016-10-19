package com.example.bluetooth;

import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * 
 *
 * @author likairui
 * 
 * 参考：http://ju.outofmemory.cn/entry/217374
 *
 * @date 2016年10月18日 下午5:13:15
 */
@SuppressLint("NewApi")
public class CentralActivity extends Activity {

	private final String TAG = Constants.TAG;
	private static final int WHAT_STOP_SCAN = 1;
	private static final int WHAT_STATE_CONNECTED = 2;
	private static final int WHAT_STATE_DISCONNECTED = 3;
	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGatt mBluetoothGatt;

	private DevicesAdapter mDevicesAdapter;

	private ListView mListView;
	private ProgressBar progressBar;

	final int STOP_SCAN_TIME = 10 * 1000;

	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case WHAT_STOP_SCAN:
					stopScan();
					break;
				case WHAT_STATE_CONNECTED:
					BluetoothDevice device = (BluetoothDevice) msg.obj;
					mDevicesAdapter.removeAll();
					mDevicesAdapter.add(device, true);
					break;
				case WHAT_STATE_DISCONNECTED:
					BluetoothDevice disDevice = (BluetoothDevice) msg.obj;
					mDevicesAdapter.refresh(disDevice, false);
					break;
				default:
					break;
			}
		};
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_central);

		mListView = (ListView) findViewById(R.id.lv_devicelist);
		progressBar = (ProgressBar) findViewById(R.id.pb);
		mDevicesAdapter = new DevicesAdapter(getApplicationContext());
		mListView.setAdapter(mDevicesAdapter);

		// 蓝牙技术在Android 4.3+ 是通过BluetoothManager访问，而不是旧的静态 BluetoothAdapter.getInstance()
		mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();

		startScanning();

		mListView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				stopScan();
				BDevice item = mDevicesAdapter.getItem(position);
				BluetoothDevice cd = item.mDevice;
				if (item.mConnState)
					mBluetoothGatt.disconnect();
				else
					cd.connectGatt(getApplicationContext(), false, mGattCallback);
			}
		});

		mBluetoothAdapter.setName("中央");
	}

	public void scanClick(View v) {
		stopScan();
		startScanning();
	}

	private void startScanning() {
		Log.d(TAG, "开始扫描...");
		stopScan();
		progressBar.setVisibility(View.VISIBLE);
		mDevicesAdapter.removeAll();
		mHandler.sendEmptyMessageDelayed(WHAT_STOP_SCAN, STOP_SCAN_TIME);// 20秒后停止扫描

		//扫描我们的自定义的设备服务advertising
		ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(Constants.SERVICE_UUID)).build();
		ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
		filters.add(scanFilter);
		
		ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
		mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, mScanCallback);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// 我们第一次需要强制执行蓝牙启用，如果他们没有这样做，则用户将设置为启用。
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
			//蓝牙是禁用的
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivity(enableBtIntent);
			finish();
			return;
		}

		// 检查蓝牙BLE。在产品中，我们的清单条目将保持这个从安装在这些设备上， 但这将允许测试设备或其他侧向载荷，报告是否存在的特征。
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "不支持BLE.", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		//停止任何活动扫描
		stopScan();
		//断开任何活动连接
		if (mBluetoothGatt != null) {
			mBluetoothGatt.disconnect();
			mBluetoothGatt = null;
		}
	}

	protected void stopScan() {
		progressBar.setVisibility(View.GONE);
		mHandler.removeMessages(WHAT_STOP_SCAN);
		if (mScanCallback != null && mBluetoothAdapter.getBluetoothLeScanner() != null) {
			Log.i("gomtel---", "停止扫描");
			mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
		}
	}

	private ScanCallback mScanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			processResult(result);
		}

		@Override
		public void onBatchScanResults(List<ScanResult> results) {
			Log.i(TAG, "onBatchScanResults: " + results.size() + " results");
			for (ScanResult result : results)
				processResult(result);
		}

		@Override
		public void onScanFailed(int errorCode) {
			Log.i(TAG, "LE 扫描失败: " + errorCode);
		}

		private void processResult(ScanResult result) {
			BluetoothDevice device = result.getDevice();
			Log.i(TAG, "New LE Device: " + device.getName() + "  " + device.getAddress() + " @" + result.getRssi());
			mDevicesAdapter.add(device);
		}
	};

	protected BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

		//请求连接之后的回调， status返回的状态成功与否， newState提示新的状态
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			super.onConnectionStateChange(gatt, status, newState);
			Log.i("gomtel---", "onConnectionStateChange");
			mBluetoothGatt = gatt;

			BluetoothDevice device = gatt.getDevice();
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Log.i("gomtel---", "已连接!" + device.getName() + ", " + device.getAddress());
				Message.obtain(mHandler, WHAT_STATE_CONNECTED, device).sendToTarget();
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				Log.i("gomtel---", "已断开!");
				Message.obtain(mHandler, WHAT_STATE_DISCONNECTED, device).sendToTarget();
			}
		}

		//服务发现之后的回调 
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			super.onServicesDiscovered(gatt, status);
			Log.i("gomtel---", "onServicesDiscovered");
		}

		//请求 characteristic读之后的回调 
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			super.onCharacteristicRead(gatt, characteristic, status);
			Log.i("gomtel---", "onCharacteristicRead");
		}

		//请求characteristic写之后的回调 
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			super.onCharacteristicWrite(gatt, characteristic, status);
			Log.i("gomtel---", "onCharacteristicWrite");
		}

		//characteristic属性改变之后的回调 
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicChanged(gatt, characteristic);
			Log.i("gomtel---", "onCharacteristicChanged");
		}

		//类似characteristic 
		public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			super.onDescriptorRead(gatt, descriptor, status);
			Log.i("gomtel---", "onDescriptorRead");
		}

		//类似characteristic 
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			super.onDescriptorWrite(gatt, descriptor, status);
			Log.i("gomtel---", "onDescriptorWrite");
		}

		//可靠写完成之后的回调 
		public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
			super.onReliableWriteCompleted(gatt, status);
			Log.i("gomtel---", "onReliableWriteCompleted");
		}

		//读远端Rssi请求之后的回调 
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			super.onReadRemoteRssi(gatt, rssi, status);
			Log.i("gomtel---", "onReadRemoteRssi");
		}

		//请求MTU改变之后的回调 
		public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
			super.onMtuChanged(gatt, mtu, status);
			Log.i("gomtel---", "onMtuChanged");
		}
	};
}
