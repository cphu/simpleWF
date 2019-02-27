package com.sfeir.simplewf.fit;

import android.content.Context;
import android.support.annotation.Nullable;

import java.util.ArrayList;

public interface FitService {

    interface Handler<T> {
        void onResults(@Nullable T results);

        void onError(Exception e);

        void onConnectionError(String msgError);
    }

    void requestDailyBy30min(Context context, final Handler<ArrayList<FitData>> completion);

    void requestDailyTotal(Context context, final Handler<FitData> completion);
}
