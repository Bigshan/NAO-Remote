package com.ryanwedoff.senor.naoservercontroller;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class FileActivity extends AppCompatActivity {

    private static final int READ_REQUEST_CODE = 1;
    private RecyclerView.Adapter<FileTextAdapter.ViewHolder> mAdapter;
    private ArrayList<String> fileLines;
    SocketService mBoundService;
    private boolean mIsBound;
    MyReceiver myReceiver;
    private NaoFileParse fileParse;
    public static ArrayList robotNames;
    private static boolean fConnect = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        fileLines = new ArrayList<>();

        RecyclerView mRecyclerView = (RecyclerView) findViewById(R.id.file_list_view);
        mRecyclerView.setHasFixedSize(false);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new FileTextAdapter(fileLines);
        mRecyclerView.setAdapter(mAdapter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.add_file);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fConnect = true;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("text/plain");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, READ_REQUEST_CODE);

            }
        });

        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);


        Context context = getActivity();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        SharedPreferences sharedPref = context.getSharedPreferences(getString(R.string.pref_file_key), Context.MODE_PRIVATE);
        String defaultValue = getString(R.string.robot_names);
        String namesObj = sharedPref.getString(getString(R.string.robot_names), defaultValue);
        Gson gson = new Gson();
        robotNames = gson.fromJson(namesObj, ArrayList.class);
        if(robotNames == null){
            robotNames = new ArrayList<String>();
        }

        String [] moods = getResources().getStringArray(R.array.mood_array);
        fileParse = new NaoFileParse(robotNames,moods);

        //TODO this code can move to method
        ToggleButton toggle = (ToggleButton) findViewById(R.id.run_pause_button);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is Paused
                } else {
                    // The toggle is Play
                }
            }
        });

    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBoundService = ((SocketService.LocalBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBoundService = null;
        }

    };
    private void doBindService() {
        //swipeContainer.setRefreshing(false);
        bindService(new Intent(FileActivity.this, SocketService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        if(mBoundService!=null){
            mBoundService.IsBoundable();
        }
    }
    private void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    @Override
    protected void onPause(){
        super.onPause();
        doUnbindService();
        if(myReceiver != null)
            unregisterReceiver(myReceiver);
        if(mBoundService!=null)
            mBoundService.stopSelf();
    }


   @Override
    protected void onResume(){
        super.onResume();


           ConnectivityManager cm =
                   (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);

           NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
           boolean isConnected = activeNetwork != null &&
                   activeNetwork.isConnectedOrConnecting();
           if(isConnected){
               startService(new Intent(FileActivity.this, SocketService.class));
               doBindService();
           } else {
               View view = findViewById(R.id.file_relative_view);
               //TODO
               //Snackbar.make(view, "No network connection", Snackbar.LENGTH_LONG).setAction("Action", null).show();
           }
           IntentFilter intentFilter = new IntentFilter();
           intentFilter.addAction(SocketService.ACTION);
           registerReceiver(myReceiver, intentFilter);


    }


    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i("URI", "Uri: " + uri.toString());
                new GetRobotFile().execute(uri);

            }
        }


    }

    public void onRestart(View view) {
    }

    public void onReSend(View view) {
        TextView textView = (TextView) findViewById(R.id.file_text_view);
        String message = textView.getText().toString();
        if(mBoundService != null){
            try{
                int lineNum = fileLines.indexOf(message);
                if(fileParse.checkLine(message,lineNum))
                    mBoundService.sendMessage(message);

            } catch (Exception e) {
                Log.e("Socket Connection Error", "Socket Connection Error");
                Snackbar.make(view, "Service Binding Error", Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
        } else{
            Snackbar.make(view, "Socket Connection Refused", Snackbar.LENGTH_LONG).setAction("Action", null).show();
        }

    }

    public void onClear(View view) {
        fileLines.clear();
        mAdapter.notifyDataSetChanged();
    }

    private class GetRobotFile extends AsyncTask<Uri, Void, Void> {
        /**
         * This is done under async to ensure that network sources can pull in files
         */
        protected Void doInBackground(Uri... uri){
            Log.e("HERE","HERE");
            InputStream inputStream = null;
            try {
                inputStream = getContentResolver().openInputStream(uri[0]);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            BufferedReader br;
            if (inputStream != null) {
                br = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                try {
                    while ((line = br.readLine()) != null) {
                        fileLines.add(line);
                        //Log.e(line,line);
                    }
                    if(!fileParse.firstCheckLine(fileLines.get(0))){
                        View view = findViewById(R.id.file_relative_view);
                        Snackbar.make(view, "'--NAO-START' needed at line 0", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                        Log.i("--NAO-START", "needed at line 0");
                    }

                    if(!fileParse.lastCheckLine(fileLines.get(fileLines.size()-1))){
                        View view = findViewById(R.id.file_relative_view);
                        Snackbar.make(view, "'--NAO-STOP' needed at last line", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                        Log.i("--NAO-STOP","needed at last line");
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }else{
                Log.e("Null URI Error","Null URI Error");
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            firstConnect();
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }


    }
    private void firstConnect(){
        ConnectivityManager cm =
                (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        if(isConnected){
            startService(new Intent(FileActivity.this, SocketService.class));
            doBindService();
        } else {
            View view = findViewById(R.id.file_relative_view);
            Snackbar.make(view, "No network connection", Snackbar.LENGTH_LONG).setAction("Action", null).show();
        }
        mAdapter.notifyDataSetChanged();
        fConnect = false;
    }

    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra(SocketService.SERVER_CONNECTION);

            //TODO
            View view = findViewById(R.id.robot_name_layout);
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).setAction("Action", null).show();
        }
    }

    public Context getActivity() {
        return this;
    }



}