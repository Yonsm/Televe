package net.yonsm.televe;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import internal.playlist.Channel;
import internal.tv.TvChannelPublisher;

public class TvService extends IntentService {
    public TvService() {
        super("TvService");
    }

    public static void start(Context context) {
        context.startService(new Intent(context, TvService.class));
    }

    public static void startPublish(Context context, Channel channel) {
        if (channel != null) {
            context.startService(new Intent(context, TvService.class)
                    .putExtra(Channel.EXTRA_CHANNEL, channel));
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) return;
        try {
            TvChannelPublisher publisher = new TvChannelPublisher(this);
            Channel channel = intent.getParcelableExtra(Channel.EXTRA_CHANNEL);
            if (channel != null) publisher.publish(channel);
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) publisher.createChannel();
        } catch (Exception error) {
            Log.w("TvService", "TV recommendations unavailable", error);
        }
    }
}
