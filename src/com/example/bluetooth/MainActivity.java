package com.example.bluetooth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}

	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.central:
				startActivity(new Intent(MainActivity.this, CentralActivity.class));
				break;
			case R.id.periphera:
				startActivity(new Intent(MainActivity.this, PeripheraActivity.class));
				break;
			case R.id.exit:
				onBackPressed();
				break;
		}
	}
}
