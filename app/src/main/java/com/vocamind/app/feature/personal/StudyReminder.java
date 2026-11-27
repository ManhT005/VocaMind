package com.vocamind.app.feature.personal;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.vocamind.app.MainActivity;
import com.vocamind.app.R;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/** A recurring local reminder without exact-alarm access. */
final class StudyReminder {
    static final String FIRE = "com.vocamind.app.action.STUDY_REMINDER";
    private static final String CHANNEL = "daily_study";
    private static final int NOTIFICATION = 241;
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private StudyReminder() {}

    static boolean enabled(Context context) {
        return PersonalPreferences.open(context).getBoolean("study_reminder", false);
    }

    static LocalTime time(Context context) {
        return LocalTime.ofSecondOfDay(PersonalPreferences.open(context).getInt("study_reminder_minute", 20 * 60) * 60L);
    }

    static void configure(Context context, boolean enabled, LocalTime time) {
        PersonalPreferences.open(context).edit().putBoolean("study_reminder", enabled)
                .putInt("study_reminder_minute", time.getHour() * 60 + time.getMinute()).apply();
        schedule(context);
        if (!enabled) context.getSystemService(NotificationManager.class).cancel(NOTIFICATION);
    }

    static void schedule(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent reminder = PendingIntent.getBroadcast(context, 0,
                new Intent(context, StudyReminderReceiver.class).setAction(FIRE), FLAGS);
        if (!enabled(context)) {
            alarms.cancel(reminder);
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        LocalTime time = time(context);
        ZonedDateTime next = now.toLocalDate().atTime(time).atZone(now.getZone());
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(time).atZone(now.getZone());
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), reminder);
    }

    static boolean hasPermission(Context context) {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean canNotify(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = notifications.getNotificationChannel(CHANNEL);
        return hasPermission(context) && notifications.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    static void notifyIfEnabled(Context context) {
        if (!enabled(context) || !canNotify(context)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                context.getString(R.string.reminder_title), NotificationManager.IMPORTANCE_DEFAULT));
        Intent openStudy = new Intent(context, MainActivity.class).setAction(MainActivity.ACTION_OPEN_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 0, openStudy, FLAGS);
        try {
            manager.notify(NOTIFICATION, new NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_ui_learn)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.reminder_notification))
                .setContentIntent(open).setAutoCancel(true).build());
        } catch (SecurityException permissionChanged) {
            // The user may revoke notification permission between the check and posting.
        }
    }
}
