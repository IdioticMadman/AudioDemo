package com.robert.audiodemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AppContract.View {
    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private EditText mInput;
    private Button mSubmitButton;
    private TextView mTipsView;
    private AppContract.Presenter mPresenter;
    private AlertDialog mDialog;
    private Toast mToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTipsView = findViewById(R.id.txt_tips);
        mTipsView.setText(Html.fromHtml(getString(R.string.tips)));

        mSubmitButton = findViewById(R.id.btn_submit);
        mSubmitButton.setOnClickListener(this);

        mInput = findViewById(R.id.input);
        mInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                // 输入框变化时按钮跟随
                mSubmitButton.setText(s.length() == 0 ? R.string.btn_random : R.string.btn_link);
            }
        });

        mPresenter = new Presenter(this);
        // 默认检查一次权限
        checkPermission();
    }

    @Override
    public void onClick(View v) {
        if (!checkPermission()) {
            showToast(getString(R.string.toast_permission));
            return;
        }
        if (mInput.isEnabled()) {
            String roomCode = mInput.getText().toString().trim();
            if (TextUtils.isEmpty(roomCode)) {
                mPresenter.createRoom();
            } else {
                mPresenter.joinRoom(roomCode);
            }
        } else {
            mPresenter.leaveRoom();
        }
    }

    @Override
    public void showProgress(int string) {
        mDialog = new AlertDialog.Builder(this)
                .setMessage(string)
                .setCancelable(false)
                .create();
        mDialog.show();
    }

    @Override
    public void dismissProgress() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    @Override
    public void showToast(String msg) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        mToast.show();
    }

    @Override
    public void showToast(int msgRes) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(this, msgRes, Toast.LENGTH_LONG);
        mToast.show();
    }

    @Override
    public void showRoomCode(String roomCode) {
        mInput.setText(roomCode);
    }

    @Override
    public void onOnline() {
        mInput.setEnabled(false);
        mSubmitButton.setText(R.string.btn_unlink);
    }

    @Override
    public void onOffline() {
        mInput.setEnabled(true);
        mInput.setText("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.destroy();
    }

    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
            return false;
        }
        return true;
    }
}
