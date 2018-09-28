package com.craiovadata.guessthecountry;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String SIGHTS_AND_SOUNDS_COLLECTION = "sights_and_sounds_";
    private static final String TAG = "MainActivity";
    private Button buttonA, buttonB;
    private ImageView imageView;
    private MediaPlayer mediaPlayer;
    private Uri soundUri;
    private TextView textViewMessageOutput;
    DocumentSnapshot document;
    FloatingActionButton fab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        buttonA = findViewById(R.id.buttonA);
        buttonB = findViewById(R.id.buttonB);
        buttonA.setOnClickListener(this);
        buttonB.setOnClickListener(this);
        imageView = findViewById(R.id.imageView);
        textViewMessageOutput = findViewById(R.id.textView);

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cleanUI();
                getRandomDocument();

                fab.setEnabled(false);
                fab.setClickable(false);
                fab.setAlpha(0.3f);
            }
        });

        getRandomDocument();
    }

    private void cleanUI() {

        buttonA.setVisibility(View.INVISIBLE);
        buttonB.setVisibility(View.INVISIBLE);

        textViewMessageOutput.setText(null);

        imageView.setVisibility(View.INVISIBLE);

        if (mediaPlayer!=null)
            mediaPlayer.reset();
    }

    void getRandomDocument() {
        final int r = new Random().nextInt(319) + 2;
        FirebaseFirestore.getInstance().collection(SIGHTS_AND_SOUNDS_COLLECTION)
                .document(Integer.toString(r)).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    document = task.getResult();
                    if (document.exists()) {
                        Log.d(TAG, "DocumentSnapshot: " + document);
                        updateUI(document);
                        downloadMusicUrl(document.getId());
                    } else {
                        Log.d(TAG, "No such document");
                    }
                } else {
                    Log.d(TAG, "get failed with ", task.getException());
                }
            }
        });

    }

    private void downloadMusicUrl(String id) {
        StorageReference ref = FirebaseStorage.getInstance().getReference("sounds/" + id + "_x264.mp4");
        ref.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                soundUri = uri;
                setMediaPlayer();
            }
        });
    }

    private void updateUI(DocumentSnapshot doc) {

        downloadImage(doc.getId());

        // updating buttons
        String country = (String) doc.get("country");
        String fakeCountry = "Romania";

        boolean coin = new Random().nextBoolean();

        if (coin){
            buttonA.setTag(true);
            buttonA.setText(country);
            buttonB.setTag(false);
            buttonB.setText(fakeCountry);
        } else {
            buttonA.setTag(false);
            buttonA.setText(fakeCountry);
            buttonB.setTag(true);
            buttonB.setText(country);
        }


    }

    private void downloadImage(String id) {
        StorageReference imgRef = FirebaseStorage.getInstance().getReference("images/" + id + ".jpg");

        final long ONE_MEGABYTE = 1024 * 1024;
        imgRef.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "images/island.jpg" is returns, use this as needed
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(View.VISIBLE);

                buttonA.setVisibility(View.VISIBLE);
                buttonB.setVisibility(View.VISIBLE);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle any errors
            }
        });

    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseMediaPlayer();
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void setMediaPlayer() {
        if (soundUri == null) return;
        if (mediaPlayer == null)
            mediaPlayer = new MediaPlayer();
        else
            mediaPlayer.reset();

        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        mediaPlayer.setScreenOnWhilePlaying(true);
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.seekTo(0);
            }
        });
        mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                releaseMediaPlayer();
                return true;
            }
        });
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                    mediaPlayer.start();
            }
        });
        try {
            mediaPlayer.setDataSource(this, soundUri);
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            releaseMediaPlayer();
        }
    }

    @Override
    public void onClick(View v) {
        boolean tag = (boolean) v.getTag();
        String country = (String)document.get("country");
        if (tag){
            String textImage = (String)document.get("img_title");
            textViewMessageOutput.setText( country + " is correct!\n" +
                    textImage);
        } else {
            textViewMessageOutput.setText("Nope! The correct answer is " + country);
        }

        fab.setEnabled(true);
        fab.setClickable(true);
        fab.setAlpha(1.0f);


    }
}
