package com.example.bluetooth;

import java.util.UUID;

public class Constants {

	public static final UUID SERVICE_UUID = UUID.fromString("00008A1A-0000-1000-8000-00805F9B34FB");
	public static final UUID CHARACTERISTIC_UUID = UUID.fromString("00008A2A-0000-1000-8000-00805F9B34FB");
	//public static final UUID DESCRIPTOR_UUID = UUID.fromString("00008A2B-0000-1000-8000-00805F9B34FB");
	public static final UUID DESCRIPTOR_CLIENT_CHARACTERISTIC_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

	protected static final String TAG = "gomtel---";
}
