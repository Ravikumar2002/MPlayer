package com.example.mplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.customview.widget.Openable;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.jean.jcplayer.model.JcAudio;
import com.example.jean.jcplayer.view.JcPlayerView;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private  boolean checkPermission=false;
//    global
    Uri uri;
    String songName,songUrl;

    ListView listView;

    ArrayList<String>arrayListSongName=new ArrayList<>();
    ArrayList<String>arrayListSongURL=new ArrayList<>();

    ArrayAdapter<String>arrayAdapter;

    JcPlayerView jcPlayerView;
    ArrayList<JcAudio> jcAudios=new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //listview initialize

        listView=findViewById(R.id.myListview);

//        firebase data retrieving
        jcPlayerView=findViewById(R.id.jcplayer);

        retrieveSongs();

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                jcPlayerView.playAudio(jcAudios.get(i));
                jcPlayerView.setVisibility(View.VISIBLE);
            }
        });

    }

    private void retrieveSongs() {

        DatabaseReference databaseReference=FirebaseDatabase.getInstance().getReference("Songs");//node denge firebase database
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                for(DataSnapshot ds: snapshot.getChildren()){
                    Song songobj=ds.getValue(Song.class);//firebase  data  store
                    arrayListSongName.add(songobj.getSongName());
                    arrayListSongURL.add(songobj.getSongUrl());

                    jcAudios.add(JcAudio.createFromURL(songobj.getSongName(),songobj.getSongUrl()));
                }

//                adapter initialize

                arrayAdapter=new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_list_item_1,arrayListSongName){
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

                        View view=super.getView(position,convertView,parent);
                        TextView textView=(TextView) view.findViewById(android.R.id.text1);
                        textView.setSingleLine(true);

                        textView.setMaxLines(1);


                        return view;
                    }
                };

                jcPlayerView.initPlaylist(jcAudios,null);//jcplayer initialize listview
                listView.setAdapter(arrayAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });


    }

    //    step 1 oncreateoptionmenu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // inflate
        getMenuInflater().inflate(R.menu.custom_menu,menu);

        return super.onCreateOptionsMenu(menu);
    }
//    step 2 option item selected menu

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId()==R.id.navi_upload){//upload item ke id ke equal hai to
            if(validatePermission()){
                pickSong();//main activity resolve function
            }
        }
        return super.onOptionsItemSelected(item);
    }


    private void pickSong() {//audio song pick intent
        Intent intent_upload=new Intent();
        intent_upload.setType("audio/*");
        intent_upload.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent_upload,1);

    }

//    step 4 activity for result handling


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if(requestCode==1){ //result matching
            if(resultCode==RESULT_OK){
                //uniform resource identifier object create
                uri=data.getData();//get data in pick song uri in save

//                song name detected use cursor
                Cursor cursor=getApplicationContext().getContentResolver()
                        .query(uri,null,null,null,null);

                int indexName=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);//song file name
                cursor.moveToFirst();//song peek
                songName=cursor.getString(indexName);//store song

//                upload firebase storage function---
                uploadSongToFirebaseStorage();


            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
//step 5-- firebase store in  song--
    private void uploadSongToFirebaseStorage() {

        StorageReference storageReference= FirebaseStorage.getInstance().getReference()// song name fatching
                .child("Songs").child(uri.getLastPathSegment());

        ProgressDialog progressDialog=new ProgressDialog(this);
        progressDialog.show();

//        firebase uploading song

        storageReference.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {//success ke liye

                Task<Uri>uriTask=taskSnapshot.getStorage().getDownloadUrl();
                while (!uriTask.isComplete());
                    Uri urlsong=uriTask.getResult();
                    songUrl=urlsong.toString();

                    //function create--

                    uploadDetailsToDatabase();

                    progressDialog.dismiss();




            }
        }).addOnFailureListener(new OnFailureListener() {// failure ke liye
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage().toString(), Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(@NonNull UploadTask.TaskSnapshot snapshot) {
                double progress=(100.0*snapshot.getBytesTransferred())/snapshot.getTotalByteCount();// check percentage
                int currentProgress=(int)progress;
                progressDialog.setMessage("Uploaded: "+currentProgress+"%");
            }
        });

    }
// upload details function----
    private void uploadDetailsToDatabase() {
//        details uploaded firebase
        Song songObj=new Song(songName,songUrl);//object ke under data ko set kr diye

//        firebase me store krenge data ko
        FirebaseDatabase.getInstance().getReference("Songs").push().setValue(songObj)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {

                if (task.isSuccessful()){
                    Toast.makeText(MainActivity.this, "Song Uploaded", Toast.LENGTH_SHORT).show();
                }

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this,e.getMessage().toString() , Toast.LENGTH_SHORT).show();
            }
        });
    }

    //    step 3 function create and permission check  ya permission handling
    private  boolean validatePermission(){
        Dexter.withActivity(MainActivity.this).withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        checkPermission=true;
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        checkPermission=false;
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                    permissionToken.continuePermissionRequest();
                    }
                }).check();

        return checkPermission;
    }

}