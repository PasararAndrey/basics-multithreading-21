package com.artemchep.basics_multithreading;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.artemchep.basics_multithreading.cipher.CipherUtil;
import com.artemchep.basics_multithreading.domain.Message;
import com.artemchep.basics_multithreading.domain.WithMillis;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {

    private final List<WithMillis<Message>> mList = new ArrayList<>();

    private final Queue<WithMillis<Message>> messageQueue = new LinkedList<>();
    private final MessageAdapter mAdapter = new MessageAdapter(mList);

    Thread messageProcessingThread;

    private final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startMessageProcessingThread();
        final RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter);
        showWelcomeDialog();

    }

    private void startMessageProcessingThread() {
        messageProcessingThread = new Thread(
                () -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        WithMillis<Message> message;
                        synchronized (messageQueue) {
                            while (messageQueue.isEmpty()) {
                                Log.d(TAG, "startMessageProcessingThread: No messages");
                                try {
                                    messageQueue.wait();
                                } catch (InterruptedException e) {
                                    Log.d(TAG, "startMessageProcessingThread: was interrupted");
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                            }
                            message = messageQueue.poll();
                        }
                        Log.d(TAG, "startMessageProcessingThread: " + System.currentTimeMillis() + " - " + message.elapsedMillis + " = " + (System.currentTimeMillis() - message.elapsedMillis));
                        String encryptedText = CipherUtil.encrypt(message.value.plainText);
                        final Message messageNew = message.value.copy(encryptedText);
                        final WithMillis<Message> messageWithMillis = new WithMillis<>(messageNew, System.currentTimeMillis() - message.elapsedMillis);
                        runOnUiThread(() -> {
                            update(messageWithMillis);
                        });
                    }
                }
        );
        messageProcessingThread.start();
    }

    private void showWelcomeDialog() {
        new AlertDialog.Builder(this)
                .setMessage("What are you going to need for this task: Thread, Handler.\n" +
                        "\n" +
                        "1. The main thread should never be blocked.\n" +
                        "2. Messages should be processed sequentially.\n" +
                        "3. The elapsed time SHOULD include the time message spent in the queue.")
                .show();
    }

    public void onPushBtnClick(View view) {
            Message message = Message.generate();
        insert(new WithMillis<>(message,System.currentTimeMillis()));
    }

    public void insert(final WithMillis<Message> message) {
        mList.add(message);
        mAdapter.notifyItemInserted(mList.size() - 1);
        synchronized (messageQueue) {
            messageQueue.add(message);
            messageQueue.notify();
        }
    }

    @UiThread
    public void update(final WithMillis<Message> message) {
        for (int i = 0; i < mList.size(); i++) {
            if (mList.get(i).value.key.equals(message.value.key)) {
                mList.set(i, message);
                mAdapter.notifyItemChanged(i);
                return;
            }
        }
        throw new IllegalStateException();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        messageProcessingThread.interrupt();
    }
}
