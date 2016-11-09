package com.example.bluetooth;

import java.util.ArrayList;
import java.util.List;

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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

@SuppressLint("NewApi")
public class PeripheraActivity extends Activity {

	private final String TAG = Constants.TAG;
	private static final int WHAT_STATE_CONNECTED = 2;
	private static final int WHAT_STATE_DISCONNECTED = 3;
	private static final int WHAT_WRITE_REQUEST = 4;

	private BluetoothManager mBluetoothManager;
	private BluetoothGattServer mGattServer;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothLeAdvertiser mLeAdvertiser;

	private Switch mSwitch;
	private ListView mLvDevice;

	private RelativeLayout mRlSend;
	private ListView mLvData;
	private ArrayList<String> mData;
	private ArrayAdapter<String> mDataAdapter;
	private EditText mEtSendData;
	private Button mBtnSendData;
	private TextView mTvReconnectTime;
	private TextView mTvConnDevice;

	private DevicesAdapter mDevicesAdapter;
	protected String mConnDeviceAddress = null;

	Handler mHandler = new Handler() {
		private long currentTimeMillis = System.currentTimeMillis();

		public void handleMessage(Message msg) {
			switch (msg.what) {
				case WHAT_STATE_CONNECTED:
					BluetoothDevice device = (BluetoothDevice) msg.obj;
					mDevicesAdapter.add(device, true);
					mRlSend.setVisibility(View.VISIBLE);

					long reconnectTime = System.currentTimeMillis() - currentTimeMillis;
					mTvReconnectTime.setText("上次重连耗时：" + reconnectTime);
					break;
				case WHAT_STATE_DISCONNECTED:
					mDevicesAdapter.removeAll();
					mRlSend.setVisibility(View.GONE);
					mData.clear();
					mDataAdapter.notifyDataSetChanged();
					mEtSendData.setText("" + mData.size());
					currentTimeMillis = System.currentTimeMillis();
					break;
				case WHAT_WRITE_REQUEST:
					String value = (String) msg.obj;
					Log.i(TAG, "onCharacteristicWriteRequest:" + new String(value));
					mData.add(0, value);
					mDataAdapter.notifyDataSetChanged();
					mEtSendData.setText("" + mData.size());
					break;
			}
		};
	};

	public void scanClick(View v) {
		switch (v.getId()) {
			/*case R.id.btn_conn:
				if (mConnDeviceAddress.isEmpty())
					return;

				mHandler.post(new Runnable() {
					public void run() {
						BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(mConnDeviceAddress);
						boolean connectState = mGattServer.connect(remoteDevice, true);
						Log.i(TAG, "连接状态：" + connectState);
					}
				});
				break;*/
			case R.id.btn_empty:
				mData.clear();
				mDataAdapter.notifyDataSetChanged();
				mEtSendData.setText("" + mData.size());
				break;

			case R.id.btn_send:

				break;
		}
	}

	private boolean getConnectState() {
		if (mBluetoothManager != null) {
			final List<BluetoothDevice> connectedDevices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
			runOnUiThread(new Runnable() {
				public void run() {
					mTvConnDevice.setText("连接的设备：" + connectedDevices.size());
				}
			});
			return (connectedDevices.size() > 0) ? true : false;
		}
		return false;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow()
				.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_periphera);

		// 注册控件
		mSwitch = (Switch) findViewById(R.id.sw_on_off);
		mLvDevice = (ListView) findViewById(R.id.lv_devicelist);

		mRlSend = (RelativeLayout) findViewById(R.id.rl_sendData);
		mLvData = (ListView) findViewById(R.id.lv_datalist);
		mEtSendData = (EditText) findViewById(R.id.et_text);
		mBtnSendData = (Button) findViewById(R.id.btn_send);
		mTvReconnectTime = (TextView) findViewById(R.id.tv_reconnectTime);
		mTvConnDevice = (TextView) findViewById(R.id.tv_conn_device);

		mDevicesAdapter = new DevicesAdapter(getApplicationContext());
		mLvDevice.setAdapter(mDevicesAdapter);

		mData = new ArrayList<String>();
		mDataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mData) {
			public View getView(int position, View convertView, ViewGroup parent) {
				TextView tv = (TextView) super.getView(position, convertView, parent);
				if (tv != null)
					tv.setTextSize(10);
				return tv;
			}
		};
		mLvData.setAdapter(mDataAdapter);

		mLvDevice.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// 主动断开所有连接
				List<BluetoothDevice> connectedDevices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
				for (BluetoothDevice bd : connectedDevices)
					if (bd != null)
						mGattServer.cancelConnection(bd);
			}
		});

		mSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				boolean sw = isChecked ? mBluetoothAdapter.enable() : mBluetoothAdapter.disable();
			}
		});

		new Thread(new Runnable() {
			public void run() {
				while (true) {
					SystemClock.sleep(2 * 1000);
					getConnectState();
				}
			}
		}).start();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// 准备开始进行广播
		initBluetooth();
	}

	private void initBluetooth() {
		// 初始化蓝牙
		mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		mLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
		mBluetoothAdapter.setName("周边 " + mBluetoothAdapter.getAddress());

		IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		registerReceiver(mReceiver, intentFilter);

		// 设置标题
		getActionBar().setTitle("Periphera：" + mBluetoothAdapter.getAddress());
		getActionBar().setDisplayHomeAsUpEnabled(true);

		// We need to enforce that Bluetooth is first enabled, and take the user to settings to enable it if they have not done so.
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {//启用蓝牙
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, 1);
			ToastUtils.showToast(this, "蓝牙不可用");
			finish();
			return;
		}

		// Check for Bluetooth LE Support.  In production, our manifest entry will keep this from installing on these devices, but this will allow test devices or other sideloads to report whether or not the feature exists.
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			ToastUtils.showToast(this, "No LE Support.");
			finish();
			return;
		}

		//Check for advertising support. Not all devices are enabled to advertise Bluetooth LE data.
		if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
			ToastUtils.showToast(this, "No Advertising Support.");
			finish();
			return;
		}

		mSwitch.setChecked(mBluetoothAdapter.isEnabled());
		startAdvertise();
	}

	public void startAdvertise() {
		if (mLeAdvertiser == null)
			return;

		mGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
		addServiceToGattServer();

		//AdvertisementData.Builder dataBuilder = new AdvertisementData.Builder();
		AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
		AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();

		dataBuilder.setIncludeDeviceName(true);// must need true,otherwise can not be discovered when central scan
		//dataBuilder.setIncludeTxPowerLevel(false); //necessity to fit in 31 byte advertisement
		//dataBuilder.setServiceUuids(serviceUuids);
		dataBuilder.addServiceUuid(new ParcelUuid(Constants.SERVICE_UUID));

		settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
		//settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
		settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
		//settingsBuilder.setType(AdvertiseSettings.ADVERTISE_TYPE_CONNECTABLE);
		settingsBuilder.setConnectable(true);

		mLeAdvertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), mAdvertiseCallback);
	}

	private void addServiceToGattServer() {
		BluetoothGattService mGattService = new BluetoothGattService(Constants.SERVICE_UUID,
				BluetoothGattService.SERVICE_TYPE_PRIMARY);
		// alert level char.
		int property = BluetoothGattCharacteristic.PROPERTY_NOTIFY//
				| BluetoothGattCharacteristic.PROPERTY_INDICATE//
				| BluetoothGattCharacteristic.PROPERTY_READ//
				| BluetoothGattCharacteristic.PROPERTY_WRITE//
				| BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;

		BluetoothGattCharacteristic mCharacter = new BluetoothGattCharacteristic(//
				Constants.CHARACTERISTIC_UUID, //
				property//
				, BluetoothGattCharacteristic.PERMISSION_READ | //
						BluetoothGattCharacteristic.PERMISSION_WRITE);

		mGattService.addCharacteristic(mCharacter);
		mGattServer.addService(mGattService);
	}

	/**
	 * 监听经典蓝牙广播
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.i(TAG, device.getName() + " 已连接");
			} else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Log.i(TAG, device.getName() + " 已断开");
			}
		}
	};

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
			mConnDeviceAddress = device.getAddress();
			getConnectState();

			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Log.i(TAG, "已连接!" + device.getName() + ", " + device.getAddress());
				Message.obtain(mHandler, WHAT_STATE_CONNECTED, device).sendToTarget();

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				Log.i(TAG, "已断开");
				Message.obtain(mHandler, WHAT_STATE_DISCONNECTED).sendToTarget();
			}
		}

		/**
		 * 当有服务请求读 characteristic 的时候 
		 * device发请求的远端设备引用
		 * requestId 请求Id 
		 * offset 偏移量 
		 * characteristic被请求的 characteristic 
		 */
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
				BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
			Log.i(TAG, "onCharacteristicReadRequest");
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
			Message.obtain(mHandler, WHAT_WRITE_REQUEST, new String(value)).sendToTarget();;
		}

		/**
		 * 类似characteristic的读
		 */
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
				BluetoothGattDescriptor descriptor) {
			super.onDescriptorReadRequest(device, requestId, offset, descriptor);
			Log.i(TAG, "onDescriptorReadRequest");
		}

		/**
		 * 类似characteristic的写 
		 */
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor,
				boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
			Log.i(TAG, "onDescriptorWriteRequest");
		}

		/**
		 * 执行将保存到队列中的数据写入到characteristic的回调 
		 */
		public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
			super.onExecuteWrite(device, requestId, execute);
			Log.i(TAG, "onExecuteWrite");
		}

		/**
		 * 会回调当有服务端的数据传送到客户端的时候 
		 */
		public void onNotificationSent(BluetoothDevice device, int status) {
			super.onNotificationSent(device, status);
			Log.i(TAG, "onNotificationSent");
		}

		/**
		 * 服务添加之后的回调 
		 */
		public void onServiceAdded(int status, BluetoothGattService service) {
			super.onServiceAdded(status, service);
			Log.i(TAG, "onServiceAdded");
		}

		/**
		 * 请求MTU改变之后的回调 
		 */
		public void onMtuChanged(BluetoothDevice device, int mtu) {
			super.onMtuChanged(device, mtu);
			Log.i(TAG, "onMtuChanged");
		}

	};

	@Override
	protected void onPause() {
		super.onPause();
		stopAdvertising();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mHandler.removeCallbacksAndMessages(null);
		unregisterReceiver(mReceiver);
	}

	/*
	 * Terminate the advertiser
	 */
	private void stopAdvertising() {
		if (mLeAdvertiser == null)
			return;

		mLeAdvertiser.stopAdvertising(mAdvertiseCallback);
	}

}
