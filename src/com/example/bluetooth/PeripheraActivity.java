package com.example.bluetooth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

@SuppressLint("NewApi")
public class PeripheraActivity extends Activity {

	protected static final String TAG = "gomtel---";
	private static final int WHAT_STATE_CONNECTED = 2;
	private static final int WHAT_STATE_DISCONNECTED = 3;

	private BluetoothManager mBluetoothManager;
	private BluetoothGattServer mGattServer;
	private BluetoothAdapter mAdapter;
	private BluetoothLeAdvertiser mLeAdvertiser;
	private Button btnConn;
	private ListView mListView;
	private DevicesAdapter mDevicesAdapter;
	protected String mConnDeviceAddress = null;

	Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
				case WHAT_STATE_CONNECTED:
					BluetoothDevice device = (BluetoothDevice) msg.obj;
					mDevicesAdapter.add(device, true);
					break;
				case WHAT_STATE_DISCONNECTED:
					mDevicesAdapter.removeAll();
					break;
			}
			setBtnState();
		};
	};

	public void scanClick(View v) {
		if (mGattServer == null || mConnDeviceAddress.isEmpty())
			return;
		
		mHandler.post(new Runnable() {
			public void run() {
				BluetoothDevice remoteDevice = mAdapter.getRemoteDevice(mConnDeviceAddress);
				boolean connectState = mGattServer.connect(remoteDevice, true);
				Log.i("gomtel---", "连接状态：" + connectState);
			}
		});
	}

	protected void setBtnState() {
		btnConn.setEnabled(mConnDeviceAddress == null ? false : true);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_periphera);
		mDevicesAdapter = new DevicesAdapter(getApplicationContext());

		btnConn = (Button) findViewById(R.id.btn_conn);
		setBtnState();
		mListView = (ListView) findViewById(R.id.lv_devicelist);
		mListView.setAdapter(mDevicesAdapter);

		mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mAdapter = mBluetoothManager.getAdapter();
		mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);//获取GattServer实例 
	}

	@Override
	protected void onResume() {
		super.onResume();
		// We need to enforce that Bluetooth is first enabled, and take the user to settings to enable it if they have not done so.
		if (mAdapter == null || !mAdapter.isEnabled()) {//启用蓝牙
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivity(enableBtIntent);
			finish();
			return;
		}

		// Check for Bluetooth LE Support.  In production, our manifest entry will keep this from installing on these devices, but this will allow test devices or other sideloads to report whether or not the feature exists.
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "No LE Support.", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		//Check for advertising support. Not all devices are enabled to advertise Bluetooth LE data.
		if (!mAdapter.isMultipleAdvertisementSupported()) {
			Toast.makeText(this, "No Advertising Support.", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		/*mLeAdvertiser = mAdapter.getBluetoothLeAdvertiser();
		mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);*/

		addServer();
		startAdvertising();
	}

	private void addServer() {
		BluetoothGattService mGattService = new BluetoothGattService(Constants.SERVICE_UUID,
				BluetoothGattService.SERVICE_TYPE_PRIMARY);

		BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(//
				Constants.CHARACTERISTIC_UUID, 0x0A, 0x11);

		BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(Constants.DESCRIPTOR_UUID, 0x11);
		descriptor.setValue("gdd test".getBytes());

		characteristic.addDescriptor(descriptor);
		characteristic.setValue("the characteristic".getBytes());
		/*BluetoothGattCharacteristic characteristicUpdating = new BluetoothGattCharacteristic(
				Constants.CHARACTERISTIC_UUID_UPDATING, 0x0A, 0x11);*/
		mGattService.addCharacteristic(characteristic);
		//mGattServer.addCharacteristic(characteristicUpdating);
		mGattServer.addService(mGattService);

		mAdapter.setName("123456");
	}

	private void startAdvertising() {
		mAdapter = mBluetoothManager.getAdapter();
		mLeAdvertiser = mAdapter.getBluetoothLeAdvertiser();

		AdvertiseSettings settings = new AdvertiseSettings.Builder().build();

		AdvertiseData data = new AdvertiseData.Builder()//
				.addServiceUuid(new ParcelUuid(Constants.SERVICE_UUID))//
				.addManufacturerData(1, "1".getBytes())//
				.setIncludeDeviceName(true)//
				.addServiceData(new ParcelUuid(Constants.SERVICE_UUID), "1".getBytes())//
				.build();

		// 发送让中心设备获取自己信息的方法 
		mLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
	}

	/*
	 * Terminate the advertiser
	 */
	private void stopAdvertising() {
		if (mLeAdvertiser == null)
			return;

		mLeAdvertiser.stopAdvertising(mAdvertiseCallback);
	}

	private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
		@Override
		public void onStartSuccess(AdvertiseSettings settingsInEffect) {
			Log.d(TAG, "AdvertiseCallbackImpl->onStartSuccess is being invoked!!!");
			Log.d(TAG, "SETTING MODE:" + settingsInEffect.getMode());
			Log.d(TAG, "SETTING TIMEOUT:" + settingsInEffect.getTimeout());
			Log.d(TAG, "SETTING TxPowerLevel:" + settingsInEffect.getTxPowerLevel());
		}

		@Override
		public void onStartFailure(int errorCode) {
			Log.d(TAG, "AdvertiseCallbackImpl->onStartFailure is being invoked!!!");
			Log.d(TAG, "errorCode is :" + errorCode);
		}
	};

	// 方法都是异步的方法，因此在回调中不要写大量的工作 
	private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
		/**
		 * 当连接状态发生变化的时候的回调，比如连接和断开的时候都会回调参数说明 
		 * device 产生回调的远端设备引用 
		 * status 回调的时候的状态信息 
		 * newState 进入的新的状态的信息 
		 */
		public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
			super.onConnectionStateChange(device, status, newState);
			Log.i("gomtel---", "onConnectionStateChange");
			mConnDeviceAddress = device.getAddress();

			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Log.i("gomtel---", "已连接!" + device.getName() + ", " + device.getAddress());
				Message.obtain(mHandler, WHAT_STATE_CONNECTED, device).sendToTarget();

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				Log.i("gomtel---", "已断开");
				Message.obtain(mHandler, WHAT_STATE_DISCONNECTED).sendToTarget();
			}
		}

		/**
		 * 当有服务请求读 characteristic的时候 
		 * device发请求的远端设备引用 
		 * requestId 请求Id 
		 * offset 偏移量 
		 * characteristic被请求的 characteristic 
		 */
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
				BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
			Log.i("gomtel---", "onCharacteristicReadRequest");
		}

		/**
		 * characteristic 需要写的 characteristic
		 * preparedWrite 是不是需要保存到队列之后执行写入操作的 
		 * responseNeeded 需不需要返回数据的 
		 * value 需要写的数据 
		 */
		public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
				BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset,
				byte[] value) {
			super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
			Log.i("gomtel---", "onCharacteristicWriteRequest");
		}
		
		/**
		 * 类似characteristic的读
		 */
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
				BluetoothGattDescriptor descriptor) {
			super.onDescriptorReadRequest(device, requestId, offset, descriptor);
		}

		/**
		 * 类似characteristic的写 
		 */
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
				boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
			Log.i("gomtel---", "onDescriptorWriteRequest");
		}

		/**
		 * 执行将保存到队列中的数据写入到characteristic的回调 
		 */
		public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
			super.onExecuteWrite(device, requestId, execute);
			Log.i("gomtel---", "onExecuteWrite");
		}

		/**
		 * 会回调当有服务端的数据传送到客户端的时候 
		 */
		public void onNotificationSent(BluetoothDevice device, int status) {
			super.onNotificationSent(device, status);
			Log.i("gomtel---", "onNotificationSent");
		}

		/**
		 * 服务添加之后的回调 
		 */
		public void onServiceAdded(int status, BluetoothGattService service) {
			super.onServiceAdded(status, service);
			Log.i("gomtel---", "onServiceAdded");
		}

		/**
		 * 请求MTU改变之后的回调 
		 */
		public void onMtuChanged(BluetoothDevice device, int mtu) {
			super.onMtuChanged(device, mtu);
			Log.i("gomtel---", "onMtuChanged");
		}

	};

	@Override
	protected void onPause() {
		super.onPause();
		stopAdvertising();
	}

}
