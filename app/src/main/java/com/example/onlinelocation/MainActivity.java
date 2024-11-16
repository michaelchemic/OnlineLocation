package com.example.onlinelocation;

import android.app.AlertDialog;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import android.Manifest;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import android.os.Environment;
import android.widget.Button;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import org.xmlpull.v1.XmlSerializer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity implements AMapLocationListener,LocationSource,AMap.OnMyLocationChangeListener{
    //请求权限码
    private static final int REQUEST_PERMISSIONS = 9527;
    //声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    //声明AMapLocationClientOption对象
    public AMapLocationClientOption mLocationOption = null;
    private MapView mapView;
    //地图控制器
    private AMap aMap = null;
    //位置更改监听
    private OnLocationChangedListener mListener;
    //轨迹录制和文件GPX保存
    private List<LatLng> trackList = new ArrayList<>();
    //保存轨迹对象
    private Polyline trackPolyline;
    private List<Polyline> trackPolylines = new ArrayList<>(); // 用于保存所有轨迹线

    private boolean isRecording = false;
    private Button startButton, stopButton;
    private Button HelpButton;
    private static final int REQUEST_CODE_CREATE_FILE = 1; // 文件选择请求码

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        //按钮初始化
        startButton = findViewById(R.id.button1);
        stopButton = findViewById(R.id.button2);
        HelpButton = findViewById(R.id.button3);
        //初始化位置
        initLocation();
        //开始录制轨迹
        startButton.setOnClickListener(v -> startRecording());
        //停止录制轨迹并保存为GPX
        stopButton.setOnClickListener(v -> stopRecording());
        //初始化地图
        initMap(savedInstanceState);
        //检查android版本
        checkingAndroidVersion();

    }



    //开始轨迹录制
    private void startRecording() {
        if (!isRecording) {
            isRecording = true;
            trackList.clear();
            if (trackPolyline != null) {
                trackPolyline.remove(); // 清除之前的轨迹
            }
            mLocationClient.startLocation(); // 开始定位
            clearTrajectory();//清除轨迹
            Toast.makeText(this, "开始记录轨迹", Toast.LENGTH_SHORT).show();

        }
    }

    //停止轨迹录制
    private void stopRecording() {
        if (isRecording) {
            isRecording = false;
            mLocationClient.stopLocation(); // 停止定位
            saveTrackToGPX(trackList); // 保存轨迹为GPX文件
            Toast.makeText(this, "轨迹录制结束，文件已保存", Toast.LENGTH_SHORT).show();
        }
    }
    // 调用此方法弹出文件选择对话框
    private void saveTrackToGPX(List<LatLng> trackList) {
        if (trackList.isEmpty()) return;

        // 创建保存文件的 Intent
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/gpx+xml");  // 设置文件类型为 GPX
        intent.putExtra(Intent.EXTRA_TITLE, "track.gpx");  // 默认文件名为 track.gpx

        // 启动文件选择对话框
        startActivityForResult(intent, REQUEST_CODE_CREATE_FILE);
    }
    // 在 onActivityResult 中处理文件选择结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CREATE_FILE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();  // 获取用户选择的文件 URI
                saveGPXFile(uri);
            }
        }
    }
    // 保存轨迹为GPX格式文件
    private void saveGPXFile(Uri uri) {
        if (uri == null) return;

        try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
             OutputStreamWriter writer = new OutputStreamWriter(outputStream)) {

            // 初始化 XML 序列化器
            XmlSerializer serializer = android.util.Xml.newSerializer();
            serializer.setOutput(writer);

            serializer.startDocument("UTF-8", true);
            serializer.startTag("", "gpx");
            serializer.attribute("", "version", "1.1");
            serializer.attribute("", "xmlns", "http://www.topografix.com/GPX/1/1");

            serializer.startTag("", "trk");
            serializer.startTag("", "trkseg");

            // 写入轨迹点
            for (LatLng latLng : trackList) {
                serializer.startTag("", "trkpt");
                serializer.attribute("", "lat", String.valueOf(latLng.latitude));
                serializer.attribute("", "lon", String.valueOf(latLng.longitude));

                // 添加时间戳（可使用实际时间）
                serializer.startTag("", "time");
                serializer.text("2024-11-15T00:00:00Z"); // 示例时间
                serializer.endTag("", "time");

                serializer.endTag("", "trkpt");
            }

            serializer.endTag("", "trkseg");
            serializer.endTag("", "trk");
            serializer.endTag("", "gpx");

            serializer.endDocument();
            writer.flush();

            // 提示用户保存成功
            Toast.makeText(this, "GPX文件已保存：" + uri.getPath(), Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "保存GPX文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查Android版本
     */
    private void checkingAndroidVersion() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            //Android6.0及以上先获取权限再定位
            requestPermission();

        }else {
            //Android6.0以下直接定位
             mLocationClient.startLocation();
        }
    }
    /**
     * 动态请求权限
     */
    @AfterPermissionGranted(REQUEST_PERMISSIONS)
    private void requestPermission() {
        String[] permissions = {
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        if (EasyPermissions.hasPermissions(this, permissions)) {
            //true 有权限 开始定位
            showMsg("已获得权限，正在定位！");
            //启动定位
            mLocationClient.startLocation();
        } else {
            //false 无权限
            EasyPermissions.requestPermissions(this, "权限不足！", REQUEST_PERMISSIONS, permissions);
        }
    }
    /**
     * 请求权限结果
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //设置权限请求结果
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    /**
     * Toast提示
     * @param msg 提示内容
     */
    private void showMsg(String msg){
        Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();
    }

    /**
     * 初始化定位
     */
    private void initLocation() {
        //初始化定位
        try {
            mLocationClient = new AMapLocationClient(getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mLocationClient != null) {
            //设置定位回调监听
            mLocationClient.setLocationListener(this);
            //初始化AMapLocationClientOption对象
            mLocationOption = new AMapLocationClientOption();
            //设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            //获取最近3s内精度最高的一次定位结果：
            //设置setOnceLocationLatest(boolean b)接口为true，启动定位时SDK会返回最近3s内精度最高的一次定位结果。如果设置其为true，setOnceLocation(boolean b)接口也会被设置为true，反之不会，默认为false。
            mLocationOption.setOnceLocationLatest(true);
            //设置是否返回地址信息（默认返回地址信息）
            mLocationOption.setNeedAddress(true);
            //设置定位请求超时时间，单位是毫秒，默认30000毫秒，建议超时时间不要低于8000毫秒。
            mLocationOption.setHttpTimeOut(20000);
            //关闭缓存机制，高精度定位会产生缓存。
            mLocationOption.setLocationCacheEnable(false);
            //给定位客户端对象设置定位参数
            mLocationClient.setLocationOption(mLocationOption);
        }
    }

    /**
     * 接收异步返回的定位结果
     *
     * @param aMapLocation
     */
    @Override
    public void onLocationChanged(AMapLocation aMapLocation) {
        if (aMapLocation != null) {
            if (aMapLocation.getErrorCode() == 0) {
                //地址
                String address = aMapLocation.getAddress();
                //经纬度
                String longitude = String.valueOf(aMapLocation.getLongitude());
                String latitude = String.valueOf(aMapLocation.getLatitude());
//                aMapLocation.getLocationType();//获取当前定位结果来源，如网络定位结果，详见定位类型表
//                aMapLocation.getAccuracy();//获取精度信息
//                aMapLocation.getCountry();//国家信息
//                aMapLocation.getProvince();//省信息
//                aMapLocation.getCity();//城市信息
//                aMapLocation.getDistrict();//城区信息
//                aMapLocation.getStreet();//街道信息
//                aMapLocation.getStreetNum();//街道门牌号信息
//                aMapLocation.getCityCode();//城市编码
//                aMapLocation.getAdCode();//地区编码
//                aMapLocation.getAoiName();//获取当前定位点的AOI信息
//                aMapLocation.getBuildingId();//获取当前室内定位的建筑物Id
//                aMapLocation.getFloor();//获取当前室内定位的楼层
//                aMapLocation.getGpsAccuracyStatus();//获取GPS的当前状态
                if(address == null){
                    showMsg("无法获取位置！");
                }else {
                    StringBuffer stringBuffer = new StringBuffer();
                    stringBuffer.append("经度：" + longitude + "\n");
                    stringBuffer.append("纬度：" + latitude + "\n");
                    stringBuffer.append("地址：" + address + "\n");
                    Log.d("MainActivity",stringBuffer.toString());
                    //showMsg("当前位置："+address);
                    //停止定位后，本地定位服务不会被销毁。
                    //mLocationClient.stopLocation();

                    LatLng currentLatLng = new LatLng(aMapLocation.getLatitude(), aMapLocation.getLongitude());
                    trackList.add(currentLatLng); // 将新位置添加到轨迹列表


                    //显示地图定位结果
                    if(mListener!=null){
                       // trackPolyline.remove();
                        //显示系统图标
                        mListener.onLocationChanged(aMapLocation);
                    }
                    //执行相关功能
                    plotTrajectory();
                }

            } else {
                //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
                Log.e("AmapError", "location Error, ErrCode:"
                        + aMapLocation.getErrorCode() + ", errInfo:"
                        + aMapLocation.getErrorInfo());
            }
        }
    }

    //绘制轨迹
    protected void plotTrajectory() {
        // 绘制轨迹
        PolylineOptions polylineOptions = new PolylineOptions()
                .addAll(trackList)
                .color(0xFFFF0000) // 设置轨迹线为红色
                .width(10);

        Polyline polyline = aMap.addPolyline(polylineOptions); // 在地图上绘制轨迹
        trackPolylines.add(polyline); // 将轨迹线保存到列表中
    }

    protected void clearTrajectory() {
        // 清除所有轨迹
        for (Polyline polyline : trackPolylines) {
            polyline.remove();  // 移除每一条轨迹线
        }
        trackPolylines.clear();  // 清空轨迹线列表
        Toast.makeText(this, "所有轨迹已清除", Toast.LENGTH_SHORT).show();
    }



    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        mapView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        mapView.onPause();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        mapView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        mLocationClient.onDestroy();
        mapView.onDestroy();
    }

    /**
     * 初始化地图
     * @param savedInstanceState
     */
    private void initMap(Bundle savedInstanceState) {
        mapView = findViewById(R.id.map_view);
        //在activity执行onCreate时执行mMapView.onCreate(savedInstanceState)，创建地图
        mapView.onCreate(savedInstanceState);
        //初始化地图控制器对象
        aMap = mapView.getMap();
        // 设置定位监听
        aMap.setLocationSource(this);
        // 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
        aMap.setMyLocationEnabled(true);
    }
    /**
     * 激活定位
     */
    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        mListener = onLocationChangedListener;
        if (mLocationClient != null) {
            mLocationClient.startLocation();//启动定位
        }
    }

    /**
     * 停止定位
     */
    @Override
    public void deactivate() {
        mListener = null;
        if (mLocationClient != null) {
            mLocationClient.stopLocation();
            mLocationClient.onDestroy();
        }
        mLocationClient = null;
    }


    @Override
    public void onMyLocationChange(Location location) {

    }
}

