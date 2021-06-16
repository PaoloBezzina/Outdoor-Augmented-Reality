package com.example.geofencing;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private static final String TAG = "MapsActivity";
    @SuppressLint("StaticFieldLeak")
    public static ImageButton goToARbutton;
    public static ArrayList<LandmarkObject> landmarks = new ArrayList<>();
    private final float GEOFENCE_RADIUS = 40;
    private final int FINE_LOCATION_ACCESS_REQUEST_CODE = 10001;
    private final int BACKGROUND_LOCATION_ACCESS_REQUEST_CODE = 10002;
    private GoogleMap mMap;
    private GeofencingClient geofencingClient;
    private GeofenceHelper geofenceHelper;

    public static int getLandmark(String id) {

        for (int i = 0; i < landmarks.size(); i++)
            if (landmarks.get(i).name.equals(id)) {
                return i;
            }
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);

        geofencingClient = LocationServices.getGeofencingClient(this);
        geofenceHelper = new GeofenceHelper(this);

        goToARbutton = findViewById(R.id.button_start_AR);
        goToARbutton.setOnClickListener(v -> startARActivity());
        goToARbutton.setEnabled(false);
        goToARbutton.setClickable(false);
    }

    public void startARActivity() {
        Intent intent = new Intent(this, ArActivity.class);
        startActivity(intent);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng zejtun = new LatLng(35.85583, 14.53306);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(zejtun, 16));
        //mMap.addMarker(new MarkerOptions().position(zejtun).title("Marker on Zejtun"));

        enableUserLocation();

        initialiseGeofences();

        // To allow user to add their own geofences
        // mMap.setOnMapLongClickListener(this);
    }

    private void enableUserLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        } else {
            //Ask for permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                //We need to show user a dialog for displaying why the permission is needed and then ask for the permission...
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_ACCESS_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, FINE_LOCATION_ACCESS_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == FINE_LOCATION_ACCESS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //We have the permission
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                mMap.setMyLocationEnabled(true);
            }

        }

        if (requestCode == BACKGROUND_LOCATION_ACCESS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //We have the permission
                Toast.makeText(this, "You can access geofences...", Toast.LENGTH_SHORT).show();
            } else {
                //We do not have the permission..
                Toast.makeText(this, "Background location access is neccessary for geofences to trigger...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        if (Build.VERSION.SDK_INT >= 29) {
            //We need background permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                handleMapLongClick(latLng);
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    //We show a dialog and ask for permission
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_ACCESS_REQUEST_CODE);
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_ACCESS_REQUEST_CODE);
                }
            }

        } else {
            handleMapLongClick(latLng);
        }

    }

    private void handleMapLongClick(LatLng latLng) {
        mMap.addMarker(new MarkerOptions().position(latLng).title(latLng.toString()));
        addCircle(latLng, GEOFENCE_RADIUS);
        addGeofence(latLng, GEOFENCE_RADIUS, latLng.toString());
    }

    private void initialiseGeofences() {
        mMap.clear();
        LandmarkObject home = new LandmarkObject("Home", 35.8524, 14.5319, "1615", "This present church was constructed on and around the site occupied by the old medieval church, so that the present edifice dates from the sixteenth century. For about three centuries this building served as the parish church for all the south-east of Malta. One cannot fail to admire the Gothic and Romanesque styles of architecture. The primitive dome is the most ancient example of Maltese cupolas still standing. Of particular interest is also the orientation of the south-east transept of the church which is aligned with the bays of Marsxlokk, St.Thomas and Marsakala, since the church also doubled as a watch tower. The church occupies the highest point overlooking the harbours in the South, favourite landing place for barbery corsains.");
        LandmarkObject st_mary_chapel = new LandmarkObject("St. Marija Tal-Hlas Church", 35.8528, 14.5314, "1692", "Before this church there was another church dedicated to Our Lady which was dismantled in 1618. The building of the existing church coincided with the laying of the first stone of the new parish church of Żejtun, laid on the 25th November 1692.  The chapel is architecturally very modest and built in vernacular style; however it is elegantly located within this intricate urban space.  On the side facade of   the church there is a sun dial.  In the 17th Century this church was used as a burial ground for babies.");
        LandmarkObject sacred_heart_chapel = new LandmarkObject("Our Lady of Sacred Heart Chapel (Tas-Sinjura)", 35.8526, 14.5307, "1881", "The name of this chapel Tas-Sinjura (of the Signora) have a double meaning since the dedication of this chapel is to the Sacred Heart of Mary and at the same time the referring to the person who built the chapel, who was the noble Signora Margarita de Conti Manduca.");
        LandmarkObject saint_catherine_parish_church = new LandmarkObject("Parish Church of St. Catherine", 35.8551, 14.5335, "1720", "This majestic parish church was built between 1692 and 1720, according to the design of Lorenzo Gafà, one of Malta’s foremost architects. Żejtun parish church is considered by many as his masterpiece. It is an imposing edifice, with an attractive dome, Doric and Ionic pilasters, and massive cornices all round. Various works of art abound both in the Żejtun Parish Church and in its museum. ");
        LandmarkObject madonna_tal_hniena_church = new LandmarkObject("Our Lady of Mercy Church", 35.8507, 14.5265, "1575", "A centre of devotion to Our Lady of Sorrows in Żejtun, is the church of Our Lady of Mercy, in the zone of Bir id-Deheb. In 1575 a church already existed in this place, but in 1618 Bishop Cagliares ordered its closure. Three years later the Bishop reopened this church, dedicating it to St Mary but giving it the title of Our Lady of Mercy. During the fourth Sunday of September the villagers of Bir id-Deheb hamlet organise a feast dedicated to Our Lady with a procession along the streets forming part of this hamlet.");
        LandmarkObject bon_kunsill_church = new LandmarkObject("Mater Boni Consigli Chapel", 35.8536, 14.5318, "1750", "This is a private chapel annexed to Aedes Danielis Palace and built by the Noble Enrico Testaferrata in 1750. Emanuel Testaferrata Bonici, Marquis of San Vincenzo Ferreri, embellished this basilica with costly marble works and commissioned renowned artists to paint the dome and other paintings. The main façade of the basilica is built in Baroque style.");
        LandmarkObject st_gregory_church = new LandmarkObject("St. Gregory's Church - Old Parish Church", 35.8529, 14.5382, "1436", "This church was constructed on and around the site occupied by the old medieval church, so that the present edifice dates from the sixteenth century. For about three centuries this building served as the parish church for all the south-east of Malta. One cannot fail to admire the Gothic and Romanesque styles of architecture. The primitive dome is the most ancient example of Maltese cupolas still standing. Of particular interest is also the orientation of the south-east transept of the church which is aligned with the bays of Marsxlokk, St.Thomas and Marsakala, since the church also doubled as a watch tower. The church occupies the highest point overlooking the harbours in the South, favourite landing place for barbery corsains.");
        LandmarkObject st_angelo_church = new LandmarkObject("Church of St. Angelo", 35.8536, 14.5312, "1670", "This church is dedicated to the Carmelite Friar who suffered martyrdom in the Middle Ages. It was built by the nobleman and benefactor of Żejtun Parish Church, Girgor Bonici, in 1670 and houses some very good paintings by the renowned Giuseppe d’Arena. Members of the Bonici and Testaferrata Families, including Girgor Bonici himself, lie buried inside this church. It is a private church of Bonici and Testaferrata Family.");
        LandmarkObject hal_tmin_chapel = new LandmarkObject("St. Mary of Hal Tmin Chapel", 35.8559, 14.5488, "1597", "St. Mary of Ħal Tmin wayside chapel was built in 1597. The Chapel is cubic in form with a slight gabled roof and a bell cot. A side window sits on either side to the main entrance.");
        LandmarkObject jesus_of_nazareth_church = new LandmarkObject("Jesus of Nazareth Institute", 35.8545, 14.5370, "1926", "Jesus of Nazareth Institute is an orphanage founded by Josephine Curmi, the daughter of Dr Paul Curmi, Zejtun’s penultimate mayor at the time of the British. This very extensive building used to house as many as 200 orphans in the difficult years following the Second World War. The Home is run by the Jesus of Nazareth Sisters, a congregation which, though founded at Zejtun at the time of the depression of the thirties, has nowadays other Homes outside the Maltese Islands. At Christmas time a large mechanical crib is exhibited at this institute. It depicts Nativity scenes with paper-munched figurines and is very popular with the Maltese.");
        LandmarkObject st_clement_chapel = new LandmarkObject("St. Clement's Chapel", 35.8604, 14.5323, "1597", "This church is dedicated to Saint Clement after its founder Clement Tabone. It was built 1658 after in the place where the Żejtun villagers won against the Muslim invaders in the last siege of 1614.  This church houses some very good paintings, especially a painting of the Madonna with Jesus by the renowned artist Francesco Zahra. Each year on the first Wednesday after Easter starts from this church the traditional St. Gregory's procession with the participation of the Mdina Cathedral Chapter and the Archbishop of Malta.");
        LandmarkObject christ_saviour_chapel = new LandmarkObject("Christ Saviour Chapel", 35.8570, 14.5344, "1764", "In the past this was the main church for the hamlet of Bisqallin. Previous nowadays church there where two churches next to each other, one was dedicated to Our Saviour and the other one to the Visitation of Our Lady. Nowadays church was built in the 18th century. In this church there are paintings of Toussaints Busuttil and the titular painting of the Transfiguration of Christ on the Tabor is attributed to Francesco Zahra. Interesting aspect in this church are the stone benches known in Maltese as dkieken.");
        LandmarkObject st_nicholas_chapel = new LandmarkObject("St. Nicholas Chapel", 35.8487, 14.5474, "1504/1640", "St. Nicholas Chapel it is a small chapel in the outskirts of Żejtun, in the hamlet of Misraħ Strejnu. Nowadays is a private chapel forming part of an agro-tourism centre San Niklaw Estate. Though it is not precisely known when this Chapel of St Nicholas was erected, references made in old documents relate to the year 1504. Nonetheless in 1640 the chapel was rebuilt reflecting 17th century architecture. The Feast of St Nicholas is still celebrated every year in this small hamlet of Zejtun on the 6th of December. Nowadays is a private chapel forming part of an agro-tourism centre San Niklaw Estate.");

        landmarks.add(home);
        landmarks.add(st_mary_chapel);
        landmarks.add(sacred_heart_chapel);
        landmarks.add(saint_catherine_parish_church);
        landmarks.add(madonna_tal_hniena_church);
        landmarks.add(bon_kunsill_church);
        landmarks.add(st_gregory_church);
        landmarks.add(st_angelo_church);
        landmarks.add(hal_tmin_chapel);
        landmarks.add(jesus_of_nazareth_church);
        landmarks.add(st_clement_chapel);
        landmarks.add(christ_saviour_chapel);
        landmarks.add(st_nicholas_chapel);

        for (int i = 0; i < landmarks.size(); i++)
            initialiseGeofence(landmarks.get(i).latlng, landmarks.get(i).name);
    }

    private void initialiseGeofence(LatLng latLng, String geoFenceID) {

        //addMarker(latLng);
        mMap.addMarker(new MarkerOptions().position(latLng).title(geoFenceID));
        addCircle(latLng, GEOFENCE_RADIUS);
        addGeofence(latLng, GEOFENCE_RADIUS, geoFenceID);
    }

    private void addGeofence(LatLng latLng, float radius, String geofenceID) {

        Geofence geofence = geofenceHelper.getGeofence(geofenceID, latLng, radius, Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_DWELL | Geofence.GEOFENCE_TRANSITION_EXIT);
        GeofencingRequest geofencingRequest = geofenceHelper.getGeofencingRequest(geofence);
        PendingIntent pendingIntent = geofenceHelper.getPendingIntent();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "onSuccess: Geofence Added..."))
                .addOnFailureListener(e -> {
                    String errorMessage = geofenceHelper.getErrorString(e);
                    Log.d(TAG, "onFailure: " + errorMessage);
                });
    }

    private void addMarker(LatLng latLng) {
        MarkerOptions markerOptions = new MarkerOptions().position(latLng);
        mMap.addMarker(markerOptions);
    }

    private void addCircle(LatLng latLng, float radius) {
        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(latLng);
        circleOptions.radius(radius);
        circleOptions.strokeColor(Color.argb(255, 255, 0, 0));
        circleOptions.fillColor(Color.argb(64, 255, 0, 0));
        circleOptions.strokeWidth(4);
        mMap.addCircle(circleOptions);
    }
}
