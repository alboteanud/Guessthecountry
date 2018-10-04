package com.craiovadata.guessthecountry;

import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.tasks.OnCompleteListener;
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

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.craiovadata.guessthecountry.Utils.log;


public class MainActivity extends AppCompatActivity {
    private static final String SIGHTS_AND_SOUNDS_COLLECTION = "sights_and_sounds_";
    private static final String NUMBER_OF_COUNTRIES_KEY_REMOTE_CONFIG = "number_of_countries";
    static final String TAG = "MainActivity";
    private static final int CODE = 0;
    private static final int NAME = 1;

    @BindView(R.id.textFlagA)
    TextView textViewFlagA;

    @BindView(R.id.textFlagB)
    TextView textViewFlagB;

    @BindView(R.id.textViewMessageOutput)
    TextView textViewMessageOutput;

    @BindView(R.id.countryView)
    ImageView imageViewMain;

    @BindView(R.id.flagA)
    ImageView flagA;

    @BindView(R.id.flagB)
    ImageView flagB;

    @BindView(R.id.adView)
    AdView adView;

    @BindView(R.id.progressBar)
    ProgressBar progressBar;

    @BindView(R.id.btn_play_music)
    View btnSound;

    @BindView(R.id.buttonA)
    View btnA;

    @BindView(R.id.buttonB)
    View btnB;

    @BindView(R.id.fab)
    FloatingActionButton floatingActionButton;

    @BindView(R.id.btn_info)
    View btnInfo;

    private MediaPlayer mediaPlayer;
    private Uri soundUri;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;
    private TypedArray countries;
    private Random random;
    private InterstitialAd mInterstitialAd;
    private int fabClicks;

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    private ListenerRegistration docListenerRegistration;
    private int nr_docs_countries_firebase;
    private static final int DELAY_FAB_ACTIVATION = 4 * 1000;
    private Handler handler;
    private Runnable runnableActivateFab;
    private Item item;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initObjects();
        initAds();
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        signInAnonymously();

    }

    @OnClick({R.id.fab})
    public void onFabNextClicked(View view) {

        if (mInterstitialAd.isLoaded() && fabClicks++ > 3)
            mInterstitialAd.show();
        else {
            cleanUI();
//            showSplashAssets(true);
            fetchRandomDocument();
        }
    }


    private void initAds() {
        MobileAds.initialize(this, "ca-app-pub-3931793949981809~9763575423");

        AdRequest requestBanner = new AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
        adView.loadAd(requestBanner);

        mInterstitialAd = new InterstitialAd(this);
        mInterstitialAd.setAdUnitId(Utils.getAdUnitId());
        AdRequest requestInterstitial = new AdRequest.Builder().addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
        if (!BuildConfig.DEBUG)
            mInterstitialAd.loadAd(requestInterstitial);

        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                // Code to be executed when when the interstitial ad is closed.
                cleanUI();
                fetchRandomDocument();
                mInterstitialAd.loadAd(new AdRequest.Builder().build());
                fabClicks = 0;
            }
        });
    }

    private void initObjects() {
        random = new Random();
        handler = new Handler();
        runnableActivateFab = new Runnable() {
            @Override
            public void run() {
                activateFab(true);
            }
        };
        countries = getResources().obtainTypedArray(R.array.countrycodes);

        mAuth = FirebaseAuth.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        firestore = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build();
        firestore.setFirestoreSettings(settings);
        storage = FirebaseStorage.getInstance();
    }

    private void onSignInCompleted() {
//        fetchParamNumCountries
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
                        if (task.isSuccessful()) {
                            mFirebaseRemoteConfig.activateFetched();
                        } else {
                            showErrorToast(task.getException());
                        }
                        nr_docs_countries_firebase = (int) mFirebaseRemoteConfig.getLong(NUMBER_OF_COUNTRIES_KEY_REMOTE_CONFIG);
                        fetchRandomDocument();

                    }
                });

    }

    private void showProgressBar(Boolean shouldShow) {
        if (shouldShow) progressBar.setVisibility(View.VISIBLE);
        else progressBar.setVisibility(View.INVISIBLE);
    }

    private void cleanUI() {
        btnSound.setVisibility(View.INVISIBLE);
        btnInfo.setVisibility(View.INVISIBLE);

        GlideApp.with(this).clear(imageViewMain);
        GlideApp.with(this).clear(flagA);
        GlideApp.with(this).clear(flagB);

        textViewMessageOutput.setText(null);
        textViewFlagA.setText(null);
        textViewFlagB.setText(null);
        if (mediaPlayer != null)
            mediaPlayer.reset();
        activateFab(false);

    }

    private void activateFab(Boolean shouldActivate) {
        float alphaVal = shouldActivate ? 1.0f : 0.5f;
        floatingActionButton.setAlpha(alphaVal);
        floatingActionButton.setClickable(shouldActivate);
        floatingActionButton.setEnabled(shouldActivate);

    }

    private void lateActivateFab() {
        handler.removeCallbacks(runnableActivateFab);
        handler.postDelayed(runnableActivateFab, DELAY_FAB_ACTIVATION);
    }

    private void fetchRandomDocument() {

        showProgressBar(true);

        int r = random.nextInt(nr_docs_countries_firebase) + 2;
//        r=158;
        final String r_s = Integer.toString(r);
        final DocumentReference documentReference = firestore.collection(SIGHTS_AND_SOUNDS_COLLECTION).document(r_s);

//        if (docListenerRegistration != null)
//            docListenerRegistration.remove();
        docListenerRegistration = documentReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                showProgressBar(false);
                lateActivateFab();
                if (e != null) {
                    log("Listen failed. " + e);
                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    log("Current data: " + documentSnapshot.getData());

                    Item item = documentSnapshot.toObject(Item.class);
                    item.setId(documentSnapshot.getId());
                    docListenerRegistration.remove();
                    onItemLoaded(item);


                } else {
                    log("Current data: null");
                }

            }
        });

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
        final String[] wrongCountry = getRandomFakeCountry();
        final boolean coin = random.nextBoolean();
        if (coin) {
            btnA.setTag(coin);
            btnB.setTag(null);

            fetchFlag(flagA, textViewFlagA, item.getCountry_code(), item.getCountry());
            fetchFlag(flagB, textViewFlagB, wrongCountry[CODE], wrongCountry[NAME]);

        } else {
            btnA.setTag(null);
            btnB.setTag(true);

            fetchFlag(flagA, textViewFlagA, wrongCountry[CODE], wrongCountry[NAME]);
            fetchFlag(flagB, textViewFlagB, item.getCountry_code(), item.getCountry());
        }
    }

    private void fetchFlag(ImageView imageView, final TextView textView, String code, final String countryName) {
        String flagLocation = "flags_jpg/" + code + ".jpg";
        StorageReference flagRef = storage.getReference(flagLocation);

        GlideApp.with(this)
                .load(flagRef)
                .addListener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        textView.setText(countryName);
                        return false;
                    }
                })
                .into(imageView);

    }

    private void fetchMusicUrl() {
        StorageReference ref = storage.getReference(
                "sounds/" + item.getId() + "_x264.mp4");
        ref.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                soundUri = uri;
                setMediaPlayer();
            }
        });
        btnSound.setVisibility(View.VISIBLE);
    }

    private String[] getRandomFakeCountry() {
        String[] fakeCountry = null;
        while (fakeCountry == null || fakeCountry[CODE].equals(item.getCountry_code())) {
            int r = random.nextInt(countries.length());

            int id = countries.getResourceId(r, 0);
            fakeCountry = getResources().getStringArray(id);
        }
        log("fake country: " + fakeCountry[CODE] + " " + fakeCountry[NAME] + " " + random.toString());
        return fakeCountry;
    }

    private void fetchImage() {
        StorageReference imgRef = item.getImgRef(storage);
        StorageReference thumbRef = item.getThumbRef(storage);

        GlideApp.with(this)
                .load(imgRef)
                .addListener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        updateButtons();
                        return false;
                    }
                })
                .thumbnail(GlideApp.with(this).load(thumbRef))
//                .transition(DrawableTransitionOptions.withCrossFade())
//                .placeholder(R.drawable.ic_launcher_countries)
                .into(imageViewMain);


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

    @OnClick({R.id.buttonA, R.id.buttonB})
    public void onFlagClick(View v) {
        Object tag = v.getTag();
        String outputMessage;
        if (tag == null) {
            outputMessage = String.format(getString(R.string.output_message_wrong_answer), item.getCountry());
        } else {
            outputMessage = String.format(getString(R.string.output_message_correct_answer), item.getCountry(), item.getImg_title());
            btnInfo.setVisibility(View.VISIBLE);
        }
        textViewMessageOutput.setText(outputMessage);

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
                            onSignInCompleted();
                        } else {
                            // If sign in fails, display a message to the user.
                            log("signInAnonymously:failure " + task.getException());

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

    @OnClick(R.id.btn_play_music)
    public void onSoundClicked(View view) {

        if (mediaPlayer == null) {
            setMediaPlayer();
        } else if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
//            mediaPlayer.seekTo(0);
        } else {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                releaseMediaPlayer();
            }
        }
    }

    private void onItemLoaded(Item item) {
        this.item = item;
        fetchImage();
        fetchMusicUrl();
    }

    @OnClick(R.id.btn_info)
    public void onInfoClicked(View view) {
        if (item != null) {
            InfoDialogFragment infoDialogFragment = new InfoDialogFragment();
            infoDialogFragment.setItem(item);
            infoDialogFragment.show(getSupportFragmentManager(), InfoDialogFragment.TAG);
        }
    }


}
