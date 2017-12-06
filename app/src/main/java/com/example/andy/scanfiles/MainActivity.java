package com.example.andy.scanfiles;

import android.Manifest;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

public class MainActivity extends AppCompatActivity implements RetainedFragment.OnDataPass {

    private static final String TAG_RETAINED_FRAGMENT = "RetainedFragment";

    private RetainedFragment mRetainedFragment;

    private EditText mScanResultView;
    private Button mScanActionButton;
    private Button mShareButton;
    private ProgressBar spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mScanResultView = (EditText) findViewById(R.id.editTextScanResult);
        mScanActionButton = (Button) findViewById(R.id.scan);
        mShareButton = (Button) findViewById(R.id.share);
        spinner = (ProgressBar)findViewById(R.id.progressBar1);

        // find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        mRetainedFragment = (RetainedFragment) fm.findFragmentByTag(TAG_RETAINED_FRAGMENT);

        // create the fragment and data the first time
        if (mRetainedFragment == null) {
            // add the fragment
            mRetainedFragment = new RetainedFragment();
            fm.beginTransaction().add(mRetainedFragment, TAG_RETAINED_FRAGMENT).commit();
        }

        mRetainedFragment.setDataPass(this);
        if (mRetainedFragment.isScanning()) {
            spinner.setVisibility(View.VISIBLE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkPermission();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(isFinishing()) {
            // Remove the retained fragment
            FragmentManager fm = getFragmentManager();
            fm.beginTransaction().remove(mRetainedFragment).commit();
        }
    }

    public void onClickScanAction(View v)
    {
        if (mRetainedFragment.isScanning()) {
            mScanActionButton.setText(getString(R.string.action_go_scan));
            mScanResultView.setText(getString(R.string.hint));
            mRetainedFragment.killScanJob();
            spinner.setVisibility(View.GONE);
        } else {
            mScanActionButton.setText(getString(R.string.action_stop_scan));
            mRetainedFragment.doScan();
            mShareButton.setEnabled(false);
            spinner.setVisibility(View.VISIBLE);
        }
    }

    public void onClickShare(View v) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/html");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, mScanResultView.getText());
        startActivity(Intent.createChooser(sharingIntent, getString(R.string.share_title)));
    }

    @Override
    public void onDataPass(final String data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                spinner.setVisibility(View.GONE);
                mScanResultView.setText(data);
                mScanActionButton.setText(getString(R.string.action_go_scan));  // reset the scan button label
                mShareButton.setEnabled(true);
            }
        });
    }

    @Override
    public void onBackPressed() {
        mRetainedFragment.killScanJob();
        finish();
    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {//Can add more as per requirement

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    123);
        } else {
            mScanActionButton.setEnabled(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 123: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mScanActionButton.setEnabled(true);
                } else {
                    checkPermission();
                }
                return;
            }
        }
    }
}
