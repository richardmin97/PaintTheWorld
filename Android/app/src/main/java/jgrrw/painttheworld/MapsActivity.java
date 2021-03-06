package jgrrw.painttheworld;

import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.util.Log;
import android.content.Intent;
import android.provider.Settings;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;


import java.util.HashMap;
import java.util.TimerTask;
import java.util.Timer;
import java.util.List;
import java.util.ArrayList;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.server.converter.StringToIntConverter;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polygon;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.android.volley.*;
import com.android.volley.toolbox.*;

public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener{

    public final static String GAME_END_STATUS = "jggrw.painttheworld.GAME_END_STATUS";
    public final static String RED_TEAM_SCORE = "jggrw.painttheworld.RED_TEAM_SCORE";
    public final static String BLUE_TEAM_SCORE = "jggrw.painttheworld.BLUE_TEAM_SCORE";
    private GoogleMap mMap;
    private TimerTask timerTask;
    private Timer timer;
    private GoogleApiClient mGoogleApiClient;
    private android.location.Location mLocation;
    private static final String TAG = "MapsActivity";
    private LocationRequest locationRequest;
    private LocationListener locationListener;
    public static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 0;
    private com.google.android.gms.location.FusedLocationProviderApi fusedLocationProviderApi;



    public String latitude_string = "";
    public String longitude_string = "";

    //This constant is how often the post to game_data occurs/main game logic loops
    public static final long GAME_LOOP_INCREMENT = 250;
    //Update location every 100 milliseconds
    public static final int LOCATION_INTERVAL = 100;

    TextView playerTeam;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        //Ask user for location permissions
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
        mLocation = new android.location.Location("");

        getLocation();

        playerTeam = (TextView) findViewById(R.id.Team);
        playerTeam.setText("loading");

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        final TextView textView = new TextView(this);
    }


    //Methods for Google Play Services Location
    private void getLocation() {
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(LOCATION_INTERVAL);
        locationRequest.setFastestInterval(LOCATION_INTERVAL);
        fusedLocationProviderApi = LocationServices.FusedLocationApi;
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onLocationChanged(android.location.Location location) {
        mLocation.set(location);
        Log.d("LOCATION:", location.getLatitude() + ", " + location.getLongitude());
    }

    //Methods for connecting to Google Play Services API
    @Override
    public void onConnected(Bundle bundle) {
        try {
            fusedLocationProviderApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
        } catch (SecurityException e) {
            Log.e("GET LOCATION", e.toString());
        }


    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection Suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed. Error: " + connectionResult.getErrorCode());
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    public void timerStart() {
        if(timer != null) {
            return;
        }
        timer = new Timer();
        timer.scheduleAtFixedRate(timerTask, 0, GAME_LOOP_INCREMENT);
    }

    public void timerStop() {
        timer.cancel();
        timer = null;
    }

    private double startX;
    private double startY;
    private double blockSize;
    private int[][] gameGrid;
    private int length;
    private int user_id;
    private int teamColor = 0;

    public void drawPaintAt(int x, int y, int colorCode) {

        String color;
        if(colorCode == 1)
            color = "#88FF0000"; // red
        else if (colorCode == 2)
            color = "#880000FF";  // blue
        else
            return;

        double top_lat     =  startX - y*blockSize + blockSize/2;
        double bottom_lat  =  startX - y*blockSize - blockSize/2;
        double top_long    =  startY + x*blockSize + blockSize/2;
        double bottom_long =  startY + x*blockSize - blockSize/2;

        PolygonOptions polygonOptions = new PolygonOptions()
                .add(new LatLng(top_lat, top_long), new LatLng(top_lat, bottom_long),
                        new LatLng(bottom_lat, bottom_long), new LatLng(bottom_lat, top_long))
                .strokeWidth(0)
                .fillColor(Color.parseColor(color));

        Polygon square = mMap.addPolygon(polygonOptions);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        // This information needs to be obtained from the server during login time.

        double centerX = getIntent().getExtras().getDouble(LoginActivity.CENTER_COORD_X);
        double centerY = getIntent().getExtras().getDouble(LoginActivity.CENTER_COORD_Y);
        int radius = getIntent().getExtras().getInt(LoginActivity.RADIUS);
        length = (2 * radius) + 1;
        blockSize = getIntent().getExtras().getDouble(LoginActivity.GRIDSIZE_LONGITUDE);  // potentially add two different blocksizes? */
        user_id = getIntent().getExtras().getInt(LoginActivity.USER_ID);
        Log.d("centerX", String.valueOf(centerX));
        Log.d("centerY", String.valueOf(centerY));
        Log.d("radius", String.valueOf(radius));
        Log.d("length", String.valueOf(length));
        Log.d("blockSize", String.valueOf(blockSize));
        Log.d("user_id", String.valueOf(user_id));

        if (user_id < 3) // user on team red
        {
            teamColor = 1;
            playerTeam.setText("You are on team red!");
        }
        else    // user on team blue
        {
            teamColor = 2;
            playerTeam.setText("You are on team blue!");
        }

        // This grid contains the locations of all the paint on the map.
        // Each location needs to be mapped to a coordinate before it can be painted.
        // Top left position of the grid is mapped to startX and startY - simply map by multiplying x and y by blockSize and adding to startY and startX.
        // 0 = empty
        // 1 = red
        // 2 = blue
        startX = (centerX + blockSize/2) + (radius * blockSize);
        startY = (centerY - blockSize/2) - (radius * blockSize);
        gameGrid = new int[length][length];     // should be 101 x 101
        LatLng battlegroundCenter = new LatLng(centerX, centerY);
        float zoomLevel = 16.5f;

        // Additional configuration of maps

        mMap.getUiSettings().setMapToolbarEnabled(false);

        mMap.addMarker(new MarkerOptions().position(battlegroundCenter).title("Center of battleground."));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(battlegroundCenter, zoomLevel));

        // Draw square around arena.
        String color = "#2500FF00"; // green
        double top_lat     =  startX;
        double bottom_lat  =  startX - length * blockSize;
        double top_long    =  startY + length * blockSize;
        double bottom_long =  startY;
        PolygonOptions polygonOptions = new PolygonOptions()
                .add(new LatLng(top_lat, top_long), new LatLng(top_lat, bottom_long),
                        new LatLng(bottom_lat, bottom_long), new LatLng(bottom_lat, top_long))
                .strokeWidth(0)
                .fillColor(Color.parseColor(color));
        Polygon square = mMap.addPolygon(polygonOptions);

        timer = null;
        //Main game logic loop
        timerTask = new TimerTask() {

            @Override
            public void run() {
                runOnUiThread(new Runnable()
                {
                    public void run()
                    {
                        double currentX = mLocation.getLongitude();
                        double currentY = mLocation.getLatitude();
                        final String URL = "http://ec2-54-153-39-233.us-west-1.compute.amazonaws.com/game_data";

                        // Post params to be sent to the server
                        HashMap<String, Double> params = new HashMap<String, Double>();
                        params.put("lat", mLocation.getLatitude());
                        params.put("long", (mLocation.getLongitude()));
                        params.put("user-id", (double) user_id);
                        JSONObject js = new JSONObject(params);
                        try {
                            js.put("user-id", user_id);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        JsonObjectRequest req = new JsonObjectRequest(URL, js,
                                new Response.Listener<JSONObject>() {
                                    @Override
                                    public void onResponse(JSONObject response) {
                                        try {
                                            // check to see if game is over
                                            if (response.has("error")) {
                                                String isGameOver = response.getString("error");
                                                Log.d("error", isGameOver);
                                                if (isGameOver.equals("Game over.")) {
                                                    gameOver();
                                                    timer.cancel();
                                                    timer.purge();
                                                    return;
                                                }
                                                else
                                                    Log.d("error", isGameOver);
                                            }
                                            else{
                                            JSONArray diffs = response.getJSONArray("grid-deltas");
                                            int numberOfDiffs = diffs.length();
                                            for (int i = 0; i < numberOfDiffs; i++) {
                                                JSONObject singleDiff = diffs.getJSONObject(i);
                                                JSONObject singleDiffCoord = singleDiff.getJSONObject("coord");
                                                int x = singleDiffCoord.getInt("x");
                                                int y = singleDiffCoord.getInt("y");
                                                int colorCode = singleDiff.getInt("color");
                                                drawPaintAt(x, y, colorCode);
                                                gameGrid[x][y] = colorCode;
                                            }
                                            }
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                            Log.d("error", e.getMessage());
                                        }
                                    }
                                }, new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                            }
                        });

                        // add the request object to the queue to be executed
                        ApplicationController.getInstance().addToRequestQueue(req);
                    }
                });
            }
        };

        //Start main game logic loop
        timerStart();
    }

    /**
     * Prepares data to be displayed in GameOverActivity and changes to GameOverActivity
     *
     */
    public void gameOver() {
        Intent intent = new Intent(this, GameOverActivity.class);
        //Current it just puts some meaningless placeholders
        int redScore = 0;
        int blueScore = 0;
        for (int i = 0; i < length; i++) {
            for (int j = 0; j < length; j++)
            {
                if (gameGrid[i][j] == 1)
                    redScore++;
                else if (gameGrid[i][j] == 2)
                    blueScore++;
            }
        }
        String status = "Error: No game result assigned";
        if (teamColor == 1)
        {
            if (redScore > blueScore) {
                status = "Victory!";
            }
            else if (redScore < blueScore)
            {
                status = "You lost :(";
            }
            else
            {
                status = "It's a tie!";
            }
        }
        else if (teamColor == 2)
        {
            if (redScore > blueScore) {
                status = "You lost :(";
            }
            else if (redScore < blueScore)
            {
                status = "Victory!";
            }
            else
            {
                status = "It's a tie!";
            }
        }

        intent.putExtra(GAME_END_STATUS, status);
        intent.putExtra(RED_TEAM_SCORE, redScore);
        intent.putExtra(BLUE_TEAM_SCORE, blueScore);
        startActivity(intent);
    }
}