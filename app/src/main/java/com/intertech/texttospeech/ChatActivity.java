package com.intertech.texttospeech;

import android.content.Context;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.http.ServiceCall;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.assistant.v2.Assistant;
import com.ibm.watson.assistant.v2.model.CreateSessionOptions;
import com.ibm.watson.assistant.v2.model.MessageInput;
import com.ibm.watson.assistant.v2.model.MessageOptions;
import com.ibm.watson.assistant.v2.model.MessageResponse;
import com.ibm.watson.assistant.v2.model.RuntimeResponseGeneric;
import com.ibm.watson.assistant.v2.model.SessionResponse;
import com.intertech.texttospeech.adapters.ChatAdapter;
import com.intertech.texttospeech.databinding.ActivityChatBinding;
import com.intertech.texttospeech.models.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    ChatAdapter mAdapter;
    ArrayList<Message> messageArrayList;
    EditText inputMessage;
    ImageButton btnSend;
    boolean initialRequest;
    Context mContext;

    Assistant watsonAssistant;
    Response<SessionResponse> watsonAssistantSession;

    ActivityChatBinding binding;
    TextToSpeech speech;

    private void createServices() {
        watsonAssistant = new Assistant("2019-02-28",
                new IamAuthenticator(mContext.getString(R.string.assistant_apikey)));
        watsonAssistant.setServiceUrl(mContext.getString(R.string.assistant_url));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(R.layout.activity_chat);

        speech = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                speech.setLanguage(Locale.UK);
            }
        });

        mContext = getApplicationContext();

        inputMessage = findViewById(R.id.message);
        btnSend = findViewById(R.id.btn_send);
        recyclerView = findViewById(R.id.recycler_view);

        messageArrayList = new ArrayList<>();
        mAdapter = new ChatAdapter(messageArrayList, message -> {
            speech.speak(message, TextToSpeech.QUEUE_FLUSH, null , null);
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);
        this.inputMessage.setText("");
        this.initialRequest = true;


        btnSend.setOnClickListener(v -> {
            if (checkInternetConnection()) {
                sendMessage();
            }
        });

        createServices();
        sendMessage();
    }

    private void sendMessage() {
        final String inputmessage = this.inputMessage.getText().toString().trim();
        if (!this.initialRequest) {
            Message inputMessage = new Message();
            inputMessage.setMessage(inputmessage);
            inputMessage.setId("1");
            messageArrayList.add(inputMessage);
        } else {
            Message inputMessage = new Message();
            inputMessage.setMessage(inputmessage);
            inputMessage.setId("100");
            this.initialRequest = false;
            Toast.makeText(getApplicationContext(), "Tap on the message for Voice", Toast.LENGTH_LONG).show();
        }

        this.inputMessage.setText("");
        mAdapter.notifyDataSetChanged();
        Thread thread = new Thread(() -> {
            try {
                if (watsonAssistantSession == null) {
                    ServiceCall<SessionResponse> call = watsonAssistant.createSession(new CreateSessionOptions.Builder().assistantId(mContext.getString(R.string.assistant_id)).build());
                    watsonAssistantSession = call.execute();
                }
                MessageInput input = new MessageInput.Builder()
                        .text(inputmessage)
                        .build();
                MessageOptions options = new MessageOptions.Builder()
                        .assistantId(mContext.getString(R.string.assistant_id))
                        .input(input)
                        .sessionId(watsonAssistantSession.getResult().getSessionId())
                        .build();
                Response<MessageResponse> response = watsonAssistant.message(options).execute();
                Log.i("response", "run: " + response.getResult());
                if (response != null &&
                        response.getResult().getOutput() != null &&
                        !response.getResult().getOutput().getGeneric().isEmpty()) {
                    List<RuntimeResponseGeneric> responses = response.getResult().getOutput().getGeneric();
                    for (RuntimeResponseGeneric r : responses) {
                        Message outMessage;
                        if ("text".equals(r.responseType())) {
                            outMessage = new Message();
                            outMessage.setMessage(r.text());
                            outMessage.setId("2");
                            messageArrayList.add(outMessage);
                        } else {
                            Log.e("Error", "Unhandled message type");
                        }
                    }

                    runOnUiThread(() -> {
                        mAdapter.notifyDataSetChanged();
                        if (mAdapter.getItemCount() > 1) {
                            recyclerView.getLayoutManager()
                                    .smoothScrollToPosition(recyclerView, null, mAdapter.getItemCount() - 1);

                        }

                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        thread.start();
    }

    private boolean checkInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        if (isConnected) {
            return true;
        } else {
            Toast.makeText(this, " No Internet Connection available ", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public void onPause() {
        if (speech != null) {
            speech.stop();
            speech.shutdown();
        }
        super.onPause();
    }

}
