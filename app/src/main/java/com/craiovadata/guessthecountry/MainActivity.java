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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.GenericTransitionOptions;
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
    static final String TAG = "MainActivity";
    private static final String SIGHTS_AND_SOUNDS_COLLECTION = "sights_and_sounds_";
    private static final String NUMBER_OF_COUNTRIES_KEY_REMOTE_CONFIG = "number_of_countries";
    private static final int CODE = 0;
    private static final int NAME = 1;
    private static final int DELAY_FAB_ACTIVATION = 4 * 1000;
    @BindView(R.id.textFlagA)
    TextView textFlagA;
    @BindView(R.id.textFlagB)
    TextView textFlagB;
    @BindView(R.id.textViewMessageOutput)
    TextView outputMessage;
    @BindView(R.id.mainImageView)
    ImageView mainImage;
    @BindView(R.id.flagA)
    ImageButton flagA;
    @BindView(R.id.flagB)
    ImageButton flagB;
    @BindView(R.id.adView)
    AdView adView;
    @BindView(R.id.progressBar)
    ProgressBar progressBar;
    @BindView(R.id.btn_play_music)
    View btnSound;
    @BindView(R.id.fab)
    FloatingActionButton fab;
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
    private int param_nr_docs = 319;
    private Handler handler;
    private Runnable runnableActivateFab;
    private Item item;
    private boolean coin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_main);
        ButterKnife.bind(this);
        initObjects();
        signInAnonymously();
    }

    @OnClick({R.id.fab})
    public void onFabNextClicked(View view) {
        if (mInterstitialAd != null && mInterstitialAd.isLoaded() && fabClicks++ > 4 + random.nextInt(4)) {
            mInterstitialAd.show(); // interstitial will not load in debug mode
        } else {
            showWellcomeUI();
            fetchRandomDocument();
        }
    }

    private void initAds() {
        if (mInterstitialAd != null) return;

        MobileAds.initialize(MainActivity.this, getString(R.string.admob_app_id));

        // interstitial
        mInterstitialAd = new InterstitialAd(MainActivity.this);
        mInterstitialAd.setAdUnitId(Utils.getAdUnitId_interstitial(random));
        AdRequest requestInterstitial = new AdRequest.Builder()
                .tagForChildDirectedTreatment(true)
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
//        if (!BuildConfig.DEBUG)
        mInterstitialAd.loadAd(requestInterstitial);

        mInterstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                // Code to be executed when the interstitial ad is closed.
                showWellcomeUI();
                fetchRandomDocument();
                mInterstitialAd.loadAd(new AdRequest.Builder().build());
                fabClicks = 0;
            }
        });

        // banner
        AdRequest requestBanner = new AdRequest.Builder()
                .tagForChildDirectedTreatment(true)
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR).build();
        adView.loadAd(requestBanner);

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
                            Exception exception = task.getException();
                            log("get failed with " + exception);
                            if (exception != null)
                                Toast.makeText(MainActivity.this, exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        param_nr_docs = (int) mFirebaseRemoteConfig.getLong(NUMBER_OF_COUNTRIES_KEY_REMOTE_CONFIG);
                        fetchRandomDocument();

                    }
                });

    }

    private void initObjects() {
        random = new Random();
        handler = new Handler();
        runnableActivateFab = new Runnable() {
            @Override
            public void run() {
//                activateFab(true);
                fab.show();
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

    private void lateActivateFab() {
        handler.removeCallbacks(runnableActivateFab);
        handler.postDelayed(runnableActivateFab, DELAY_FAB_ACTIVATION);

    }

    private void fetchRandomDocument() {
        if (random == null) return;  // probabil click de test
        progressBar.setVisibility(View.VISIBLE);

        int r = random.nextInt(param_nr_docs) + 2;
        final String r_s = Integer.toString(r);
        final DocumentReference documentReference = firestore.collection(SIGHTS_AND_SOUNDS_COLLECTION).document(r_s);
        docListenerRegistration = documentReference.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                lateActivateFab();
                if (e != null) {
                    log("Listen failed. " + e);
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    return;
                }
                if (documentSnapshot != null && documentSnapshot.exists()) {


                    Item item = documentSnapshot.toObject(Item.class);
                    if (item != null) {
                        item.setId(documentSnapshot.getId());
                        docListenerRegistration.remove();
                        onItemLoaded(item);
                    }
                    log("Current id: " + documentSnapshot.getId() + " data:" + documentSnapshot.getData());


                } else {
                    log("Current data: null");
                    progressBar.setVisibility(View.GONE);
                }

            }
        });

    }

    private void showWellcomeUI() {
        fab.hide();
        textFlagA.setVisibility(View.GONE);
        flagA.setVisibility(View.GONE);
        textFlagB.setVisibility(View.GONE);
        flagB.setVisibility(View.GONE);
        btnSound.setVisibility(View.GONE);
        btnInfo.setVisibility(View.GONE);
        outputMessage.setVisibility(View.GONE);
        mainImage.setVisibility(View.GONE);
        if (mediaPlayer != null)
            mediaPlayer.reset();
    }

    private void fetchImage() {
        StorageReference imgRef = item.getImgRef(storage);
        StorageReference thumbRef = item.getThumbRef(storage);

        GlideApp.with(getApplicationContext()).clear(mainImage);
        mainImage.setVisibility(View.VISIBLE);

        GlideApp.with(getApplicationContext())
                .load(imgRef)
                .addListener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        fetchMusicUrl();
                        updateButtons();
                        return false;
                    }
                })
                .thumbnail(GlideApp.with(this).load(thumbRef))
                .transition(GenericTransitionOptions.with(R.anim.zoom_in))
                .into(mainImage);


    }

    private void fetchFlag(final ImageButton imageButton, final TextView textView, String code, final String countryName) {
        String flagLocation = "flags_jpg/" + code + ".jpg";
        StorageReference flagRef = storage.getReference(flagLocation);

        GlideApp.with(getApplicationContext())
                .load(flagRef)
                .addListener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {

                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        imageButton.setVisibility(View.VISIBLE);
                        textView.setText(countryName);
                        textView.setVisibility(View.VISIBLE);
                        return false;
                    }
                })
//                .transition(GenericTransitionOptions.with(R.anim.zoom_in))
                .into(imageButton);

    }

    private void updateButtons() {

        final String[] wrongCountry = getRandomFakeCountry();
        coin = random.nextBoolean();
        if (coin) {
            fetchFlag(flagA, textFlagA, item.getCountry_code(), item.getCountry());
            fetchFlag(flagB, textFlagB, wrongCountry[CODE], wrongCountry[NAME]);

        } else {
            fetchFlag(flagA, textFlagA, wrongCountry[CODE], wrongCountry[NAME]);
            fetchFlag(flagB, textFlagB, item.getCountry_code(), item.getCountry());
        }

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
                btnSound.setVisibility(View.VISIBLE);

                initAds();

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

    private void signInAnonymously() {

        mAuth.signInAnonymously()
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            log("signInAnonymously:success");
                            //noinspection unused,unused
                            FirebaseUser user = mAuth.getCurrentUser();
//                            updateUI(user);-
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

    }

    @OnClick(R.id.btn_info)
    public void onInfoClicked(View view) {
        if (item != null) {
            InfoDialogFragment infoDialogFragment = new InfoDialogFragment();
            infoDialogFragment.setItem(item);
            infoDialogFragment.show(getSupportFragmentManager(), InfoDialogFragment.TAG);
        }
    }

    @OnClick({R.id.flagA, R.id.flagB})
    public void onFlagClick(View v) {
        if (item == null) return;
        String outputMessage;
        int id = v.getId();
        if ((coin && id == R.id.flagA) || (!coin && id == R.id.flagB)) {
            outputMessage = String.format(getString(R.string.output_message_correct_answer), item.getCountry(), item.getImg_title());
            btnInfo.setVisibility(View.VISIBLE);

        } else {
            outputMessage = String.format(getString(R.string.output_message_wrong_answer), item.getCountry());
        }
        this.outputMessage.setText(outputMessage);
        if (this.outputMessage.getVisibility() != View.VISIBLE) {
            this.outputMessage.setVisibility(View.VISIBLE);
        }


    }

}
