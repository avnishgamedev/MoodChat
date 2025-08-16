package com.avnishgamedev.moodchat;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

public class App extends Application implements DefaultLifecycleObserver {

    @Override
    public void onCreate() {
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(LifecycleOwner owner) {
        Log.d("MoodChatLifeCycle", "onStart");
        UserManager.getInstance().setUserOnlineStatus(true);
    }

    @Override
    public void onStop(LifecycleOwner owner) {
        Log.d("MoodChatLifeCycle", "onStop");
        UserManager.getInstance().setUserOnlineStatus(false);
    }
}
