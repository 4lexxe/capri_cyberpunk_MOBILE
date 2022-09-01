package com.capri.companion;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Collections;

/** Test-process service with no dependency on target APK classes or Kotlin runtime. */
public final class FakeRecognitionService extends RecognitionService {
    private int turns;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Override protected void onStartListening(Intent intent, Callback listener) {
        try { listener.readyForSpeech(new Bundle()); } catch (RemoteException ignored) { return; }
        if (++turns == 1) handler.postDelayed(() -> {
            Bundle result = new Bundle();
            result.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,
                new ArrayList<>(Collections.singletonList("Hola Capri desde el flotante")));
            try { listener.results(result); } catch (RemoteException ignored) { }
        }, 700);
    }
    @Override protected void onCancel(Callback listener) { handler.removeCallbacksAndMessages(null); }
    @Override protected void onStopListening(Callback listener) { }
}
