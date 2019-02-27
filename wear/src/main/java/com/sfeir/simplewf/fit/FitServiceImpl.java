package com.sfeir.simplewf.fit;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.Builder;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataReadResult;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


import static com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN;

public class FitServiceImpl implements FitService {
    private static final String TAG = FitServiceImpl.class.getSimpleName();
    private static final int RETURN_CODE = 10;

    public FitServiceImpl(){}

    private static OnConnectionFailedListener getConnectionFailureListener(Context context, Handler completion) {
        return connectionResult -> {
            int errorCode = connectionResult.getErrorCode();
            boolean hasResolution = connectionResult.hasResolution();

            String msgError = "Connection Failed: [" + errorCode + "]. Has Resolution: [" + hasResolution + "]";
            Log.e(TAG, msgError);

            if ((errorCode == ConnectionResult.SIGN_IN_REQUIRED || errorCode == ConnectionResult.RESOLUTION_REQUIRED || errorCode == FitnessStatusCodes.NEEDS_OAUTH_PERMISSIONS) && hasResolution) {
                try {
                    // Option 1
                    //                    PendingIntent resolution = connectionResult.getResolution();
                    //                    resolution.send();

                    // Option 2
                    //                    Timber.e("Trying option 2...");
                    //          PendingIntent pendingIntent = connectionResult.getResolution();
                    //          context.getApplicationContext().startIntentSender(pendingIntent.getIntentSender(), null,
                    //              Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    //                  | Intent.FLAG_FROM_BACKGROUND,
                    //              Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    //                  | Intent.FLAG_FROM_BACKGROUND, 0);

                    // Option 3
                    Log.e(TAG, "Trying option 3...");
                    FitnessOptions fitnessOptions = FitnessOptions.builder().addDataType(DataType.TYPE_STEP_COUNT_DELTA).build();
                    GoogleSignInAccount signInAccount = GoogleSignIn.getAccountForExtension(context, fitnessOptions);

                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(DEFAULT_SIGN_IN).requestScopes(Fitness.SCOPE_ACTIVITY_READ_WRITE)//
                        .setAccountName(signInAccount.getEmail()).build();
                    Intent signInIntent = GoogleSignIn.getClient(context, gso).getSignInIntent();
                    context.getApplicationContext().startActivity(signInIntent);
                } catch (Exception e) {
                    msgError = "Failed to connect, no permissions. Permission resolution failed";
                    Log.e(TAG, e.getMessage());
                }
                completion.onConnectionError(msgError);
            } else {
                completion.onConnectionError(msgError);
            }
        };
    }

    private static GoogleApiClient getClient(Context context) {
        return new Builder(context).addApi(Fitness.HISTORY_API).addApi(Fitness.RECORDING_API).useDefaultAccount().build();
    }

    @Override
    public void requestDailyBy30min(Context context, Handler completion) {
        final GoogleApiClient googleApiClient = getClient(context);

        final ConnectionCallbacks connectionCallbacks = new ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle bundle) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(new Date());
                int intYear = cal.get(Calendar.YEAR);
                int intMonth = cal.get(Calendar.MONTH);
                int intDay = cal.get(Calendar.DAY_OF_MONTH);
                cal.set(intYear, intMonth, intDay, 0, 0);
                final long start = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_MONTH, 1);
                final long end = cal.getTimeInMillis();

                final ArrayList<FitData> results = new ArrayList<>();

                DataReadRequest request = new DataReadRequest.Builder().aggregate(DataType.TYPE_STEP_COUNT_DELTA,
                    DataType.AGGREGATE_STEP_COUNT_DELTA).setTimeRange(start, end, TimeUnit.MILLISECONDS).bucketByTime(30, TimeUnit.MINUTES).build();

                ResultCallback<DataReadResult> resultCallback = dataReadResult -> {
                    if (dataReadResult.getStatus().isSuccess()) {
                        long totalSteps;
                        for (Bucket bck : dataReadResult.getBuckets()) {
                            totalSteps = 0;
                            for (DataSet ds : bck.getDataSets()) {
                                for (DataPoint p : ds.getDataPoints()) {
                                    long value = p.getValue(Field.FIELD_STEPS).asInt();
                                    totalSteps += value;
                                }
                            }
                            results.add(getResult(bck, totalSteps));

                        }
                    } else {
                        Log.e(TAG, "Failed to request 30 min : " + dataReadResult.getStatus().getStatusMessage());
                    }

                    completion.onResults(results);
                    googleApiClient.disconnect();
                };

                Fitness.HistoryApi.readData(googleApiClient, request).setResultCallback(resultCallback);
            }

            @Override
            public void onConnectionSuspended(int i) {
            }
        };

        googleApiClient.registerConnectionCallbacks(connectionCallbacks);
        googleApiClient.registerConnectionFailedListener(getConnectionFailureListener(context, completion));
        googleApiClient.connect();
    }

    @Override
    public void requestDailyTotal(Context context, Handler completion) {
        final GoogleApiClient googleApiClient = getClient(context);

        final ConnectionCallbacks connectionCallbacks = new ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle bundle) {
                // Subscribe
                Fitness.RecordingApi.subscribe(googleApiClient, DataType.TYPE_STEP_COUNT_DELTA);

                ResultCallback<DailyTotalResult> resultCallback = dailyTotalResult -> {
                    if (dailyTotalResult.getStatus().isSuccess()) {
                        List<DataPoint> points = dailyTotalResult.getTotal().getDataPoints();
                        if (!points.isEmpty()) {
                            int totalSteps = points.get(0).getValue(Field.FIELD_STEPS).asInt();
                            Date now = new Date();
                            completion.onResults(new FitData(now, now, totalSteps));
                        } else {
                            completion.onResults(null);
                        }
                    } else {
                        Log.e(TAG, "Failed to request total: " + dailyTotalResult.getStatus().getStatusMessage());
                        completion.onResults(null);
                    }

                    googleApiClient.disconnect();
                };

                Fitness.HistoryApi.readDailyTotal(googleApiClient, DataType.TYPE_STEP_COUNT_DELTA).setResultCallback(resultCallback);
            }

            @Override
            public void onConnectionSuspended(int i) {
            }
        };

        googleApiClient.registerConnectionCallbacks(connectionCallbacks);
        googleApiClient.registerConnectionFailedListener(getConnectionFailureListener(context, completion));
        googleApiClient.connect();
    }

    private static FitData getResult(Bucket bck, long totalSteps) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(bck.getStartTime(TimeUnit.MILLISECONDS));
        final Date start = cal.getTime();
        cal.setTimeInMillis(bck.getEndTime(TimeUnit.MILLISECONDS));
        final Date end = cal.getTime();
        return new FitData(start, end, totalSteps);
    }
}
