package com.craiovadata.guessthecountry;

import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;
import java.util.Random;

import javax.annotation.Nullable;

import static com.craiovadata.guessthecountry.Utils.log;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String SIGHTS_AND_SOUNDS_COLLECTION = "sights_and_sounds_";
    private static final String NUMBER_OF_COUNTRIES_KEY_REMOTE_CONFIG = "number_of_countries";
    private static final String COUNTRY_CODE_KEY = "country_code";
    private static final String COUNTRY_KEY = "country";
    static final String TAG = "MainActivity";
    private static final int CODE = 0;
    private static final int NAME = 1;
    private View buttonA, buttonB;
    private TextView textViewFlagA, textViewFlagB, textViewMessageOutput, textViewLoading;
    private ImageView imageViewMain, flagA, flagB;
    private MediaPlayer mediaPlayer;
    private Uri soundUri;
    private DocumentSnapshot documentCountry;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;
    private TypedArray countries;
    private Random random;
    private InterstitialAd mInterstitialAd;
    private int fabClicks;
    private AdView adView;
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        initViews();
        buttonA.setOnClickListener(this);
        buttonB.setOnClickListener(this);

        countries = getResources().obtainTypedArray(R.array.countrycodes);

        initFirebaseStuff();
        initAds();

    }

    private void initFirebaseStuff() {
        mAuth = FirebaseAuth.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build();
        firestore.setFirestoreSettings(settings);
    }

    private void initViews() {
        buttonA = findViewById(R.id.buttonA);
        buttonB = findViewById(R.id.buttonB);
        flagA = findViewById(R.id.flagA);
        flagB = findViewById(R.id.flagB);
        textViewFlagA = findViewById(R.id.textFlagA);
        textViewFlagB = findViewById(R.id.textFlagB);
        textViewLoading = findViewById(R.id.textViewLoading);
        imageViewMain = findViewById(R.id.countryView);
        textViewMessageOutput = findViewById(R.id.textViewMessageOutput);
        adView = findViewById(R.id.adView);
        progressBar = findViewById(R.id.progressBar);
        random = new Random();
    }

    private void initAds() {
        MobileAds.initialize(this, "ca-app-pub-3931793949981809~9763575423");

        AdRequest request = new AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
        adView.loadAd(request);

        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId(Utils.getAdUnitId(random));
        AdRequest adRequest = new AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
        mInterstitialAd.loadAd(adRequest);

        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                // Code to be executed when when the interstitial ad is closed.
                initNewCountry();
                mInterstitialAd.loadAd(new AdRequest.Builder().build());
                fabClicks = 0;
            }
        });
    }

    private void initNewCountry() {
        showProgressBar();
//        fetchParamNumCountries()
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);
        mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_defaults);
//        final long cacheExpiration = 200;
        mFirebaseRemoteConfig.fetch()
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        hideProgressBar();
                        if (task.isSuccessful()) {
                            mFirebaseRemoteConfig.activateFetched();
                        } else {
                            showErrorToast(task.getException());
                        }
                        int num_countries = (int) mFirebaseRemoteConfig.getLong(NUMBER_OF_COUNTRIES_KEY_REMOTE_CONFIG);
                        fetchRandomCountryDocument(num_countries);

                    }
                });
//        .addOnFailureListener(new OnFailureListener() {
//            @Override
//            public void onFailure(@NonNull Exception e) {
//                showErrorToast(e);
//            }
//        });
    }

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
        textViewLoading.setText(getString(R.string.loading_text));
    }

    private void hideProgressBar() {
        progressBar.setVisibility(View.GONE);
        textViewLoading.setText(null);
    }

    private void cleanUI() {
        imageViewMain.setImageDrawable(null);
        flagA.setImageDrawable(null);
        flagB.setImageDrawable(null);
        textViewMessageOutput.setText(null);
        textViewFlagA.setText(null);
        textViewFlagB.setText(null);
        if (mediaPlayer != null)
            mediaPlayer.reset();
    }

    private void fetchRandomCountryDocument(int num_countries) {
        int r = random.nextInt(num_countries) + 2;
//        r = 125;
        final DocumentReference documentReference = firestore.collection(SIGHTS_AND_SOUNDS_COLLECTION)
                .document(Integer.toString(r));


//        ListenerRegistration fr = documentReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
//            @Override
//            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
//
//            }
//        });
//
//        fr.remove();
        TODO - replace with .addSnapshotListener

        Task<DocumentSnapshot> fe = documentReference.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (task.isSuccessful()) {
                    DocumentSnapshot result = task.getResult();
                    if (result.exists()) {
                        documentCountry = result;
                        final String imgLocation = "images/" + documentCountry.getId() + ".jpg";
                        fetchImage(imgLocation, imageViewMain, true);

                        log("country " + documentCountry.getString(COUNTRY_CODE_KEY) + " " + documentCountry.getString(COUNTRY_KEY) + " " + documentCountry.getId() + " img_loc: " + imgLocation);


                    } else {
                        log("No such documentCountry");

                    }
                } else {
//                    showErrorToast(task.getException());
                }

            }
        });
//                .addOnFailureListener(new OnFailureListener() {
//                    @Override
//                    public void onFailure(@NonNull Exception e) {
//                        showErrorToast(e);
//                    }
//                });



    }

    private void showErrorToast(Exception exception) {
        log("get failed with " + exception);

        FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) exception;
        if (firestoreException != null) {
            FirebaseFirestoreException.Code errorCode = firestoreException.getCode();
            String errorMessage = firestoreException.getMessage();
            // test the errorCode and errorMessage, and handle accordingly

            Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateButtons() {
        String countryCode = documentCountry.getString(COUNTRY_CODE_KEY);
        String countryName = documentCountry.getString(COUNTRY_KEY);
        String locFlag_true = "flags_jpg/" + countryCode + ".jpg";


        final String[] wrongCountry = getRandomFakeCountry(countryCode);
        String locFlag_false = "flags_jpg/" + wrongCountry[CODE] + ".jpg";
//       locFlag_false = "flags_jpg/NO.jpg";

        final boolean coin = random.nextBoolean();
        if (coin) {
            buttonA.setTag(1);
            buttonB.setTag(null);

            textViewFlagA.setText(countryName);
            textViewFlagB.setText(wrongCountry[NAME]);

            fetchImage(locFlag_true, flagA, false);
            fetchImage(locFlag_false, flagB, false);
        } else {
            buttonA.setTag(null);
            buttonB.setTag(1);

            textViewFlagA.setText(wrongCountry[NAME]);
            textViewFlagB.setText(countryName);

            fetchImage(locFlag_false, flagA, false);
            fetchImage(locFlag_true, flagB, false);
        }
    }

    private void fetchMusic() {
        StorageReference ref = storage.getReference(
                "sounds/" + documentCountry.getId() + "_x264.mp4");
        ref.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                soundUri = uri;
                setMediaPlayer();
            }
        });
    }

    private String[] getRandomFakeCountry(String correct_answer_country_code) {
        String[] fakeCountry = null;
        while (fakeCountry == null || fakeCountry[CODE].equals(correct_answer_country_code)) {
            int r = random.nextInt(countries.length());

            int id = countries.getResourceId(r, 0);
            fakeCountry = getResources().getStringArray(id);
        }
        log("fake country: " + fakeCountry[CODE] + " " + fakeCountry[NAME] + " " + random.toString());
        return fakeCountry;
    }

    private void fetchImage(String location, final ImageView imageView, final boolean isMainImage) {
        StorageReference imgRef = storage.getReference(location);

        final long ONE_MEGABYTE = 1024 * 1024;
        imgRef.getBytes(ONE_MEGABYTE).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                // Data for "images/island.jpg" is returns, use this as needed
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                imageView.setImageBitmap(bitmap);
                if (isMainImage) {
                    updateButtons();
                    fetchMusic();
                }

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
        // flags clicks
        Object tag = v.getTag();
        String country = documentCountry.getString(COUNTRY_KEY);
        if (tag == null) {
            textViewMessageOutput.setText(String.format(getString(R.string.output_message_wrong_answer), country));
        } else {
            String txtInfo = (String) documentCountry.get("img_title");
            textViewMessageOutput.setText(String.format(getString(R.string.output_message_correct_answer), country, txtInfo));

        }


    }

    @Override
    protected void onStart() {
        super.onStart();
        signInAnonymously();
    }

    private void signInAnonymously() {

        mAuth.signInAnonymously()
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            log("signInAnonymously:success");
                            FirebaseUser user = mAuth.getCurrentUser();
//                            updateUI(user);
                            initNewCountry();
                        } else {
                            // If sign in fails, display a message to the user.
                            log("signInAnonymously:failure " + task.getException());
//                            Toast.makeText(AnonymousAuthActivity.this, "Authentication failed.",
//                                    Toast.LENGTH_SHORT).show();
//                            updateUI(null);
                        }

                        // ...
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying())
            mediaPlayer.pause();
        if (adView != null)
            adView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adView != null)
            adView.resume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.menu_next) {

            cleanUI();
            if (mInterstitialAd.isLoaded() && fabClicks++ > 4)
                mInterstitialAd.show();
            else {
                initNewCountry();
            }

            return true;
        } else if (id == R.id.menu_sound) {
            onSoundClicked();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void onSoundClicked() {
        if (mediaPlayer == null)
            setMediaPlayer();
        else if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            mediaPlayer.seekTo(0);
        } else {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                releaseMediaPlayer();
            }
        }
    }


}
