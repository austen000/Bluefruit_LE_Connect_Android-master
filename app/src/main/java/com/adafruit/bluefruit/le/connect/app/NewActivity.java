package com.adafruit.bluefruit.le.connect.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.support.v7.app.AppCompatActivity;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.adafruit.bluefruit.le.connect.R;
import com.adafruit.bluefruit.le.connect.app.settings.ConnectedSettingsActivity;
import com.adafruit.bluefruit.le.connect.app.settings.MqttUartSettingsActivity;
import com.adafruit.bluefruit.le.connect.ble.BleManager;
import com.adafruit.bluefruit.le.connect.ble.BleUtils;
import com.adafruit.bluefruit.le.connect.mqtt.MqttManager;
import com.adafruit.bluefruit.le.connect.mqtt.MqttSettings;
import com.scichart.charting.ClipMode;
import com.scichart.charting.model.AnnotationCollection;
import com.scichart.charting.model.dataSeries.XyDataSeries;
import com.scichart.charting.modifiers.AxisDragModifierBase;
import com.scichart.charting.modifiers.ModifierGroup;
import com.scichart.charting.visuals.SciChartSurface;
import com.scichart.charting.visuals.annotations.AnnotationCoordinateMode;
import com.scichart.charting.visuals.annotations.HorizontalAnchorPoint;
import com.scichart.charting.visuals.annotations.LabelPlacement;
import com.scichart.charting.visuals.annotations.TextAnnotation;
import com.scichart.charting.visuals.annotations.VerticalAnchorPoint;
import com.scichart.charting.visuals.annotations.VerticalLineAnnotation;
import com.scichart.charting.visuals.axes.IAxis;
import com.scichart.charting.visuals.renderableSeries.IRenderableSeries;
import com.scichart.core.annotations.Orientation;
import com.scichart.core.framework.UpdateSuspender;
import com.scichart.data.model.DateRange;
import com.scichart.data.model.DoubleRange;
import com.scichart.drawing.utility.ColorUtil;
import com.scichart.extensions.builders.SciChartBuilder;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static com.adafruit.bluefruit.le.connect.app.UartInterfaceActivity.UUID_SERVICE;

/**
 * Created by auste on 9/12/2017.
 *
 * Note: numberOfSamples is always less than or equal to serialDataBufferLength.(Note: choosing a
 * value of 1 for numberOfSamples disables the filter function). The field serialDataBufferLength
 * sets the size of the serial data buffer and therefore sets the upper limit on the number of
 * samples the class has access to for real-time processing.
 */

public class NewActivity extends UartInterfaceActivity {

    private static final String TAG = "NewActivity";

    protected Context mainContext = this;
    private Button buttonBackToMain, buttonStopRun, buttonRecordMode, buttonMarkData;
    private EditText editMarkText;

    private final int serialDataBufferLength = 1;
    private final int numberOfSamples = 1;
    private final int fifoLength = 8 * 60 * 60 * 1000;                                                   //Once fifoLength is exceeded, old date will be deleted as new data is appended
    private final int window = 5500;
    private final int recordBufferLength = 200;                                                     //in ms
    private final long interval = 50;                                                               //update plot every 50ms
    private final long annoInt = 4;                                                                 //update annotations every 5ms
    private int recordBufferCount;
    private int begin = (8 * 60 * 60 * 1000) - (300 + (int) annoInt);                                        //add 8 hours since default hours is 16
    private int currentApiVersion;
    private int surfaceDivider = 10;
    private int recordIV = 200;
    private double[] serialData = new double[serialDataBufferLength];
    private double serialDataFinal = Double.NaN;

    private boolean stopUpdate = false;
    private boolean recording = false;
    //protected boolean calibrate_yAxis = false;

    private boolean displayVoltage = true;
    private boolean displayConductance = true;
    private boolean titleSet = true;

    private boolean showProgressBar = false;

    private String[] entries = new String[recordBufferLength];
    private String FILENAME = "data_logger.csv";
    private String label = "";

    private File path = Environment.getExternalStorageDirectory();
    private File curDir = new File(path.getAbsolutePath()
            + "/Android/data/com.example.auste.blunobeetleplot/");
    private File file = new File(curDir, FILENAME);


    private Date x = new Date(begin);

    private double pwm = Double.NaN;
    private double pwmLast = Double.NaN;                                                          // values used for detecting onset of periodic calibration
    private int pwmStabilityIndex = 0;
    private int pwmReq = 10;
    //private double b = 1.1985014; //0.78679571;
    //private double a = 0.00225241; //0.003303402367;
    private float minimumFreeSpace = 50;

    final CShowProgress cShowProgress = CShowProgress.getInstance();

    BooVariable bv = new BooVariable();
    //IntVariable iv = new IntVariable();

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }catch(Exception e){
            e.printStackTrace();
        }
        setContentView(R.layout.activity_plotter);
        //linkActivity(); //from non-existent BlunoLibrary class

        immersiveMode();

        //cShowProgress.showProgress(NewActivity.this);

        bv.setBoo(showProgressBar);

        bv.setListener(new BooVariable.ChangeListener() {
            @Override
            public void onChange() {
                if(showProgressBar && !stopUpdate) {
                    cShowProgress.showProgress(NewActivity.this);
                }else cShowProgress.hideProgress();

                //System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!------" + showProgressBar);
            }
        });

        //final MediaPlayer mpBeep = MediaPlayer.create(mainContext, R.raw.beep);                   // sounds used for saturation warning
        //final MediaPlayer mpBleep = MediaPlayer.create(mainContext, R.raw.bleep);

        final SciChartSurface surface = (SciChartSurface) findViewById(R.id.chartView);
        SciChartBuilder.init(this);                                                                 // Initialize the SciChartBuilder
        final SciChartBuilder sciChartBuilder = SciChartBuilder.instance();                         // Obtain the SciChartBuilder instance

        final IAxis xAxis = sciChartBuilder.newDateAxis()                                           // Create a Date X axis with mm:ss:S format
                .withAxisTitle("Time")
                .withTextFormatting("HH:mm:ss.SS")
                .build();

        final IAxis yAxis = sciChartBuilder.newNumericAxis()                                        // Create a numeric Y axis
                .withAxisTitle("Voltage [V]")
                .withVisibleRange(0.0, 3.5)                                                             // Adjust initial Y Axis view
                .build();
        final int fifoCapacity = (int)(fifoLength / interval);                                      // Set FIFO capacity to 60s on DataSeries
        Collections.addAll(surface.getYAxes(), yAxis);                                              // Add the Y axis to the YAxes collection of the surface
        Collections.addAll(surface.getXAxes(), xAxis);                                              // Add the X axis to the XAxes collection of the surface


        final XyDataSeries<Date,Double> mountainData =
                sciChartBuilder.newXyDataSeries(Date.class, Double.class)                          //Build XyDataseries for mountainData (not recording)
                        .withFifoCapacity(fifoCapacity)
                        .build();
        final XyDataSeries<Date,Double> mountainRecordData =
                sciChartBuilder.newXyDataSeries(Date.class, Double.class)
                        .withFifoCapacity(fifoCapacity)
                        .build();
        final IRenderableSeries mountainSeries = sciChartBuilder.newMountainSeries()                //Create and configure a mountain series
                .withDataSeries(mountainData)
                .withStrokeStyle(0xAA62D5FF, 1f)
                .withAreaFillLinearGradientColors(0x88309EDF, 0x88090E11)
                .build();
        final IRenderableSeries mountainSeriesRecord = sciChartBuilder.newMountainSeries()          //Create and configure a mountain series for recording
                .withDataSeries(mountainRecordData)
                .withStrokeStyle(0xAAFF4346, 1f)
                .withAreaFillLinearGradientColors(0x88ED0105, 0x88090E11)
                .build();
        surface.getRenderableSeries().add(mountainSeries);                                          // Add RenderableSeries onto the SciChartSurface
        surface.getRenderableSeries().add(mountainSeriesRecord);



        ModifierGroup chartModifiers = sciChartBuilder.newModifierGroup()                           // Create interactivity modifiers
                .withPinchZoomModifier().withReceiveHandledEvents(true).build()
                .withZoomPanModifier().withReceiveHandledEvents(true).build()
                .build();/*
        ModifierGroup additionalModifiers = sciChartBuilder.newModifierGroup()                      // Add a bunch of interaction modifiers to a ModifierGroup
                .withPinchZoomModifier().build()
                .withZoomPanModifier().withReceiveHandledEvents(true).build()
                .withZoomExtentsModifier().withReceiveHandledEvents(true).build()
                .withXAxisDragModifier().withReceiveHandledEvents(true)
                .withDragMode(AxisDragModifierBase.AxisDragMode.Scale)
                .withClipModex(ClipMode.None).build()                           //fix this
                .withYAxisDragModifier().withReceiveHandledEvents(true)
                .withDragMode(AxisDragModifierBase.AxisDragMode.Pan).build()
                .build();*/

        ModifierGroup cursorModifier = sciChartBuilder.newModifierGroup()                           // Create and configure a CursorModifier
                .withCursorModifier().withShowTooltip(true).build()
                .build();
/*     ModifierGroup legendModifier = sciChartBuilder.newModifierGroup()                           // Create a LegendModifier and configure a chart legend
                .withLegendModifier()
                .withOrientation(Orientation.VERTICAL)
                .withPosition(Gravity.RIGHT | Gravity.TOP, 10)
                .build()
                .build();
                */
        Collections.addAll(surface.getChartModifiers(), chartModifiers);
        //surface.getChartModifiers().add(legendModifier);                                            // Add the LegendModifier to the SciChartSurface
        //surface.getChartModifiers().add(additionalModifiers);  //fix this                                     // Add the modifiers to the SciChartSurface
        surface.getChartModifiers().add(cursorModifier);

        final AnnotationCollection myAnnotations = new AnnotationCollection();
        surface.setAnnotations(myAnnotations);

        editMarkText =(EditText) findViewById(R.id.editMarkText);

        buttonBackToMain = (Button) findViewById(R.id.buttonBackToMain);                            //Initialize buttonBackToMain
        buttonBackToMain.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: 'Back to Main' was pressed.");
                Intent intent = new Intent(NewActivity.this, UartActivity.class);
                startActivity(intent);
            }
        });

        buttonBackToMain.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // TODO Auto-generated method stub
                if(displayVoltage){
                    displayVoltage = false;
                    displayConductance = true;
                    titleSet = false;
                }else{
                    displayVoltage = true;
                    displayConductance = false;
                    titleSet = false;
                }
                return true;
            }
        });

        buttonMarkData = (Button) findViewById(R.id.buttonMarkData);
        buttonMarkData.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Log.d(TAG, "onClick: 'Mark' was pressed.");
                label = editMarkText.getText().toString();
                String holdLabel = label;


                InputMethodManager inputManager = (InputMethodManager)                              //hide keyboard after mark button pressed
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow((null == getCurrentFocus()) ? null :
                        getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                editMarkText.clearFocus();

                if(holdLabel.startsWith("Label")) {
                    int markIndex = 0;
                    try {
                        markIndex = Integer.parseInt(holdLabel.substring(5));
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    markIndex++;
                    String newLabel = "Label" + markIndex;
                    editMarkText.setText(newLabel);
                }
                else{
                    String newLabel = "Label1";
                    editMarkText.setText(newLabel);
                }

                //Collections.addAll(surface.getAnnotations(), textAnnotationBuilder(holdLabel));     // Add the annotations to the Annotations Collection of the surface
                //Collections.addAll(surface.getAnnotations(), verticalLineAnnotationBuilder(holdLabel));
                myAnnotations.add(verticalLineAnnotationBuilder(holdLabel));
                myAnnotations.add(textAnnotationBuilder(holdLabel));



            }
        });

        buttonStopRun = (Button) findViewById(R.id.buttonStopRun);                                  //Initialized buttonStopRun: pauses the plot
        buttonStopRun.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                if (stopUpdate) {
                    stopUpdate = false;
                    System.out.println("buttonStopRun pressed: plot will resume.");
                } else {
                    stopUpdate = true;
                    System.out.println("buttonStopRun pressed: plot has been stopped.");
                }
            }
        });

        buttonRecordMode = (Button) findViewById(R.id.buttonRecordMode);                            //Initialized buttonRecord: start/stop recording, data automatically
        buttonRecordMode.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {                                                        //write to a .csv file when recording is enabled
                if (recording) {
                    recording = false;
                    System.out.println("buttonRecordMode clicked: recording stopped.");
                    Toast.makeText(getApplicationContext(), "Stopped", Toast.LENGTH_SHORT).show();
                } else {
                    if(megabytesAvailable(curDir)>minimumFreeSpace) {                                             //checks to see if there is at least 50Mb of free space
                        recording = true;
                        System.out.println("buttonRecordMode clicked: recording started.");
                        System.out.println("Storage available : --- " + megabytesAvailable(curDir) + "Mb");
                        Toast.makeText(getApplicationContext(), "Recording", Toast.LENGTH_SHORT).show();
                    }else {
                        recording = false;
                        Toast.makeText(getApplicationContext(), "Memory is too full. Unable to record", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        TimerTask updateDataTask = new TimerTask() {
            @Override
            public void run() {

                UpdateSuspender.using(surface, new Runnable() {

                    @Override
                    public void run() {
                        if (!stopUpdate) {

                            try {
                                serialDataFinal = (double)Integer.parseInt(UartInterfaceActivity.string1)*3.3/16383.0;
                                //System.out.println(UartInterfaceActivity.string1);
                            }catch(Exception e){
                                e.printStackTrace();
                            }

                            if(pwmStabilityIndex < pwmReq && !showProgressBar) {
                                //showProgressBar = true;
                                //System.out.println("!!! no");
                                bv.setBoo(true);
                            }
                            if(pwmStabilityIndex >= pwmReq && showProgressBar){
                                System.out.println("!!! yes");
                                //showProgressBar = false;
                                bv.setBoo(false);
                            }
                            if (recording && x.getTime()%interval == 0) {
                                mountainRecordData.append(x, serialDataFinal);
                                if(x.getTime()% recordIV == 0){
                                    recordLogData(x, serialDataFinal);
                                }
                                mountainData.append(x, Double.NaN);/*
                                if (serialData[0] > 950 || serialData[0] < 100) {
                                    if (serialData[0] > 950) {
                                        playSound(mpBeep, surface);
                                    } else {
                                        playSound(mpBleep, surface);
                                    }

                                }*/
                            } else if(x.getTime()%50 == 0){
                                mountainData.append(x, serialDataFinal);//150*Math.sin(x * 0.01) + 600);
                                mountainRecordData.append(x, Double.NaN);
                            }
                            //surface.zoomExtentsX();                                             // Zoom X axis to fit the viewport
                            if (x.getTime() < window) {
                                if (x.getTime() < window + begin) {
                                    DateRange dateRange = new DateRange(new Date(begin), new Date(x.getTime()));
                                    xAxis.setVisibleRange(dateRange);
                                } else {
                                    DateRange dateRange = new DateRange(new Date(++begin), new Date((x.getTime())));
                                    xAxis.setVisibleRange(dateRange);
                                }
                            } else {
                                DateRange dateRange = new DateRange(new Date(x.getTime() - window), new Date(x.getTime() - 2*interval));
                                xAxis.setVisibleRange(dateRange);
                            }
                        }
                        if(displayVoltage && !(titleSet)){
                            yAxis.setAxisTitle("Voltage [V]");
                            yAxis.setVisibleRange(new DoubleRange(-0.0d, 3.5d));
                            titleSet = true;
                        }
                        else if(displayConductance && !(titleSet)){
                            yAxis.setAxisTitle("Skin Conductance [μS]");
                            yAxis.setVisibleRange(new DoubleRange(1d, 9d));
                            titleSet = true;
                        }
                    }
                });
            }
        };
        TimerTask updateAnnotationTask = new TimerTask() {
            @Override
            public void run() {
                UpdateSuspender.using(surface, new Runnable() {

                    @Override
                    public void run() {
                        if (!stopUpdate) {                                                           // Redraw the annotations without flickering
                            x.setTime(x.getTime() + annoInt);                                             // Advance X Axis Date object by 1ms
                            int size = myAnnotations.size();
                            if (size > 0) {
                                int index = 0;
                                for (int i = 0; i < size; i++) {
                                    myAnnotations.get(size - 1 - i).refresh();
                                    index++;
                                    if (index >= 3) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                });
            }
        };
        Timer timer = new Timer();
        long delay = 0;
        timer.scheduleAtFixedRate(updateDataTask, delay, interval/surfaceDivider);
        timer.scheduleAtFixedRate(updateAnnotationTask, delay, annoInt);

        //calibration();

    }
    /*
        protected void onResume(){
            super.onResume();
            System.out.println("BlUNOActivity onResume");
            onResumeProcess();														                //onResume Process by BlunoLibrary
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            onActivityResultProcess(requestCode, resultCode, data);					                    //onActivityResult Process by BlunoLibrary
            super.onActivityResult(requestCode, resultCode, data);
        }

        @Override
        protected void onPause() {
            super.onPause();
            onPauseProcess();														                    //onPause Process by BlunoLibrary
        }

        protected void onStop() {
            super.onStop();
            onStopProcess();														                    //onStop Process by BlunoLibrary
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            onDestroyProcess();														                    //onDestroy Process by BlunoLibrary
        }
    */
    public void recordLogData( Date x, Double y) {
        if(megabytesAvailable(curDir) > minimumFreeSpace){                                                         //If minimum 0.1mB space is available, record data
            //DateFormat formatter = new SimpleDateFormat("mm:ss", Locale.US);
            int millis = (int)x.getTime();
            String dateFormatted = String.format(Locale.US, "%02d:%02d:%02d.%03d",

                    TimeUnit.MILLISECONDS.toHours(millis)-8,                                            //Offset by 8 hours since default hours is 16
                    TimeUnit.MILLISECONDS.toMinutes(millis)%60,
                    TimeUnit.MILLISECONDS.toSeconds(millis)%60,
                    millis%1000);

            String entry = dateFormatted + ", " + y + "," + label + "\n";
            label = "";                                                                                 //clear label once recorded
            entries[recordBufferCount] = entry;
            recordBufferCount++;

            if(recordBufferCount >= recordBufferLength) {
                recordBufferCount = 0;
                if (!curDir.exists()) {
                    System.out.println("recordLogData: save directory does not exist. Creating new...");
                    //noinspection ResultOfMethodCallIgnored
                    curDir.mkdirs();
                }
                try {
                    FileOutputStream out = new FileOutputStream(file, true);
                    for(int i = 0; i < recordBufferLength; i++ ) {
                        out.write(entries[i].getBytes());
                    }
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        else {
            System.out.println("recordLogData: System storage is full.");
            stopUpdate = true;

        }
    }/*
    @Override
    public  void onConectionStateChange(connectionStateEnum theconnectionStateEnum){
    }*/

    public  void onSerialReceived(String theString){                                                //raw data contains 16-20 characters including \\n
        String[] lines = theString.split("\\r?\\n");
        String[] data = lines[1].split("~");                                                        //~ separates A0 and pwm values
        String numberString = data[0];
        String pwmString = data[1];

        if(displayConductance){
            //System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!=======" + "======" );
            if(serialDataBufferLength == 1){
                double x1 = (double)Integer.parseInt(pwmString);
                pwmLast = pwm;
                pwm = x1;
                pwmAdjust();
                if(pwmStabilityIndex < pwmReq && !showProgressBar) {
                    showProgressBar = true;
                    bv.setBoo(true);
                }
                if(pwmStabilityIndex == pwmReq && showProgressBar) {
                    showProgressBar = false;
                    bv.setBoo(false);
                }
                double x2 = (double)Integer.parseInt(numberString);
                if(!showProgressBar) { //serialDataFinal = 1000000.0/(761.513*x1*x1 - 10.8296*x1*x2 + 0.0344057*x2*x2 - 187831.0*x1 + 1344.24*x2 + 11630040.0);
                    if(pwm < 135.0) {
                        serialDataFinal = 1000.0/(0.252397*x1*x1 - 0.00360717*x1*x2 + 0.0000140559*x2*x2 - 54.9522*x1 + 0.389537*x2 + 2983.80);
                    }else if(pwm >= 135.0 && pwm < 140.0){
                        serialDataFinal = 1000.0/(0.373206*x1*x1 - 0.0050460*x1*x2 + 0.0000196542*x2*x2 - 86.9380*x1 + 0.579009*x2 + 5100.73);
                    }else if(pwm >= 140.0 && pwm < 145.0){
                        serialDataFinal = 1000.0/(0.613866*x1*x1 - 0.00882469*x1*x2 + 0.0000343647*x2*x2 - 152.32*x1 + 1.09304*x2 + 9539.35);
                    }else if(pwm >= 145.0 && pwm < 150.0){
                        serialDataFinal = 1000.0/(1.08484*x1*x1 - 0.0144539*x1*x2 + 0.0000533323*x2*x2 - 286.117*x1 + 1.88979*x2 + 19041.4);
                    }else if(pwm >= 150.0 && pwm < 155.0){
                        serialDataFinal = 1000.0/(1.87935*x1*x1 - 0.0251115*x1*x2 + 0.0000936412*x2*x2 - 519.119*x1 + 3.44477*x2 +  36122.1);
                    }else if(pwm >= 155.0 && pwm < 160.0){
                        serialDataFinal = 1000.0/(5.44162*x1*x1 - 0.0743262*x1*x2 + 0.000262991*x2*x2 - 1603.28*x1 + 10.9319*x2 + 118613.0);
                    }else if(pwm >= 160.0 && pwm < 165.0){
                        serialDataFinal = 1000.0/(1.73030*x1*x1*x1 -  0.0300619*x1*x1*x2 + 0.000212646*x1*x2*x2 - 0.000000602673*x2*x2*x2 - 809.358*x1*x1 + 9.30654*x1*x2 - 0.0327613*x2*x2 + 126313.0*x1 - 720.951*x2 - 6576260.0);
                    }else{
                        double serialDataFinalx = 1000.0/(55.4381*x1*x1 - 0.734834*x1*x2 + 0.00249130*x2*x2 - 17612.8*x1 + 116.596*x2 + 1400350.0);
                        if(serialDataFinalx < 0.2){
                            serialDataFinal = serialDataFinalx - 0.13;
                        }else{
                            serialDataFinal = serialDataFinalx;
                        }
                    }
                }
                serialData[0] = (double)Integer.parseInt(numberString);

                //System.out.println();
                //System.out.print("!!!!!!!!!!!!!!!!!!!!!!!!!!!-------" + pwmStabilityIndex + "-----------!!!!!!!!!!!!!!!!!");
                //System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!-------" + x1 + "~" + x2 + " : " + serialDataFinal + "-----------!!!!!!!!!!!!!!!!!");

            }else{                                                                                  //displaying voltage [0-5] instead
                double x = (double)Integer.parseInt(numberString);
                serialDataFinal = 5.0*(x / 1023);
            }
        }
        else {
            try {
                double x1 = (double)Integer.parseInt(pwmString);
                pwmLast = pwm;
                pwm = x1;
                pwmAdjust();
                double x2 = (double)Integer.parseInt(numberString);
                for (int i = serialDataBufferLength - 1; i >= 0; i--) {
                    if (i != 0)
                        serialData[i] = serialData[i - 1];
                    else {
                        if(!showProgressBar) { //serialData[i] = 1000000.0/(761.513*x1*x1 - 10.8296*x1*x2 + 0.0344057*x2*x2 - 187831.0*x1 + 1344.24*x2 + 11630040.0);
                            if(pwm < 135.0) {
                                serialData[i] = 1000.0/(0.252397*x1*x1 - 0.00360717*x1*x2 + 0.0000140559*x2*x2 - 54.9522*x1 + 0.389537*x2 + 2983.80);
                            }else if(pwm >= 135.0 && pwm < 140.0){
                                serialData[i] = 1000.0/(0.373206*x1*x1 - 0.0050460*x1*x2 + 0.0000196542*x2*x2 - 86.9380*x1 + 0.579009*x2 + 5100.73);
                            }else if(pwm >= 140.0 && pwm < 145.0){
                                serialData[i] = 1000.0/(0.613866*x1*x1 - 0.00882469*x1*x2 + 0.0000343647*x2*x2 - 152.32*x1 + 1.09304*x2 + 9539.35);
                            }else if(pwm >= 145.0 && pwm < 150.0){
                                serialData[i] = 1000.0/(1.08484*x1*x1 - 0.0144539*x1*x2 + 0.0000533323*x2*x2 - 286.117*x1 + 1.88979*x2 + 19041.4);
                            }else if(pwm >= 150.0 && pwm < 155.0){
                                serialData[i] = 1000.0/(1.87935*x1*x1 - 0.0251115*x1*x2 + 0.0000936412*x2*x2 - 519.119*x1 + 3.44477*x2 +  36122.1);
                            }else if(pwm >= 155.0 && pwm < 160.0){
                                serialData[i] = 1000.0/(5.44162*x1*x1 - 0.0743262*x1*x2 + 0.000262991*x2*x2 - 1603.28*x1 + 10.9319*x2 + 118613.0);
                            }else if(pwm >= 160.0 && pwm < 165.0){
                                serialData[i] = 1000.0/(1.73030*x1*x1*x1 -  0.0300619*x1*x1*x2 + 0.000212646*x1*x2*x2 - 0.000000602673*x2*x2*x2 - 809.358*x1*x1 + 9.30654*x1*x2 - 0.0327613*x2*x2 + 126313.0*x1 - 720.951*x2 - 6576260.0);
                            }else{
                                double serialDataFinalx = 1000.0/(55.4381*x1*x1 - 0.734834*x1*x2 + 0.00249130*x2*x2 - 17612.8*x1 + 116.596*x2 + 1400350.0);
                                if(serialDataFinalx < 0.2){
                                    serialData[i] = serialDataFinalx - 0.13;
                                }else{
                                    serialData[i] = serialDataFinalx;
                                }
                            }
                        }else serialData[i] = serialData[serialDataBufferLength - 1];

                    }
                }

            } catch (NumberFormatException e) {
                System.out.println("onSerialReceived: ERROR characters other than numbers found in the string:--" + numberString + "--or--" + pwmString);
            }
            int sum = 0;
            for (int i = 0; i < numberOfSamples; i++) {
                sum += serialData[i];
            }
            //System.out.println(serialData[0] + "   :   " + serialDataFinal);
            serialDataFinal = (double) sum / (double) numberOfSamples;
            //playSound(this.onCreatePanelView(R.layout.activity_plotter));

            //serialDataFinal = serialData[serialDataBufferLength - 1];
        }
    }

    private TextAnnotation textAnnotationBuilder(String holdLabel){
        SciChartBuilder sciChartBuilder = SciChartBuilder.instance();
        TextAnnotation textAnnotation = sciChartBuilder.newTextAnnotation()                         // Create a TextAnnotation and specify the inscription and position for it
                .withX1((double)x.getTime())
                .withY1(3.0)
                .withText(" " + holdLabel)
                .withHorizontalAnchorPoint(HorizontalAnchorPoint.Left)
                .withVerticalAnchorPoint(VerticalAnchorPoint.Center)
                .withFontStyle(16, ColorUtil.White)
                .build();
        return textAnnotation;
    }
    private VerticalLineAnnotation verticalLineAnnotationBuilder(String holdLabel){
        SciChartBuilder sciChartBuilder = SciChartBuilder.instance();
        if(recording){
            VerticalLineAnnotation verticalLine = sciChartBuilder.newVerticalLineAnnotation()           // Create a VerticalLineAnnotation
                    .withX1(new Date(x.getTime()))
                    .withStroke(1f, 0xc0db9dff)
                    .withVerticalGravity(Gravity.FILL_VERTICAL)
                    .withAnnotationLabel()
                    .withAnnotationLabel(LabelPlacement.Top, holdLabel)
                    .build();
            return verticalLine;
        }
        else {
            VerticalLineAnnotation verticalLine = sciChartBuilder.newVerticalLineAnnotation()           // Create a VerticalLineAnnotation
                    .withCoordinateMode(AnnotationCoordinateMode.Absolute)
                    .withX1((double) x.getTime())
                    .withStroke(1f, 0xc09d9fff)
                    .withVerticalGravity(Gravity.FILL_VERTICAL)
                    .withAnnotationLabel()
                    .withAnnotationLabel(LabelPlacement.Top, holdLabel)
                    .withIsEditable(true)
                    .build();
            return verticalLine;
        }

    }
    private void removeOldAnnotations(SciChartSurface surface){
        while ((double) surface.getAnnotations().get(0).getX1() < (x.getTime() - fifoLength / interval)) {
            surface.getAnnotations().remove(0);
        }
    }
    /*
    private void calibration() {                                                                     // Edit: comment out b/c not used with pwm calibration
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mainContext);

        alertDialogBuilder.setView(promptsView);                                                    // set calibration.xml to alertdialog builder

        final EditText userInput = (EditText) promptsView
                .findViewById(R.id.editTextDialog);

        alertDialogBuilder                                                                          // set dialog message
                .setCancelable(false)
                .setPositiveButton("Skip",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                trim_pot = 150000;
                                calibrate_yAxis = true;                                             // Only until defaults are hardcoded manually
                                Toast.makeText(getApplicationContext(), "Calibration skipped", Toast.LENGTH_SHORT).show();
                            }
                        })
                .setNegativeButton("Enter",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                try {
                                    trim_pot = Double.parseDouble(userInput.getText().toString());
                                    calibrate_yAxis = true;
                                    Toast.makeText(getApplicationContext(), "Calibration successful", Toast.LENGTH_SHORT).show();
                                }catch(Exception e){
                                    e.printStackTrace();
                                    Toast.makeText(getApplicationContext(), "Calibration error", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });

        AlertDialog alertDialog = alertDialogBuilder.create();

        alertDialog.show();
    }
    */
    public void immersiveMode(){
        currentApiVersion = android.os.Build.VERSION.SDK_INT;

        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        if(getActionBar()!=null)
            getActionBar().hide();

        if(currentApiVersion >= Build.VERSION_CODES.KITKAT)                                         // Enable immersive fullscreen for android 4.4+
        {

            getWindow().getDecorView().setSystemUiVisibility(flags);


            final View decorView = getWindow().getDecorView();
            decorView
                    .setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener()
                    {
                        @Override                                                                   // Code below is to handle presses of Volume up or Volume down.
                        public void onSystemUiVisibilityChange(int visibility)                      // Without this, after pressing volume buttons, the navigation bar will
                        {                                                                           // show up and won't hide
                            if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0)
                            {
                                decorView.setSystemUiVisibility(flags);
                            }
                        }
                    });
        }
    }
    @SuppressLint("NewApi")
    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        if(currentApiVersion >= Build.VERSION_CODES.KITKAT && hasFocus)
        {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
    public float megabytesAvailable(File f) {
        if (!curDir.exists()) {
            System.out.println("recordLogData: save directory does not exist. Creating new...");
            //noinspection ResultOfMethodCallIgnored
            curDir.mkdirs();
        }
        try {
            StatFs stat = new StatFs(f.getPath());
            long bytesAvailable = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)
                bytesAvailable = (long) stat.getBlockSizeLong() * (long) stat.getAvailableBlocksLong();
            else
                bytesAvailable = (long) stat.getBlockSize() * (long) stat.getAvailableBlocks();
            return bytesAvailable / (1024.f * 1024.f);
        }catch(Exception e){
            e.printStackTrace();
            return 0;
        }
    }
    public void onChange() {
        cShowProgress.showProgress(NewActivity.this);
    }

    public void pwmAdjust(){
        if(pwm != pwmLast ){//&& !showProgressBar){
            pwmStabilityIndex = 0;
            // showProgressBar = true;

        }
        else if(pwmStabilityIndex < pwmReq) pwmStabilityIndex++;
            //System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!-------" + pwmStabilityIndex + "-----------!!!!!!!!!!");
        else {
            //showProgressBar = false;

        }

    }

    /*
    public void playSound(MediaPlayer sound, View v){
        sound.start();
    }
    */

}