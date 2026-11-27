package com.vocamind.app.feature.personal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class StudyReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (StudyReminder.FIRE.equals(action)) {
            StudyReminder.schedule(context);
            StudyReminder.notifyIfEnabled(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            StudyReminder.schedule(context);
        }
    }
}
