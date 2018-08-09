package com.adafruit.bluefruit.le.connect.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.adafruit.bluefruit.le.connect.R;

/**
 * Created by auste on 11/28/2017.
 */

public class CShowProgress extends Activity {
    public static CShowProgress mCShowProgress;
    public Dialog mDialog;

    public CShowProgress() {

    }

    public static CShowProgress getInstance() {
        if (mCShowProgress == null) {
            mCShowProgress = new CShowProgress();
        }
        return mCShowProgress;
    }

    public void showProgress(Context mContext) {
        mDialog = new Dialog(mContext);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(R.layout.calibration);
        mDialog.findViewById(R.id.pb).setVisibility(View.VISIBLE);
        mDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        mDialog.setCancelable(true);
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        mDialog.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        mDialog.show();

    }

    public void hideProgress() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }
}