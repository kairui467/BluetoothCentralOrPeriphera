package com.example.bluetooth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;

/**
 * 
 * @author likairui
 * 
 * 参考：http://ju.outofmemory.cn/entry/217374
 *
 * @date 2016/10/18  下午5:13:15
 */
@SuppressLint("NewApi")
public class CentralActivity extends Activity {

	/** 要连接的对端设备物理地址 */
	private static final String MAC_ADDRESS = "B2:56:46:65:80:0A";
	private final String TAG = Constants.TAG;
	private static final int WHAT_START_SCAN = 1;
	private static final int WHAT_STOP_SCAN = 2;
	private static final int WHAT_STATE_CONNECTED = 3;
	private static final int WHAT_STATE_DISCONNECTED = 4;
	/** 循环读取信号 */
	private static final int WHAT_REFRESH_RSSI = 5;
	/** 可以发送数据，在服务发现成功后 */
	private static final int WHAT_CAN_SEND_DATA = 6;
	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGatt mBluetoothGatt;

	private DevicesAdapter mDevicesAdapter;

	private ListView mlvDevice;
	private RelativeLayout mRlSend;
	private ListView mlvData;
	private EditText mEtSendData;
	private Button mBtnSendData;
	private ProgressDialog mProgressDialog;

	private Switch mSwAutoConn;
	boolean isAutoConn = true;
	private ProgressBar mpBarConn;

	/** 扫描超时时间 */
	final int STOP_SCAN_TIME = 15 * 1000;
	/** 连接等待时间 */
	final int CONNECT_WAIT_TIME = 10 * 1000;

	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case WHAT_START_SCAN:
					mDevicesAdapter.add((BDevice) msg.obj);
					break;
				case WHAT_STOP_SCAN:
					stopScan();
					break;
				case WHAT_STATE_CONNECTED:
					BluetoothDevice device = (BluetoothDevice) msg.obj;
					mDevicesAdapter.removeAll();
					mDevicesAdapter.add(device, true);
					getDeviceLoopRssi();
					break;

				case WHAT_STATE_DISCONNECTED:
					BluetoothDevice disDevice = (BluetoothDevice) msg.obj;
					mDevicesAdapter.refreshConnState(disDevice, getConnectState());
					mDevicesAdapter.refreshRssi(disDevice.getAddress(), 0);
					if (mBluetoothGatt != null)
						mBluetoothGatt.close();

					refreshSendDataView(false);
					startAutoConnect();
					break;

				case WHAT_REFRESH_RSSI:
					BDevice bDevice = (BDevice) msg.obj;
					mDevicesAdapter.refreshRssi(bDevice.mDevice.getAddress(), bDevice.rssi);
					break;

				case WHAT_CAN_SEND_DATA:
					setCharacteristicNotification(Constants.SERVICE_UUID, Constants.CHARACTERISTIC_UUID, true);
					String text = "{”low_temp”:”25”,”weather”:”多云””high_temp”:”31”,”city”:”深圳”,”curr_temp”:”28”,”pm2_5”:”60”,”quality”:”良”}";
					refreshSendDataView(true);
					mEtSendData.setText(text);

					// 循环发数据
					new Thread(new Runnable() {
						public void run() {
							while (getConnectState()) {
								SystemClock.sleep(1500);
								boolean result = writeCharacteristic(Constants.SERVICE_UUID, Constants.CHARACTERISTIC_UUID, "{”low_temp”:”25");
								//Log.i(TAG, "发送数据的返回值：" + result);
							}
						}
					}).start();
					break;
				default:
					break;
			}
		}

		private void refreshSendDataView(boolean state) {
			mRlSend.setVisibility(state ? View.VISIBLE : View.GONE);
			mEtSendData.setEnabled(state);
			mBtnSendData.setEnabled(state);
		}

		private void getDeviceLoopRssi() {
			new Thread(new Runnable() {
				public void run() {
					while (getConnectState() && mBluetoothGatt != null) {
						mBluetoothGatt.readRemoteRssi();
						SystemClock.sleep(2 * 1000);
					}
				}
			}).start();
		};
	};

	class Token {
	};

	Token mToken = new Token();

	private void startAutoConnect() {
		new Thread(new Runnable() {
			public void run() {
				while (!getConnectState() && isAutoConn) {
					Log.i(TAG, "自动连接线程启动..." + Thread.currentThread().getId());
					EventBus.getDefault().post(new EventObj(EventObj.Toast, "自动连接线程启动..."));
					connect(MAC_ADDRESS, false);
					synchronized (mToken) {
						try {
							mToken.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}).start();
	}

	private boolean getConnectState() {
		if (mBluetoothManager != null && mBluetoothGatt != null) {
			final List<BluetoothDevice> connectedDevices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
			runOnUiThread(new Runnable() {
				public void run() {
					mSwAutoConn.setText("连接的设备：" + connectedDevices.size());
				}
			});
			return (connectedDevices.size() > 0) ? true : false;
		}
		return false;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_central);

		mlvDevice = (ListView) findViewById(R.id.lv_devicelist);
		mRlSend = (RelativeLayout) findViewById(R.id.rl_sendData);
		mlvData = (ListView) findViewById(R.id.lv_datalist);
		mEtSendData = (EditText) findViewById(R.id.et_text);
		mBtnSendData = (Button) findViewById(R.id.btn_send);
		mSwAutoConn = (Switch) findViewById(R.id.sw_auto_conn);
		mpBarConn = (ProgressBar) findViewById(R.id.pb_connect);

		EventBus.getDefault().register(this);

		mDevicesAdapter = new DevicesAdapter(getApplicationContext());
		mlvDevice.setAdapter(mDevicesAdapter);

		// 蓝牙技术在Android 4.3以上是通过BluetoothManager访问，而不是老的静态 BluetoothAdapter.getInstance()
		mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		mBluetoothAdapter.setName("中央");

		// 注册经典蓝牙扫描的广播
		IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(mReceiver, intentFilter);

		// 设置标题
		getActionBar().setTitle("Central：" + mBluetoothAdapter.getAddress());
		getActionBar().setDisplayHomeAsUpEnabled(true);

		// 搜索后显示的设备点击事件
		mlvDevice.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				BDevice item = mDevicesAdapter.getItem(position);
				connect(item.mDevice.getAddress(), getConnectState());
				// 更新界面
				mDevicesAdapter.refreshConnState(item.mDevice, getConnectState());
			}
		});

		mSwAutoConn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				isAutoConn = isChecked;
				ToastUtils.showToast(CentralActivity.this, "isChecked = " + isChecked);
			}
		});
	}

	@Subscribe(threadMode = ThreadMode.MAIN)
	public void showUIMain(EventObj event) {
		switch (event.eventType) {
			case EventObj.Toast:
				ToastUtils.showToast(CentralActivity.this, event.msg);
				break;
			case EventObj.Dialog:
				if (event.msg.isEmpty())
					refreshProgressDialog(event.isShow);
				else
					refreshProgressDialog(event.isShow, event.msg);
				break;
		}
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
			ToastUtils.showToast(CentralActivity.this, "不支持BLE.");
			finish();
			return;
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		//停止任何活动扫描
		stopScan();
		//断开任何活动连接
		if (mBluetoothGatt != null) {
			mBluetoothGatt.disconnect();
			mBluetoothGatt = null;
		}

		mHandler.removeCallbacksAndMessages(null);
		if (mReceiver != null)
			unregisterReceiver(mReceiver);

		EventBus.getDefault().unregister(this);
	}

	public void scanClick(View v) {
		switch (v.getId()) {
			case R.id.btn_scanBT:
				startScanBT();
				break;
			case R.id.btn_scanBLE:
				//startScanBLE();
				verifyIfRequestPermission();
				break;
			case R.id.btn_conn:
				connect(MAC_ADDRESS, false);
				break;
			case R.id.btn_send:
				String text = mEtSendData.getText().toString();
				boolean result = writeCharacteristic(Constants.SERVICE_UUID, Constants.CHARACTERISTIC_UUID, text);
				Log.i(TAG, "状态：" + result);
				break;
		}
	}

	private synchronized void connect(final String address, final boolean isDisconnect) {
		//stopScan();
		//EventBus.getDefault().post(new EventObj(EventObj.Dialog, "连接中......", true));
		refreshConnecting(true);

		new Thread(new Runnable() { // 连接方法和断开方法要放在线程里面来执行，要不然很容易连不上，尤其是三星手机，note3和s5都已测过，
			public void run() { // 连上率30%左右，放在线程上执行有90%多
				BluetoothDevice bDevice = mBluetoothAdapter.getRemoteDevice(address);
				if (bDevice == null) {
					Log.i(TAG, "BluetoothDevice is null");
					return;
				}

				// 为了能进行下次的稳定连接必须的
				if (mBluetoothGatt != null) {
					mBluetoothGatt.disconnect();
					SystemClock.sleep(1000);
					if (mBluetoothGatt != null)
						mBluetoothGatt.close();
					mBluetoothGatt = null;
					if (isDisconnect)
						refreshConnecting(false);
				}
				if (!isDisconnect) {
					mBluetoothGatt = bDevice.connectGatt(getApplicationContext(), false, mGattCallback);
					boolean connect = mBluetoothGatt.connect();
					Log.i(TAG, "开始连接...    address:" + bDevice.toString() + ",connect：" + connect);

					mHandler.postDelayed(new Runnable() {
						public void run() {
							if (!getConnectState()) {
								refreshConnecting(false);
								//EventBus.getDefault().post(new EventObj(EventObj.Dialog, false));
								EventBus.getDefault().post(new EventObj(EventObj.Toast, "连接超时！"));
								synchronized (mToken) {
									mToken.notifyAll();
								}
							}
						}
					}, CONNECT_WAIT_TIME);
				}
			}
		}).start();
	}

	private void refreshConnecting(final boolean isVisible) {
		runOnUiThread(new Runnable() {
			public void run() {
				mpBarConn.setVisibility(isVisible ? View.VISIBLE : View.GONE);
				mlvDevice.setEnabled(!isVisible);
				mlvDevice.setClickable(!isVisible);
			}
		});
	}

	private void startScanBT() {
		mDevicesAdapter.removeAll();
		mHandler.sendEmptyMessageDelayed(WHAT_STOP_SCAN, STOP_SCAN_TIME * 2);
		boolean startDiscovery = mBluetoothAdapter.startDiscovery();
		if (startDiscovery)
			Log.i(TAG, "开始扫描经典蓝牙。。。");
	}

	/**
	 * 监听经典蓝牙广播
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
				Log.i(TAG, "开始搜索");
				EventBus.getDefault().post(new EventObj(EventObj.Dialog, true));

			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				Log.i(TAG, "搜索结束");
				EventBus.getDefault().post(new EventObj(EventObj.Dialog, false));

			} else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				short rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);// 信号强度

				Log.i(TAG, "搜索到蓝牙设备：" + device);
				Log.i(TAG, Thread.currentThread().getId() + "********   "
						+ device.getBluetoothClass().getDeviceClass());

				Message.obtain(mHandler, WHAT_START_SCAN, new BDevice(device, rssi)).sendToTarget();
				int majorDeviceClass = device.getBluetoothClass().getMajorDeviceClass();
			}
		}
	};

	private void startScanBLE() {
		Log.d(TAG, "开始扫描Ble...");
		EventBus.getDefault().post(new EventObj(EventObj.Dialog, true));
		mDevicesAdapter.removeAll();
		mHandler.sendEmptyMessageDelayed(WHAT_STOP_SCAN, STOP_SCAN_TIME);// 20秒后停止扫描

		// 扫描我们的自定义的设备服务advertising
		ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(Constants.SERVICE_UUID)).build();
		ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
		filters.add(scanFilter);

		ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
		mBluetoothAdapter.getBluetoothLeScanner().startScan(/*filters, settings, */mScanCallback);
	}

	protected void stopScan() {
		EventBus.getDefault().post(new EventObj(EventObj.Dialog, false));
		mHandler.removeCallbacksAndMessages(null);
		if (mScanCallback != null && mBluetoothAdapter.getBluetoothLeScanner() != null) {
			Log.i(TAG, "停止扫描BLE");
			mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
		}

		if (mBluetoothAdapter.isDiscovering()) {
			Log.i(TAG, "停止扫描经典蓝牙");
			mBluetoothAdapter.cancelDiscovery();
		}
	}

	//***************************************************** Dialog ******************************************************

	private synchronized void refreshProgressDialog(boolean isShow) {
		refreshProgressDialog(isShow, "扫描中......");
	}

	private synchronized void refreshProgressDialog(final boolean isShow, final String msg) {
		if (mProgressDialog == null) {
			mProgressDialog = new ProgressDialog(CentralActivity.this);
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			mProgressDialog.setCanceledOnTouchOutside(false);
			mProgressDialog.setOnDismissListener(new OnDismissListener() {
				public void onDismiss(DialogInterface dialog) {
					stopScan();
				}
			});
		}

		mProgressDialog.setMessage(msg);
		if (isShow && !mProgressDialog.isShowing())
			mProgressDialog.show();
		else if (mProgressDialog.isShowing())
			mProgressDialog.cancel();
	}

	class EventObj {
		static final int Toast = 1;
		static final int Dialog = 2;
		int eventType = 0;
		boolean isShow = false;
		String msg = "";

		public EventObj(int eventType, String msg) {
			this(eventType, msg, false);
		}

		public EventObj(int eventType, boolean isShow) {
			this(eventType, "", isShow);
		}

		public EventObj(int eventType, String msg, boolean isShow) {
			this.eventType = eventType;
			this.msg = msg;
			this.isShow = isShow;
		}
	}

	//***************************************************** 回调 ******************************************************

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
			Log.i(TAG, "New LE Device: " + result.toString());

			BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(result.getDevice().getAddress());
			Log.i(TAG, "remoteDevice：" + result.getClass().getCanonicalName());

			//result.getScanRecord().
			Log.i(TAG, "***********************************************");
			mDevicesAdapter.add(result.getDevice(), result.getRssi());
		}
	};

	protected BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

		//请求连接之后的回调， status:返回的状态成功与否， newState:提示新的状态
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			super.onConnectionStateChange(gatt, status, newState);
			mBluetoothGatt = gatt;
			//refreshProgressDialog(false);
			EventBus.getDefault().post(new EventObj(EventObj.Dialog, false));
			refreshConnecting(false);
			// 唤醒重连线程
			synchronized (mToken) {
				mToken.notifyAll();
			}

			BluetoothDevice device = gatt.getDevice();
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Log.i(TAG, "已连接!!!" + device.getName() + ", " + device.getAddress());
				Message.obtain(mHandler, WHAT_STATE_CONNECTED, device).sendToTarget();
				gatt.discoverServices();
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				Log.i(TAG, "已断开!!!");
				Message.obtain(mHandler, WHAT_STATE_DISCONNECTED, device).sendToTarget();
			}
		}

		//服务发现之后的回调 
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			super.onServicesDiscovered(gatt, status);
			Log.i(TAG, "onServicesDiscovered");
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.i(TAG, "服务发现成功");
				//读取当前 characteristic 的值
				//gatt.readCharacteristic(service.getCharacteristic(Constants.CHARACTERISTIC_UUID));
				//setCharacteristicNotification(Constants.SERVICE_UUID, Constants.CHARACTERISTIC_UUID, true);
				Message.obtain(mHandler, WHAT_CAN_SEND_DATA).sendToTarget();
			}
		}

		//请求 characteristic读之后的回调
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			super.onCharacteristicRead(gatt, characteristic, status);
			Log.i(TAG, "onCharacteristicRead");
			final int charValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0);
			Log.i(TAG, "charValue:" + charValue);
		}

		//请求characteristic写之后的回调 
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			super.onCharacteristicWrite(gatt, characteristic, status);
			//Log.i(TAG, "status:" + status + ", onCharacteristicWrite:" + new String(characteristic.getValue()));
		}

		//characteristic属性改变之后的回调 
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicChanged(gatt, characteristic);
			Log.i(TAG, "onCharacteristicChanged，" + new String(characteristic.getValue()));
		}

		//类似characteristic 
		public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			super.onDescriptorRead(gatt, descriptor, status);
			Log.i(TAG, "onDescriptorRead");
		}

		//类似characteristic 
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			super.onDescriptorWrite(gatt, descriptor, status);
			Log.i(TAG, "onDescriptorWrite");
		}

		//可靠写完成之后的回调
		public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
			super.onReliableWriteCompleted(gatt, status);
			Log.i(TAG, "onReliableWriteCompleted");
		}

		//读远端Rssi请求之后的回调 
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			super.onReadRemoteRssi(gatt, rssi, status);
			Log.i(TAG, "onReadRemoteRssi:" + rssi);
			Message.obtain(mHandler, WHAT_REFRESH_RSSI, new BDevice(gatt.getDevice(), rssi)).sendToTarget();
		}

		//请求MTU改变之后的回调 
		public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
			super.onMtuChanged(gatt, mtu, status);
			Log.i(TAG, "onMtuChanged");
		}
	};

	public void setCharacteristicNotification(UUID serviceUUID, UUID characteristicUUID, boolean enabled) {
		if (mBluetoothGatt == null) {
			Log.i(TAG, "BluetoothAdapter not initialized");
			return;
		}
		try {
			BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
			BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);

			mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

			BluetoothGattDescriptor descriptor = characteristic
					.getDescriptor(Constants.DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION);
			if (descriptor == null) {
				Log.i(TAG, "descriptor is null");
				return;
			}
			descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
					: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		} catch (Exception e) {
			e.printStackTrace();
			Log.i(TAG, "e:" + e);
		}
	}

	/**
	 * 向服务端写数据
	 * @param serviceUUID
	 * @param characteristicUUID
	 * @param value 待写入的值
	 */
	public synchronized boolean writeCharacteristic(UUID serviceUUID, UUID characteristicUUID, String value) {
		if (mBluetoothGatt != null) {
			BluetoothGattService service = mBluetoothGatt.getService(serviceUUID);
			if (service == null) {
				Log.i(TAG, "service is null");
				return false;
			}
			BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
			characteristic.setValue(value.getBytes());
			return mBluetoothGatt.writeCharacteristic(characteristic);
		}
		return false;
	}

	private void verifyIfRequestPermission() {
		if (Build.VERSION.SDK_INT >= 23) {
			Log.i(TAG, "onCreate: checkSelfPermission");
			if (ContextCompat.checkSelfPermission(this, //
					Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				Log.i(TAG, "onCreate: Android 6.0 动态申请权限");

				if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
					Log.i(TAG, "*********onCreate: shouldShowRequestPermissionRationale**********");
					Toast.makeText(this, "只有允许访问位置才能搜索到蓝牙设备", Toast.LENGTH_SHORT).show();
				} else {
					ActivityCompat.requestPermissions(this, new String[] {
						Manifest.permission.ACCESS_COARSE_LOCATION,
						Manifest.permission.ACCESS_FINE_LOCATION }, 0x1);
				}
			} else {
				startScanBLE();
			}
		} else {
			startScanBLE();
		}
	}
}
