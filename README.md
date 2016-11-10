# BluetoothBleCentralOrPeriphera
运行效果图：

 ![image](https://github.com/kairui467/BluetoothCentralOrPeriphera/raw/master/screen/screen2.jpg) ![image](https://github.com/kairui467/BluetoothCentralOrPeriphera/raw/master/screen/screen3.jpg)![image](https://github.com/kairui467/BluetoothCentralOrPeriphera/raw/master/screen/screen1.jpg)
 > Central and Periphera and MainActivity

## 此Demo目的：

测试使用蓝牙的物理地址去连接BLE设备的可行性（并非BLE地址）

### 中央
 - 支持经典蓝牙和BLE的扫描
 - 使用物理地址连接BLE，因为BLE的地址是随蓝牙开关而随机生成的；
 **连接代码**

 ```java
BluetoothDevice bDevice = mBluetoothAdapter.getRemoteDevice(address);
mBluetoothGatt = bDevice.connectGatt(getApplicationContext(), false, mGattCallback);
boolean connect = mBluetoothGatt.connect();
```

调用上面的后如果连接成功或者断开都会异步去回调：
`BluetoothGattCallback.onConnectionStateChange`

大概测试下来如果**10秒左右**没有走回调则说明服务端设备**不在附近**或者**蓝牙被关闭**了，以此来实现自动连接功能

- 当连接断开并且自动连接为true后，则启动重连线程，线程在执行连接后会等待`wait()`;直到超时或者`onConnectionStateChange`被回调之后才会唤醒`notifyAll`线程；默认超时时间为10秒

- 连接成功且服务`discoverServices()`发现之后会循环发数据
`BluetoothGatt.writeCharacteristic(characteristic)`

------------



###周边
- 提供蓝牙开关以测试自动重连
- 显示重连耗时以及已连接的设备，点击已连接设备可断开连接
- 底部使用ListView展示接收到的数据

------------


### 使用物理地址连接BLE的坑：
#### 优点：
- 由于BLE地址在重启蓝牙后再扫描会改变，而物理地址不受此限制
- 而且无需进行功耗较高的扫描操作
- 使用物理地址连接可以无需服务端开启周边（Periphera）模式，连接成功后经测试与BLE方式连接没有明显区别，所有回调均正常执行

#### 缺点：
- 使用物理地址连接，相对与使用BLE连接**数据发送长度较短**，大概在10个字节以内；且一旦超过长度则会发送失败，BLE只会丢包；而且失败后后面的发送也会一直失败，BLE则无影响
- 如果长时间使用物理地址重连失败后，当服务端再次启动蓝牙或者回到有效范围内会有很大概率**连接不上**，或者连接时间过长，只能重启蓝牙

#### 疑问：
- 通过搜索引擎没有发现直接使用物理地址连接BLE的资料；既然物理地址也可以连接，那BLE地址随机的意义又何在？

####总结：
- 目前测试结果证明使用物理地址只适用于重连机制，实际传输数据还是要使用BLE
