/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.exo;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader;
import com.google.android.exoplayer2.ext.ima.ImaServerSideAdInsertionMediaSource;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.StyledPlayerControlView;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.DebugTextViewHelper;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * An activity that plays media using {@link ExoPlayer}.
 */
public class PlayerActivity extends AppCompatActivity
        implements OnClickListener, StyledPlayerControlView.VisibilityListener {

    // Saved instance state keys.

    private static final String KEY_TRACK_SELECTION_PARAMETERS = "track_selection_parameters";
    private static final String KEY_SERVER_SIDE_ADS_LOADER_STATE = "server_side_ads_loader_state";
    private static final String KEY_ITEM_INDEX = "item_index";
    private static final String KEY_POSITION = "position";
    private static final String KEY_AUTO_PLAY = "auto_play";

    //    protected StyledPlayerView playerView;
    protected PlayerView playerView;

    protected @Nullable
    ExoPlayer player;
    String Link = "https://admdn1.cdn.mangomolo.com/adsports1/smil:adsports1.stream.smil/playlist.m3u8";

    private boolean isShowingTrackSelectionDialog;
    private ImageView selectTracksButton;
    private DataSource.Factory dataSourceFactory;
    private List<MediaItem> mediaItems;
    private DefaultTrackSelector trackSelector;
    private DefaultTrackSelector.Parameters trackSelectionParameters;
    private DebugTextViewHelper debugViewHelper;
    private TracksInfo lastSeenTracksInfo;
    private boolean startAutoPlay;
    private int startItemIndex;
    private long startPosition;

    // For ad playback only.

    @Nullable
    private ImaServerSideAdInsertionMediaSource.AdsLoader serverSideAdsLoader;
    private ImaServerSideAdInsertionMediaSource.AdsLoader.@MonotonicNonNull State
            serverSideAdsLoaderState;

    // Activity lifecycle.

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataSourceFactory = DemoUtil.getDataSourceFactory(/* context= */ this);
        setContentView();
        selectTracksButton = findViewById(R.id.img_setting);
        selectTracksButton.setOnClickListener(this);

        playerView = findViewById(R.id.player_view);
//        playerView.setControllerVisibilityListener(this);
        playerView.requestFocus();

        if (savedInstanceState != null) {
            // Restore as DefaultTrackSelector.Parameters in case ExoPlayer specific parameters were set.
            trackSelectionParameters =
                    DefaultTrackSelector.Parameters.CREATOR.fromBundle(
                            savedInstanceState.getBundle(KEY_TRACK_SELECTION_PARAMETERS));
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
            startItemIndex = savedInstanceState.getInt(KEY_ITEM_INDEX);
            startPosition = savedInstanceState.getLong(KEY_POSITION);
            Bundle adsLoaderStateBundle = savedInstanceState.getBundle(KEY_SERVER_SIDE_ADS_LOADER_STATE);
            if (adsLoaderStateBundle != null) {
                serverSideAdsLoaderState =
                        ImaServerSideAdInsertionMediaSource.AdsLoader.State.CREATOR.fromBundle(
                                adsLoaderStateBundle);
            }
        } else {
            trackSelectionParameters =
                    new DefaultTrackSelector.ParametersBuilder(/* context= */ this).build();
            clearStartPosition();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        releasePlayer();
        clearStartPosition();
        setIntent(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer();
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        updateTrackSelectorParameters();
        updateStartPosition();
        outState.putBundle(KEY_TRACK_SELECTION_PARAMETERS, trackSelectionParameters.toBundle());
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay);
        outState.putInt(KEY_ITEM_INDEX, startItemIndex);
        outState.putLong(KEY_POSITION, startPosition);
        if (serverSideAdsLoaderState != null) {
            outState.putBundle(KEY_SERVER_SIDE_ADS_LOADER_STATE, serverSideAdsLoaderState.toBundle());
        }
    }

    // Activity input

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // See whether the player view wants to handle media or DPAD keys events.
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    // OnClickListener methods

    @Override
    public void onClick(View view) {
        if (view == selectTracksButton
                && !isShowingTrackSelectionDialog
                && TrackSelectionDialog.willHaveContent(trackSelector)) {
            isShowingTrackSelectionDialog = true;
            TrackSelectionDialog trackSelectionDialog =
                    TrackSelectionDialog.createForTrackSelector(
                            trackSelector,
                            /* onDismissListener= */ dismissedDialog -> isShowingTrackSelectionDialog = false);
            trackSelectionDialog.show(getSupportFragmentManager(), /* tag= */ null);
        }
    }

    // StyledPlayerControlView.VisibilityListener implementation

    @Override
    public void onVisibilityChange(int visibility) {
//        debugRootView.setVisibility(visibility);
    }

    // Internal methods

    protected void setContentView() {
        setContentView(R.layout.player_activity);
    }

    /**
     * @return Whether initialization was successful.
     */
    protected boolean initializePlayer() {
        if (player == null) {
            Intent intent = getIntent();

            mediaItems = createMediaItems(intent);
            if (mediaItems.isEmpty()) {
                return false;
            }


            trackSelector = new DefaultTrackSelector(/* context= */ this);
            lastSeenTracksInfo = TracksInfo.EMPTY;
            player =
                    new ExoPlayer.Builder(/* context= */ this)
                            .setMediaSourceFactory(createMediaSourceFactory())
                            .setTrackSelector(trackSelector)
                            .build();
            player.setTrackSelectionParameters(trackSelectionParameters);
            player.addListener(new PlayerEventListener());
            player.addAnalyticsListener(new EventLogger(trackSelector));
            player.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true);
            player.setPlayWhenReady(startAutoPlay);
            playerView.setPlayer(player);
            serverSideAdsLoader.setPlayer(player);
//            debugViewHelper.start();
        }
        boolean haveStartPosition = startItemIndex != C.INDEX_UNSET;
        if (haveStartPosition) {
            player.seekTo(startItemIndex, startPosition);
        }
        player.setMediaItems(mediaItems, /* resetPosition= */ !haveStartPosition);
        player.prepare();
//        updateButtonVisibility();
        return true;
    }

    private MediaSource.Factory createMediaSourceFactory() {
        ImaServerSideAdInsertionMediaSource.AdsLoader.Builder serverSideAdLoaderBuilder =
                new ImaServerSideAdInsertionMediaSource.AdsLoader.Builder(/* context= */ this, playerView);
        if (serverSideAdsLoaderState != null) {
            serverSideAdLoaderBuilder.setAdsLoaderState(serverSideAdsLoaderState);
        }
        serverSideAdsLoader = serverSideAdLoaderBuilder.build();
        ImaServerSideAdInsertionMediaSource.Factory imaServerSideAdInsertionMediaSourceFactory =
                new ImaServerSideAdInsertionMediaSource.Factory(
                        serverSideAdsLoader, new DefaultMediaSourceFactory(dataSourceFactory));
        return new DefaultMediaSourceFactory(dataSourceFactory)
                .setServerSideAdInsertionMediaSourceFactory(imaServerSideAdInsertionMediaSourceFactory);
    }

    private List<MediaItem> createMediaItems(Intent intent) {

        String action = "com.google.android.exoplayer.demo.action.VIEW";
        boolean actionIsListView = IntentUtil.ACTION_VIEW_LIST.equals(action);
        if (!actionIsListView && !IntentUtil.ACTION_VIEW.equals(action)) {
            showToast(getString(R.string.unexpected_intent_action, action));
            finish();
            return Collections.emptyList();
        }

        List<MediaItem> mediaItems =
                createMediaItems(intent, DemoUtil.getDownloadTracker(/* context= */ this));
        return mediaItems;
    }

    protected void releasePlayer() {
        if (player != null) {
            updateTrackSelectorParameters();
            updateStartPosition();
//            debugViewHelper.stop();
//            debugViewHelper = null;
            player.release();
            player = null;
            playerView.setPlayer(/* player= */ null);
            mediaItems = Collections.emptyList();
        }

        playerView.getAdViewGroup().removeAllViews();

    }


    private void updateTrackSelectorParameters() {
        if (player != null) {
            // Until the demo app is fully migrated to TrackSelectionParameters, rely on ExoPlayer to use
            // DefaultTrackSelector by default.
            trackSelectionParameters =
                    (DefaultTrackSelector.Parameters) player.getTrackSelectionParameters();
        }
    }

    private void updateStartPosition() {
        if (player != null) {
            startAutoPlay = player.getPlayWhenReady();
            startItemIndex = player.getCurrentMediaItemIndex();
            startPosition = Math.max(0, player.getContentPosition());
        }
    }

    protected void clearStartPosition() {
        startAutoPlay = true;
        startItemIndex = C.INDEX_UNSET;
        startPosition = C.TIME_UNSET;
    }

    // User controls

    private void showToast(int messageId) {
        showToast(getString(messageId));
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private class PlayerEventListener implements Player.Listener {

        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
//                showControls();
            }
//            updateButtonVisibility();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition();
                player.prepare();
            } else {
                player.seekToDefaultPosition();
                player.prepare();
                Toast.makeText(PlayerActivity.this, "Reloading", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksInfoChanged(TracksInfo tracksInfo) {
//            updateButtonVisibility();
            if (tracksInfo == lastSeenTracksInfo) {
                return;
            }
            if (!tracksInfo.isTypeSupportedOrEmpty(
                    C.TRACK_TYPE_VIDEO, /* allowExceedsCapabilities= */ true)) {
                showToast(R.string.error_unsupported_video);
            }
            if (!tracksInfo.isTypeSupportedOrEmpty(
                    C.TRACK_TYPE_AUDIO, /* allowExceedsCapabilities= */ true)) {
                showToast(R.string.error_unsupported_audio);
            }
            lastSeenTracksInfo = tracksInfo;
        }
    }

    private static List<MediaItem> createMediaItems(Intent intent, DownloadTracker downloadTracker) {
        List<MediaItem> mediaItems = new ArrayList<>();

        for (MediaItem item : IntentUtil.createMediaItemsFromIntent(intent)) {
            @Nullable
            DownloadRequest downloadRequest =
                    downloadTracker.getDownloadRequest(item.localConfiguration.uri);
            if (downloadRequest != null) {
                MediaItem.Builder builder = item.buildUpon();
                builder.setUri(downloadRequest.uri);

                mediaItems.add(builder.build());
            } else {
                mediaItems.add(item);
            }
        }
        return mediaItems;
    }
}
