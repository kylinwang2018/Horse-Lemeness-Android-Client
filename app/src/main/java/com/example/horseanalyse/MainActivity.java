package com.example.horseanalyse;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    VideoView normalVideo;
    VideoView frontVideo;
    VideoView rearVideo;

    ImageView normalCover;
    ImageView frontCover;
    ImageView rearCover;

    Uri normalUri = null;
    Uri frontUri = null;
    Uri rearUri = null;

    private int VIDEO_NUMBER;

    private static final String[] CAMERA_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};
    static final int REQUEST_VIDEO_CAPTURE = 3;
    static final int REQUEST_TAKE_GALLERY_VIDEO = 2;
    static final int PERMISSION_CODES = 1;
    static boolean isCamera = true;

    static final int UPLOAD = 0;

    // Request Permissions
    @TargetApi(Build.VERSION_CODES.M)
    private void requestPermission(){
        List<String> p = new ArrayList<>();
        for(String permission :CAMERA_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this,permission) != PackageManager.PERMISSION_GRANTED){
                p.add(permission);
            }
        }
        if(p.size() > 0){
            requestPermissions(p.toArray(new String[p.size()]),PERMISSION_CODES);
        }else{
            if (isCamera) {
                dispatchTakeVideoIntent();
            }else{
                dispatchGalleryIntent();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_CODES: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    if (isCamera) {
                        dispatchTakeVideoIntent();
                    }else{
                        dispatchGalleryIntent();
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(getApplicationContext(), "Permission denied",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        }
    }

    private void dispatchTakeVideoIntent() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        takeVideoIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
        //takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 1);
        takeVideoIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30);
        if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
        }
    }

    private void dispatchGalleryIntent() {
        if (android.os.Build.BRAND.equals("Huawei")) {
            Intent intentPic = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intentPic, 2);
        }
        if (android.os.Build.BRAND.equals("Xiaomi")) {
            Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "video/*");
            startActivityForResult(Intent.createChooser(intent, "Select one video"), REQUEST_TAKE_GALLERY_VIDEO);
        } else {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            startActivityForResult(Intent.createChooser(intent, "Select one video"), REQUEST_TAKE_GALLERY_VIDEO);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_OK && null != intent) {
            if (requestCode == REQUEST_VIDEO_CAPTURE || requestCode == REQUEST_TAKE_GALLERY_VIDEO) {
                Uri uri = intent.getData();
                switch (VIDEO_NUMBER){
                    case 1:
                        normalUri = uri;
                        showVideos();
                        break;
                    case 2:
                        frontUri = uri;
                        showVideos();
                        break;
                    case 3:
                        rearUri = uri;
                        showVideos();
                        break;
                }
            }
        } else {
            setDefaultVideo();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        normalVideo = findViewById(R.id.normalVideo);
        frontVideo = findViewById(R.id.frontVideo);
        rearVideo = findViewById(R.id.rearVideo);

        normalCover = findViewById(R.id.normalCover);
        frontCover = findViewById(R.id.frontCover);
        rearCover = findViewById(R.id.rearCover);


        setDefaultVideo();
    }

    @Override
    protected void onResume(){
        super.onResume();
        showVideos();
    }

    public void uploadVideos(View view) {
        if (frontUri != null && normalUri != null && rearUri != null) {
            Intent intent = new Intent(this, Result.class);
            String normalPath = getVideoPathFromURI(normalUri);
            String frontPath = getVideoPathFromURI(frontUri);
            String rearPath = getVideoPathFromURI(rearUri);
            intent.putExtra("NormalPath", normalPath);
            intent.putExtra("FrontPath", frontPath);
            intent.putExtra("RearPath", rearPath);
            startActivityForResult(intent, UPLOAD);
        }else{
            Toast.makeText(getApplicationContext(), "You need upload all three views",
                    Toast.LENGTH_SHORT).show();
        }
    }


    private String getVideoPathFromURI(Uri uri) {
        String path = uri.getPath(); // uri = any content Uri

        Uri databaseUri;
        String selection;
        String[] selectionArgs;
        String videoPath = null;
        if (path != null && path.contains("/document/video:")) { // files selected from "Documents"
            databaseUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            selection = "_id=?";
            selectionArgs = new String[]{DocumentsContract.getDocumentId(uri).split(":")[1]};
        } else { // files selected from all other sources, especially on Samsung devices
            databaseUri = uri;
            selection = null;
            selectionArgs = null;
        }
        try {
            String[] projection = {
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.LATITUDE,
                    MediaStore.Video.Media.LONGITUDE};

            Cursor cursor = getContentResolver().query(databaseUri,
                    projection, selection, selectionArgs, null);

            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(projection[0]);
                videoPath = cursor.getString(columnIndex);
            }
            cursor.close();
        } catch (Exception e) {
            Log.e("Error", "getVideoPathFromURI: Exception");
        }
        return videoPath;
    }

    private void showVideos(){
        if (normalUri != null){
            normalVideo.setVideoURI(normalUri);
            normalVideo.start();
            normalVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                }
            });
            normalVideo.setVisibility(View.VISIBLE);
            normalCover.setVisibility(View.GONE);
        }else{
            normalVideo.setVisibility(View.GONE);
            normalVideo.setVideoURI(null);
            normalCover.setVisibility(View.VISIBLE);
        }

        if (frontUri != null){
            frontVideo.setVideoURI(frontUri);
            frontVideo.start();
            frontVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                }
            });
            frontVideo.setVisibility(View.VISIBLE);
            frontCover.setVisibility(View.GONE);
        }else{
            frontVideo.setVisibility(View.GONE);
            frontVideo.setVideoURI(null);
            frontCover.setVisibility(View.VISIBLE);
        }

        if (rearUri != null){
            rearVideo.setVideoURI(rearUri);
            rearVideo.start();
            rearVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.setLooping(true);
                }
            });
            rearVideo.setVisibility(View.VISIBLE);
            rearCover.setVisibility(View.GONE);
        }else{
            rearVideo.setVisibility(View.GONE);
            rearVideo.setVideoURI(null);
            rearCover.setVisibility(View.VISIBLE);
        }
    }


    private void setDefaultVideo(){
        normalVideo.setVisibility(View.GONE);
        frontVideo.setVisibility(View.GONE);
        rearVideo.setVisibility(View.GONE);

        normalCover.setVisibility(View.VISIBLE);
        frontCover.setVisibility(View.VISIBLE);
        rearCover.setVisibility(View.VISIBLE);

        normalUri = null;
        frontUri = null;
        rearUri = null;

    }

    public void setNormalVideo(View view){
        VIDEO_NUMBER = 1;
        popMenu(view);
    }

    public void setFrontVideo(View view){
        VIDEO_NUMBER = 2;
        popMenu(view);
    }

    public void setRearVideo(View view){
        VIDEO_NUMBER = 3;
        popMenu(view);
    }

    private void popMenu(View view) {
        PopupMenu popup = new PopupMenu(MainActivity.this, view);
        popup.getMenuInflater().inflate(R.menu.add_video, popup.getMenu());
        popup.show();
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.take:
                        isCamera = true;
                        requestPermission();
                        break;
                    case R.id.select:
                        isCamera = false;
                        requestPermission();
                        break;
                }

                return true;
            }

        });

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



}
