package mx.com.softwell.misfotografias;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import mx.com.softwell.misfotografias.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static  final int RC_GALLERY = 21;
    private static  final int RC_CAMERA = 22;

    private static  final int RP_CAMERA = 121;
    private static  final int RP_STORANGE = 122;

    private static  final String IMAGE_DIRECTORY = "/MiPhotoApp";
    private static  final String MY_PHOTO = "mi_photo";

    private static  final String PATH_PROFILE = "profile";
    private static  final String PATH_PHOTO_URL = "photoURL";

    private TextView lblMessage;
    private ActivityMainBinding binding;

    private StorageReference storageReference;
    private DatabaseReference databaseReference;

    private String currentPhotoPath;
    private Uri photoSelectadUri;

    private BottomNavigationView.OnNavigationItemSelectedListener onNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {
        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()){
                case R.id.navigation_gallery:
                    lblMessage.setText("Galería");
                    fromGallery();
                    return true;
                case R.id.navigation_camera:
                    lblMessage.setText("Cámara");
                    //fromCamara();
                    dispachTakePictureIntent();
                    return true;
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        lblMessage = findViewById(R.id.lblMessage);
        BottomNavigationView navigation = findViewById(R.id.navigation);
        navigation.setOnNavigationItemSelectedListener(onNavigationItemSelectedListener);

        cinfigFirebase();

        binding.btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StorageReference profileReference = storageReference.child(PATH_PROFILE);
                StorageReference photoReference = profileReference.child(MY_PHOTO);
                photoReference.putFile(photoSelectadUri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Snackbar.make(binding.container, R.string.main_message_upload_success, BaseTransientBottomBar.LENGTH_LONG).show();
                    taskSnapshot.getMetadata().getReference().getDownloadUrl().addOnCompleteListener(new OnCompleteListener<Uri>() {
                        @Override
                        public void onComplete(@NonNull Task<Uri> task) {
                            savePhotoUrl(task.getResult());
                        }
                    });
                    binding.btnDelete.setVisibility(View.VISIBLE);
                    binding.lblMessage.setText(R.string.main_message_down);
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                      Snackbar.make(binding.container, R.string.main_mesage_upload_error,BaseTransientBottomBar.LENGTH_LONG).show();
                    }
                });
            }
        });
        configPhotoProfile();
    }
    private void configPhotoProfile(){
        storageReference.child(PATH_PROFILE).child(MY_PHOTO).getDownloadUrl()
                .addOnSuccessListener(new OnSuccessListener<Uri>() {
                    @Override
                    public void onSuccess(Uri uri) {
                        RequestOptions options = new RequestOptions()
                                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                .centerCrop();

                        Glide.with(MainActivity.this)
                                .load(uri)
                                .placeholder(R.drawable.load2)
                                .error(R.drawable.ic_error_24)
                                .apply(options)
                                .into(binding.imgPhoto);
                        binding.btnDelete.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        binding.btnDelete.setVisibility(View.GONE);
                        Snackbar.make(binding.container, R.string.main_menssage_error_notfound, BaseTransientBottomBar.LENGTH_LONG).show();
                    }
                });
    }

    private void savePhotoUrl(Uri downloadUri) {
        databaseReference.setValue(downloadUri.toString());
    }

    private void cinfigFirebase() {
    storageReference = FirebaseStorage.getInstance().getReference();
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        databaseReference =database.getReference().child(PATH_PROFILE).child(PATH_PHOTO_URL);
    }

    private void fromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RC_GALLERY);
    }
    private void fromCamara(){
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, RC_CAMERA);
    }
    private void dispachTakePictureIntent(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null){
            File photoFle;
            photoFle = createImageFile();
            if (photoFle!=null){
                Uri photoUri = FileProvider.getUriForFile(this,
                        "mx.com.softwell.misfotografias", photoFle);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, RC_CAMERA);
            }
        }
    }

    private File createImageFile() {
        final String tmeStamp = new SimpleDateFormat("dd-MM-yyyy_HHmmss", Locale.ROOT)
                .format(new Date());
        final  String imageFileName = MY_PHOTO + tmeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File image = null;
        try {
            image = File.createTempFile(imageFileName, "jpg", storageDir);
            currentPhotoPath = image.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return image;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(resultCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case RC_GALLERY:
                    if (data!=null) {
                        photoSelectadUri = data.getData();
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), photoSelectadUri);
                            binding.imgPhoto.setImageBitmap(bitmap);
                            binding.btnDelete.setVisibility(View.GONE);
                            binding.lblMessage.setText(R.string.main_menssage_Question_upload);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
            }
                    break;
                case RC_CAMERA:
                    photoSelectadUri = addPicGallery();
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(),
                                photoSelectadUri);
                        binding.imgPhoto.setImageBitmap(bitmap);
                        binding.btnDelete.setVisibility(View.GONE);
                        binding.lblMessage.setText(R.string.main_menssage_Question_upload);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
        }
      }
    }

    private Uri addPicGallery() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File file = new File(currentPhotoPath);
        Uri contentUri = Uri.fromFile(file);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
        currentPhotoPath = null;
        return contentUri;
    }
}