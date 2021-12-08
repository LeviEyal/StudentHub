package com.example.myapplication.new_post;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.utils.FirebaseUtils;
import com.example.myapplication.R;
import com.example.myapplication.utils.Utils;
import com.example.myapplication.database.PostData;
import com.example.myapplication.databinding.FragmentNewPostBinding;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.mobsandgeeks.saripaar.ValidationError;
import com.mobsandgeeks.saripaar.Validator;
import com.mobsandgeeks.saripaar.annotation.NotEmpty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NewPostFragment extends Fragment implements Validator.ValidationListener {
    static final private String TAG = "NewPostFragment";
    static final private int MAX_IMAGES = 3;

    private FragmentNewPostBinding binding;

    private int imagesCount = 0;
    private List<ImageView> images;
    private List<String> imagesUris = new ArrayList<>();
    @NotEmpty()
    private EditText title;
    @NotEmpty()
    private EditText description;

    private EditText price;
    private LinearLayout linearLayout;
    private ActivityResultLauncher<Intent> someActivityResultLauncher;

    @SuppressLint("UseCompatLoadingForDrawables")
    private Drawable getDefaultImage(){
        Context context = getContext();
        return context!=null ? context.getDrawable(R.drawable.ic_baseline_image_24) : null;
    }
    private ImageView createDefaultImage(){
        ImageView imageView = new ImageView(getContext());
        imageView.setImageDrawable(getDefaultImage());
        imageView.setOnClickListener(this::onImagePick);
        int width = (int)getResources().getDimension(R.dimen.add_post_image_width);
        int height = (int)getResources().getDimension(R.dimen.add_post_image_height);
        int padding = (int)getResources().getDimension(R.dimen.add_post_image_padding);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width,height);
        imageView.setLayoutParams(lp);
        imageView.setPadding(padding,0,padding,padding*2);
        linearLayout.addView(imageView);
        return imageView;
    }

    private void onImagePick(View v){
        //TODO: check if image set
        final String TYPE = "image/*";
        Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getIntent.setType(TYPE);
        Intent pickIntent = new Intent(Intent.ACTION_PICK);
        pickIntent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,TYPE);
        Intent chooserIntent = Intent.createChooser(getIntent, "Select Image");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {pickIntent});
        someActivityResultLauncher.launch(chooserIntent);
    }

    private int getPrice(){
        String priceStr = price.getText().toString();
        return !priceStr.isEmpty() ? Integer.parseInt(price.getText().toString()) : 0;
    }

    private void upload_post(ProgressDialog mDialog,Context context,View v){
        DatabaseReference mDatabase = FirebaseDatabase.getInstance().getReference();
        String key = mDatabase.child("products").push().getKey();
        String user_id = FirebaseAuth.getInstance().getCurrentUser().getUid();
        PostData post = new PostData(title.getText().toString(),description.getText().toString(), getPrice(),user_id,imagesUris);
        Map<String, Object> postValues = post.toMap();
        Task<?> addToDBTask = mDatabase.child("/posts/" + key).updateChildren(postValues);
        addToDBTask.addOnCompleteListener(task->{
            mDialog.cancel();
            if(task.isSuccessful()){
                cleanForm();
                Utils.hideKeyboardFrom(context,v);
                Toast.makeText(context, "Post Added Successfully.",
                        Toast.LENGTH_SHORT).show();
            }else{
                Toast.makeText(context, "Add Post Failed.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        NewPostViewModel newPostViewModel = new ViewModelProvider(this).get(NewPostViewModel.class);
        binding = FragmentNewPostBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        init(root);
        return root;
    }

    private void add_image(ActivityResult result){
        //TODO: extract to some utils class
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            assert data != null;
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), imageUri);
                images.get(images.size()-1).setImageBitmap(bitmap);
                imagesCount++;
                if(images.size()<MAX_IMAGES){
                    images.add(createDefaultImage());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void init(View root){
        title = root.findViewById(R.id.post_title);
        description = root.findViewById(R.id.post_description);
        price = root.findViewById(R.id.post_price);
        linearLayout = root.findViewById(R.id.new_post_images);
        Button addPostBtn = root.findViewById(R.id.add_post_btn);

        images = new ArrayList<>(MAX_IMAGES);
        imagesUris = new ArrayList<>(MAX_IMAGES);
        images.add(createDefaultImage());
        Validator validator = new Validator(this);
        addPostBtn.setOnClickListener(v -> validator.validate());
        someActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::add_image);
    }
    void cleanForm(){
        title.setText("");
        description.setText("");
        price.setText("");
        imagesCount = 0;
        for(ImageView imageView : images){
            imageView.setImageResource(0);
        }
        images.get(0).setImageDrawable(getDefaultImage());

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onValidationSucceeded() {
        Context context = getContext();
        ProgressDialog mDialog = Utils.createProgressDialog(getContext());
        for(int i=0;i<imagesCount;i++){
            final StorageReference storageReference = FirebaseStorage.getInstance().
                    getReference("posts-images/" + UUID.randomUUID().toString() + ".jpg");
            storageReference.putBytes(FirebaseUtils.before_upload(images.get(i))).
                    addOnSuccessListener(taskSnapshot -> storageReference.getDownloadUrl().addOnCompleteListener(upload_task -> {
                        imagesUris.add(upload_task.getResult().toString());
                        Log.d(TAG,upload_task.getResult().toString());
                        if(imagesUris.size() == imagesCount){
                            upload_post(mDialog,context,getView());
                        }
                    })).
                    addOnFailureListener(e -> {
                        mDialog.cancel();
                        Toast.makeText(context,"Failed to upload post",Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Image Uploading was failed:\n" + e.getMessage());
                    });
        }
    }

    @Override
    public void onValidationFailed(List<ValidationError> errors) {
        Utils.onValidationFailed(errors,getContext());
    }
}