package com.zhang.autotouch.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import androidx.annotation.RequiresApi;

import com.zhang.autotouch.MainActivity;
import com.zhang.autotouch.R;
import com.zhang.autotouch.TouchEventManager;
import com.zhang.autotouch.bean.TouchEvent;
import com.zhang.autotouch.bean.TouchPoint;
import com.zhang.autotouch.utils.DensityUtil;
import com.zhang.autotouch.utils.SpUtils;
import com.zhang.autotouch.utils.WindowUtils;

import java.text.DecimalFormat;
import java.util.List;

/**
 * 无障碍服务-自动点击
 *
 * @date 2019/9/6 16:23
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class AutoTouchService extends AccessibilityService {

    private final String TAG = "AutoTouchService+++";
    //自动点击事件
    private TouchPoint autoTouchPoint;
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private TextView tvTouchPoint;
    //倒计时
    private float countDownTime;
    private DecimalFormat floatDf = new DecimalFormat("#0.0");
    //修改点击文本的倒计时
    private Runnable touchViewRunnable;
    private List<TouchPoint> allListTouchEvent;
    private int currentPoint = 0;
    private int currentPlayType;//dang qian bo fang lei xing

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        handler = new Handler();
        EventBus.getDefault().register(this);
        windowManager = WindowUtils.getWindowManager(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onReciverTouchEvent(TouchEvent event) {
        initAllAction();
        Log.d(TAG, "onReciverTouchEvent: " + event.toString());
        TouchEventManager.getInstance().setTouchAction(event.getAction());
        handler.removeCallbacks(autoTouchRunnable);
        currentPlayType = event.getAction();
        switch (event.getAction()) {
            case TouchEvent.ACTION_START:
                autoTouchPoint = event.getTouchPoint();
                onAutoClick();
                break;
            case TouchEvent.ACTION_CONTINUE:
                if (autoTouchPoint != null) {
                    onAutoClick();
                }
                break;
            case TouchEvent.ACTION_PAUSE:
                handler.removeCallbacks(autoTouchRunnable);
                handler.removeCallbacks(touchViewRunnable);
                break;
            case TouchEvent.ACTION_STOP:
                handler.removeCallbacks(autoTouchRunnable);
                handler.removeCallbacks(touchViewRunnable);
                removeTouchView();
                autoTouchPoint = null;
                break;
            case TouchEvent.ACTION_STARTALL:
            case TouchEvent.ACTION_CIRCLE:
                autoTouchPoint = null;
                allListTouchEvent = SpUtils.getTouchPoints(MainActivity.mContext);
                if (allListTouchEvent != null && allListTouchEvent.size() > 0) {
                    autoTouchPoint = allListTouchEvent.get(0);
                    currentPoint = 0;
                    onAutoClick();
                }
                break;
        }
    }

    private void initAllAction() {
        allListTouchEvent = null;
        currentPoint = 0;
    }

    private Runnable autoTouchRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "onAutoClick: " + "x=" + autoTouchPoint.getX() + " y=" + autoTouchPoint.getY());
            Path path = new Path();
            path.moveTo(autoTouchPoint.getX(), autoTouchPoint.getY());
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription gestureDescription = builder.addStroke(
                            new GestureDescription.StrokeDescription(path, 0, 100))
                    .build();
            dispatchGesture(gestureDescription, new AccessibilityService.GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d("AutoTouchService", "滑动结束" + gestureDescription.getStrokeCount());
                    if (currentPlayType == TouchEvent.ACTION_STARTALL || currentPlayType==TouchEvent.ACTION_CIRCLE) {
                        onAutoClick();
                    }
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.d("AutoTouchService", "滑动取消");
                }
            }, null);
            autoTouchPoint = null;
        }
    };

    private long getDelayTime() {
//        int random = (int) (Math.random() * (30 - 1) + 1);
//        return autoTouchEvent.getDelay() * 1000L + random;
        return autoTouchPoint.getDelay() * 1000L;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        removeTouchView();
    }

    /**
     * 显示倒计时
     */
    private void showTouchView() {
        if (autoTouchPoint != null) {
            //创建触摸点View
            if (tvTouchPoint == null) {
                tvTouchPoint = (TextView) LayoutInflater.from(this).inflate(R.layout.window_touch_point, null);
            }
            //显示触摸点View
            if (windowManager != null && !tvTouchPoint.isAttachedToWindow()) {
                int width = DensityUtil.dip2px(this, 40);
                int height = DensityUtil.dip2px(this, 40);
                WindowManager.LayoutParams params = WindowUtils.newWmParams(width, height);
                params.gravity = Gravity.START | Gravity.TOP;
                params.x = autoTouchPoint.getX() - width / 2;
                params.y = autoTouchPoint.getY() - width;
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.addView(tvTouchPoint, params);
            }
            //开启倒计时
            countDownTime = autoTouchPoint.getDelay();
            if (touchViewRunnable == null) {
                touchViewRunnable = new Runnable() {
                    @Override
                    public void run() {
                        handler.removeCallbacks(touchViewRunnable);
                        Log.d("触摸倒计时", countDownTime + "");
                        if (countDownTime > 0) {
                            float offset = 1f;
                            tvTouchPoint.setText(floatDf.format(countDownTime));
                            countDownTime -= offset;
                            handler.postDelayed(touchViewRunnable, 1000);
                        } else {
                            autoTouchRunnable.run();
                         //   handler.postDelayed(autoTouchRunnable, /*autoTouchPoint.getDelay() * 1000*/0);
                            removeTouchView();
                        }
                    }
                };
            }
            handler.post(touchViewRunnable);
        }
    }

    private void removeTouchView() {
        if (windowManager != null && tvTouchPoint.isAttachedToWindow()) {
            windowManager.removeView(tvTouchPoint);
        }
    }

    /**
     * 执行自动点击
     */
    private void onAutoClick() {
        if (currentPlayType == TouchEvent.ACTION_STARTALL || currentPlayType == TouchEvent.ACTION_CIRCLE) {
            if (allListTouchEvent != null && allListTouchEvent.size() > 0 && currentPoint < allListTouchEvent.size()) {
                autoTouchPoint = allListTouchEvent.get(currentPoint);
                currentPoint++;
            } else {
                if (currentPlayType == TouchEvent.ACTION_CIRCLE) {
                    currentPoint = 0;
                    onAutoClick();
                }
                return;
            }
        } else {
            if (autoTouchPoint == null) {
                return;
            }
        }
        showTouchView();
    }

    // 执行往控件添加数据的操作
    private void traverseTreeView() {
        AccessibilityNodeInfo rootInfo = getRootInActiveWindow();
        if (rootInfo == null) {
            return;
        }
        // 开始遍历目标节点
        if (rootInfo.getChildCount() != 0) {
            if (rootInfo == null || TextUtils.isEmpty(rootInfo.getClassName())) {
                return;
            }
            findByID(rootInfo, "com.tencent.mm:id/b4a");
        }
    }

    private void findByID(AccessibilityNodeInfo rootInfo, String text) {
        if (rootInfo.getChildCount() > 0) {
            for (int i = 0; i < rootInfo.getChildCount(); i++) {
                AccessibilityNodeInfo child = rootInfo.getChild(i);
                try {
                    if (child.findAccessibilityNodeInfosByViewId(text).size() > 0) {
                        for (AccessibilityNodeInfo info : child.findAccessibilityNodeInfosByViewId(text)) {
                            changeInput(info, "dsadas");
                        }
                    }
                } catch (NullPointerException e) {
                }
                findByID(child, text);//递归一直找一层层的全部遍历
            }
        }
    }


    private void changeInput(AccessibilityNodeInfo info, String text) {  //改变editText的内容
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text);
        info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

}
