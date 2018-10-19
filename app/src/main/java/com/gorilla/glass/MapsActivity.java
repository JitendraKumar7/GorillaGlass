package com.gorilla.glass;

import android.Manifest;
import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.android.PolyUtil;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import org.joda.time.DateTime;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MapsActivity extends FragmentActivity
        implements OnMapReadyCallback, Runnable {

    private int delay = 500;
    private LatLng origin;
    private LatLng destination;
    private GoogleMap mGoogleMap;
    private MapsActivity activity = this;
    private Handler mHandler = new Handler();

    @Override
    public void run() {
        try {
            Location location = mGoogleMap.getMyLocation();
            origin = new LatLng(location.getLatitude(), location.getLongitude());
            mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(origin, 12.0f));
        } catch (NullPointerException e) {
            mHandler.postDelayed(activity, delay);
            showLog(e.getMessage(), e);
        }


    }

    void showLog(Object message) {
        String TAG = getString(R.string.app_name);
        Log.d(TAG, String.valueOf(message));
    }

    private GeoApiContext getGeoContext() {
        GeoApiContext geoApiContext = new GeoApiContext();
        return geoApiContext.setQueryRateLimit(3)
                .setApiKey(getString(R.string.google_maps_key))
                .setConnectTimeout(1, TimeUnit.SECONDS)
                .setReadTimeout(1, TimeUnit.SECONDS)
                .setWriteTimeout(1, TimeUnit.SECONDS);
    }

    private String getEndLocationTitle(DirectionsResult results) {
        return "Time :" + results.routes[0].legs[0].duration.humanReadable + " Distance :" + results.routes[0].legs[0].distance.humanReadable;
    }

    private void addPolyline(DirectionsResult results, GoogleMap mMap) {
        List<LatLng> decodedPath = PolyUtil.decode(results.routes[0].overviewPolyline.getEncodedPath());
        mMap.addPolyline(new PolylineOptions().addAll(decodedPath));
    }

    private void addMarkersToMap(DirectionsResult results, GoogleMap mMap) {
        mMap.addMarker(new MarkerOptions().position(new LatLng(results.routes[0].legs[0].startLocation.lat, results.routes[0].legs[0].startLocation.lng)).title(results.routes[0].legs[0].startAddress));
        mMap.addMarker(new MarkerOptions().position(new LatLng(results.routes[0].legs[0].endLocation.lat, results.routes[0].legs[0].endLocation.lng)).title(results.routes[0].legs[0].startAddress).snippet(getEndLocationTitle(results)));
    }

    void showLog(Object message, Throwable e) {
        String TAG = getString(R.string.app_name);
        Log.d(TAG, "Error " + String.valueOf(message), e);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mGoogleMap = googleMap;

        Dexter.withActivity(activity)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    @SuppressLint("MissingPermission")
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        mGoogleMap.getUiSettings().setMyLocationButtonEnabled(true);
                        mGoogleMap.setMyLocationEnabled(true);
                        mHandler.postDelayed(activity, delay);
                        showLog("onPermissionGranted");
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        if (response.isPermanentlyDenied()) {
                            showLog("isPermanentlyDenied");
                        } else {
                            showLog("onPermissionDenied");
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        showLog("onPermissionRationaleShouldBeShown");
                        token.continuePermissionRequest();
                    }

                }).check();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        final FrameLayout frameSource = findViewById(R.id.frameSource);
        final FrameLayout frameLayout = findViewById(R.id.frameDestination);
        PlaceAutocompleteFragment fragmentSource = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.fragmentSource);
        fragmentSource.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {

            }

            @Override
            public void onError(Status status) {

            }
        });

        fragmentSource.setText("Current Location");


        PlaceAutocompleteFragment fragmentDestination = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.fragmentDestination);
        fragmentDestination.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                frameSource.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(Status status) {
            }
        });

        fragmentDestination.setHint("Search for Direction");

        DateTime now = new DateTime();

        try {
            DirectionsResult result = DirectionsApi.newRequest(getGeoContext())
                    .mode(TravelMode.DRIVING)
                    .origin(new com.google.maps.model.LatLng(2.3,3.3))
                    .destination(new com.google.maps.model.LatLng(2.3,3.3))
                    .departureTime(now).await();
        } catch (ApiException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
