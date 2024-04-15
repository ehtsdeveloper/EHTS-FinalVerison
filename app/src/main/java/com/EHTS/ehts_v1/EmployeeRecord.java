package com.EHTS.ehts_v1;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;


import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import com.opencsv.CSVWriter;

import java.io.File;

import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class EmployeeRecord extends AppCompatActivity {

    ImageButton backButton;
    TextView empNameRec, empIddata, agedata, heightdata, weightdata, genderdata;
    ImageView imageRec;

    TextView tvLow, tvResting, tvMax;
    TextView AvgtvLow, AvgtvResting, AvgtvMax;

    TextView tvFinalResult;
    Button ModerateTest;
    Button IntenseTest;
    private boolean isModerateTestSelected = false;
    private boolean isIntenseTestSelected = false;
    CardView cardFinalResult;

    // TextView  editProfile;
    Button deleteProfile;

    String key = "";
    String empId = "";
    String imageUrl = "";
    CardView profileCard;

    LineChart lineChart;
    Button refreshGraph;

    Button exportData;


    private TextView period;

    DatabaseReference databaseReference;
    ValueEventListener eventListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_record);

        imageRec = findViewById(R.id.ImageData);
        empNameRec = findViewById(R.id.EmpNameData);

        empIddata = findViewById(R.id.EmpIDData);
        agedata = findViewById(R.id.AgeData);

        heightdata = findViewById(R.id.heightData);
        weightdata = findViewById(R.id.weightData);

        genderdata = findViewById(R.id.genderData);
        profileCard = findViewById(R.id.profileCard);

        deleteProfile = findViewById(R.id.deleteProfile);

        tvLow = findViewById(R.id.tv_low);
        tvResting = findViewById(R.id.tv_resting);
        tvMax = findViewById(R.id.tv_max);


        AvgtvLow = findViewById(R.id.oxyEndNum);
        AvgtvResting = findViewById(R.id.Avgtv_resting);
        AvgtvMax = findViewById(R.id.Avgtv_max);


        tvFinalResult = findViewById(R.id.tvFinalResult);
        cardFinalResult = findViewById(R.id.cardFinalResult);
        ModerateTest = findViewById(R.id.ModerateTest);
        IntenseTest= findViewById(R.id.IntenseTest);



        lineChart = findViewById(R.id.chart);
        refreshGraph = findViewById(R.id.refreshGraph);


        period = findViewById(R.id.period);
        exportData = findViewById(R.id.exportData);


        // editProfile = findViewById(R.id.EditProfile);

        backButton = findViewById(R.id.backButton);


        Bundle bundle = getIntent().getExtras();
        if (bundle != null){

            empNameRec.setText(bundle.getString("Employee Name"));
            empIddata.setText(bundle.getString("Employee ID"));
            empId = bundle.getString("Employee ID");
            agedata.setText(bundle.getString("Age"));
            heightdata.setText(bundle.getString("Height (in)"));
            weightdata.setText(bundle.getString("Weight (lb)"));
            genderdata.setText(bundle.getString("Gender"));


            key = bundle.getString("Key");
            imageUrl = bundle.getString("images/");
            Glide.with(this).load(bundle.getString("images/")).into(imageRec);
        }


        fetchSensorsData();
        fetchHeartRateData();
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // startActivity(new Intent(getApplicationContext(), Home_Employee.class));
                onBackPressed(); // Go back to the previous page
            }
        });


        deleteProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final DatabaseReference reference;

                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                reference = FirebaseDatabase.getInstance().getReference("users").child(userId).child("employees");


                FirebaseStorage storage = FirebaseStorage.getInstance();

                StorageReference storageReference = storage.getReferenceFromUrl(imageUrl);

                storageReference.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        reference.child(key).removeValue();
                        Toast.makeText(EmployeeRecord.this, "Deleted", Toast.LENGTH_SHORT).show();

                        onBackPressed(); // Go back to the previous activity
                    }
                });
            }
        });


        exportData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportDataToCSV(view);
            }
        });


/*
//Don't need - provided the link to the youtube video I followed for this in the design document
// kept if you want to add this feature later on still needs some work.

        editProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(EmployeeRecord.this, Update_Profile.class)
                        .putExtra("images/", imageUrl)
                        .putExtra("Employee Name", empNameRec.getText().toString())
                        .putExtra("Employee ID", empIddata.getText().toString())
                        .putExtra("Age", agedata.getText().toString())
                        .putExtra("Height (in)", heightdata.getText().toString())
                        .putExtra("Weight (lb)", weightdata.getText().toString())
                        .putExtra("Gender", genderdata.getText().toString())
                        .putExtra("Key", key);


                startActivity(intent);
            }
        });


 */
        //pass/fail test types
        // Add the OnClickListener for the ModerateTest button
        ModerateTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isModerateTestSelected = true;
                // Determine the final result and update the UI accordingly
                updateFinalResultUI();
                //RK: intense button bug fixed below
                isModerateTestSelected = false;
            }
        });

        // Add the OnClickListener for the IntenseTest button
        IntenseTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isIntenseTestSelected = true;
                // Determine the final result and update the UI accordingly
                updateFinalResultUI();
                //RK
                isIntenseTestSelected = false;

            }
        });

    }




    private void fetchSensorsData() {
        //fetch latest test results data of Low, max, and resting hr
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId)
                .child("employees").child(empId).child("sensors_record").child(empId);

        eventListener = databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Retrieve the last child node
                DataSnapshot lastChildSnapshot = null;
                for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                    lastChildSnapshot = childSnapshot;
                }

                if (lastChildSnapshot != null) {
                    String childKey = lastChildSnapshot.getKey();
                    SensorsData data = lastChildSnapshot.getValue(SensorsData.class);
                    data.setKey(childKey);

                    // Show Data
                    if (data.getLow() != null) {
                        tvLow.setText(String.valueOf(data.getLow().intValue()));
                    }

                    if (data.getResting() != null) {
                        tvResting.setText(String.valueOf(data.getResting().intValue()));
                    }

                    if (data.getMax() != null) {
                        tvMax.setText(String.valueOf(data.getMax().intValue()));
                    }
                    // Call fetchSensorsData2() after updating the views
                    fetchSensorsData2();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EmployeeRecord.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void fetchSensorsData2() {
        // retrieves the avg of all test results
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId)
                .child("employees").child(empId).child("sensors_record").child(empId);

        eventListener = databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long childrenCount = snapshot.getChildrenCount();
                int lowSum = 0;
                int restingSum = 0;
                int maxSum = 0;

                for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                    // Access the data of each child node
                    SensorsData data = childSnapshot.getValue(SensorsData.class);

                    // Add heart rate values to the sums
                    if (data != null) {
                        if (data.getLow() != null) {
                            lowSum += data.getLow().intValue();
                        }

                        if (data.getResting() != null) {
                            restingSum += data.getResting().intValue();
                        }

                        if (data.getMax() != null) {
                            maxSum += data.getMax().intValue();
                        }
                    }
                }

                // Calculate and display the averages
                if (childrenCount > 0) {
                    int avgLow = lowSum / (int) childrenCount;
                    int avgResting = restingSum / (int) childrenCount;
                    int avgMax = maxSum / (int) childrenCount;

                    AvgtvLow.setText(String.valueOf(avgLow));
                    AvgtvResting.setText(String.valueOf(avgResting));
                    AvgtvMax.setText(String.valueOf(avgMax));

                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EmployeeRecord.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    //allow users to retrive the last 24 hour tests and ask user moderate vs intense then determine if they passed or failed
    private void updateFinalResultUI() {
        // Calculate the target heart rate ranges based on age
        int age = Integer.parseInt(agedata.getText().toString());
        int maxAgeRelatedHR = 220 - age;
        //RK: Modified equations by changing percentages of maximum heart rate for moderate and intense activity.
        int moderateIntensityLowerLimit = (int) (maxAgeRelatedHR * 0.50);
        int moderateIntensityUpperLimit = (int) (maxAgeRelatedHR * 0.76);
        int vigorousIntensityLowerLimit = (int) (maxAgeRelatedHR * 0.70);
        int vigorousIntensityUpperLimit = (int) (maxAgeRelatedHR * 0.93);



        // Initialize restingHR and maxHR variables
        int restingHR = Integer.parseInt(AvgtvResting.getText().toString());
        int maxHR = Integer.parseInt(AvgtvMax.getText().toString());


        // Calculate the target heart rate range based on the selected test
        int targetLowerLimit;
        int targetUpperLimit;
        String testType;

        if (isModerateTestSelected) {
            targetLowerLimit = moderateIntensityLowerLimit;
            targetUpperLimit = moderateIntensityUpperLimit;
            testType = "Moderate";
        } else if (isIntenseTestSelected) {
            targetLowerLimit = vigorousIntensityLowerLimit;
            targetUpperLimit = vigorousIntensityUpperLimit;
            testType = "Intense";
        } else {
            // If no test is selected, handle the situation accordingly (e.g., display a message)
            return;
        }

        // Check if either the resting or max heart rate is within the target range
        boolean isRestingHRWithinRange = (restingHR >= targetLowerLimit && restingHR <= targetUpperLimit);
        boolean isMaxHRWithinRange = (maxHR >= targetLowerLimit && maxHR <= targetUpperLimit);

    // Determine the final result and update the UI accordingly
        //RK: Decided to use resting heart rate only and not max to check if within range
        String finalResultMessage;
        if (isRestingHRWithinRange) {
            finalResultMessage = "Employee Passed the " + testType + " EHTS Exam";
            tvFinalResult.setText(finalResultMessage);
            cardFinalResult.setCardBackgroundColor(Color.GREEN);
        } else {
            finalResultMessage = "Employee Failed the " + testType + " EHTS Exam";
            tvFinalResult.setText(finalResultMessage);
            cardFinalResult.setCardBackgroundColor(Color.parseColor("#AD2424"));
        }

        // Display a toast message with the final result message
        Toast.makeText(EmployeeRecord.this, finalResultMessage, Toast.LENGTH_SHORT).show();
    }




private void fetchHeartRateData() {
    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    DatabaseReference heartRateRef = FirebaseDatabase.getInstance().getReference("users")
            .child(userId).child("employees").child(empId).child("sensors_record").child(empId);

    Query query = heartRateRef.orderByChild("recordingStartTime");
    query.addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            ArrayList<Entry> lowEntries = new ArrayList<>();
            ArrayList<Entry> restingEntries = new ArrayList<>();
            ArrayList<Entry> maxEntries = new ArrayList<>();

            long minutesSinceStart;
            long minutesGraphStart = 10160;

            for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                String recordingStartTimestamp = childSnapshot.child("recordingStartTime").getValue(String.class);
                String recordingStopTimestamp = childSnapshot.child("recordingStopTime").getValue(String.class);
                Integer low = childSnapshot.child("low").getValue(Integer.class);
                Integer resting = childSnapshot.child("resting").getValue(Integer.class);
                Integer max = childSnapshot.child("max").getValue(Integer.class);

                try {
                    minutesSinceStart = convertToMinutesSinceCustomEpoch(recordingStartTimestamp);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                if (minutesSinceStart >= minutesGraphStart) {
                    if (low != null) {
                        try {
                            lowEntries.add(new Entry(minutesSinceStart, low.intValue()));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                    }

                    if (resting != null) {
                        try {
                            restingEntries.add(new Entry(minutesSinceStart, resting.intValue()));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                    }

                    if (max != null) {
                        try {
                            maxEntries.add(new Entry(minutesSinceStart, max.intValue()));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
            // Calculate the average resting heart rate
            float avgRestingHeartRate = calculateAverage(restingEntries);

            // Create a new LimitLine for the average resting heart rate
            LimitLine avgRestingLine = new LimitLine(avgRestingHeartRate, "Avg Resting HR");
            avgRestingLine.setLineColor(Color.BLUE);
            avgRestingLine.setLineWidth(2f);

            // Clear the existing LimitLines from the chart
            lineChart.getAxisLeft().removeAllLimitLines();

            // Add the new average resting line to the chart
            lineChart.getAxisLeft().addLimitLine(avgRestingLine);

            // Configure the low line chart
            LineDataSet dataSetLow = new LineDataSet(lowEntries, "Low HR");
            dataSetLow.setColor(Color.YELLOW);
            dataSetLow.setLineWidth(2f);
            LineData lineDataLow = new LineData(dataSetLow);

            // Configure the resting line chart
            LineDataSet dataSetResting = new LineDataSet(restingEntries, "Resting HR");
            dataSetResting.setColor(Color.GREEN);
            dataSetResting.setLineWidth(2f);
            LineData lineDataResting = new LineData(dataSetResting);

            // Configure the Max line chart
            LineDataSet dataSetMax = new LineDataSet(maxEntries, "Max HR");
            dataSetMax.setColor(Color.RED);
            dataSetMax.setLineWidth(2f);
            LineData lineDataMax = new LineData(dataSetMax);

            // Merge the three LineData objects into a single LineData object
            LineData combinedLineData = new LineData();
            combinedLineData.addDataSet(dataSetLow);
            combinedLineData.addDataSet(dataSetResting);
            combinedLineData.addDataSet(dataSetMax);

            // Set the combined LineData as the data for the line chart
            lineChart.setData(combinedLineData);

            /*
            Configure graph axis
             */
            YAxis yAxis = lineChart.getAxisLeft();
            yAxis.setAxisMaximum(0f);
            yAxis.setAxisMaximum(150f);
            yAxis.setAxisLineWidth(1f);
            yAxis.setAxisLineColor(Color.BLACK);
            yAxis.setLabelCount(10);

            lineChart.getDescription().setEnabled(false);
            lineChart.setBackgroundColor(Color.WHITE);
            lineChart.getXAxis().setTextColor(Color.BLACK);
            lineChart.getAxisLeft().setTextColor(Color.BLACK);
            lineChart.getAxisRight().setEnabled(false);

            lineChart.invalidate();

            lineChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(getXAxisLabels()));
            lineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);

            lineChart.getXAxis().setGranularity(1f);
            lineChart.getXAxis().setGranularityEnabled(true);
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
            Toast.makeText(EmployeeRecord.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    });

    refreshGraph.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            fetchHeartRateData();
            fetchSensorsData();
            // Display a toast message
            Toast.makeText(EmployeeRecord.this, "Graph Refreshed", Toast.LENGTH_SHORT).show();
        }
    });
}



    public static long convertToMinutesSinceCustomEpoch(String dateTime) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date date = format.parse(dateTime);

        // Define custom Epoch
        Date customEpoch = format.parse("2023-07-01 00:00:00");

        long diffInMilli = date.getTime() - customEpoch.getTime();
        long MinutesSinceCustomEpoch = diffInMilli / 1000 / 60; // / 60;
        return MinutesSinceCustomEpoch;
    }
    private String[] getXAxisLabels() {
        String[] labels = new String[9];
        labels[0] = "4AM";
        labels[1] = "6AM";
        labels[2] = "8AM";
        labels[3] = "10AM";
        labels[4] = "12PM";
        labels[5] = "2PM";
        labels[6] = "4PM";
        labels[7] = "6PM";
        labels[8] = "8PM";
        return labels;
    }

    private float getTimeInHours(String timestamp) {
        SimpleDateFormat inputFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("HH", Locale.getDefault());
        try {
            Date date = inputFormat.parse(timestamp);
            return Float.parseFloat(outputFormat.format(date));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return 0f;
    }

    private float calculateAverage(ArrayList<Entry> entries) {
        float sum = 0;
        for (Entry entry : entries) {
            sum += entry.getY();
        }
        return sum / entries.size();
    }

    public void exportDataToCSV(View view) {
        // Retrieve the employee data
        String empName = empNameRec.getText().toString();
        String empId = empIddata.getText().toString();
        String age = agedata.getText().toString();
        String height = heightdata.getText().toString();
        String weight = weightdata.getText().toString();
        String gender = genderdata.getText().toString();

        // Retrieve the sensor data
        String low = tvLow.getText().toString();
        String resting = tvResting.getText().toString();
        String max = tvMax.getText().toString();

        String avgLow = AvgtvLow.getText().toString();
        String avgResting = AvgtvResting.getText().toString();
        String avgMax = AvgtvMax.getText().toString();

        // Determine the pass/fail test result
        String passFailResult = "Employee Failed E.H.T.S Exam"; // Set the initial result as failed
        if (cardFinalResult.getCardBackgroundColor().getDefaultColor() == Color.GREEN) {
            passFailResult = "Employee Passed E.H.T.S Exam";
        }

        // Create a CSV record using OpenCSV library
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"EHTS TEST RESULTS"});
        rows.add(new String[]{"Employee Name:", empName});
        rows.add(new String[]{"Employee ID:", empId});
        rows.add(new String[]{"Age:", age});
        rows.add(new String[]{"Height (IN):", height});
        rows.add(new String[]{"Weight (LB):", weight});
        rows.add(new String[]{"Gender:", gender});
        rows.add(new String[]{});
        rows.add(new String[]{"Last Heart Rate Results"});
        rows.add(new String[]{"Low:", low});
        rows.add(new String[]{"Resting:", resting});
        rows.add(new String[]{"Max:", max});
        rows.add(new String[]{});
        rows.add(new String[]{"Avg. Heart Rate Results of All Tests"});
        rows.add(new String[]{"Low:", avgLow});
        rows.add(new String[]{"Resting:", avgResting});
        rows.add(new String[]{"Max:", avgMax});
        rows.add(new String[]{});
        rows.add(new String[]{"Pass/Fail Test Results:", passFailResult});

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference heartRateRef = FirebaseDatabase.getInstance().getReference("users")
                .child(userId).child("employees").child(empId).child("sensors_record").child(empId);

        Query query = heartRateRef.orderByChild("recordingStartTime");
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    int testNo = 1;
                    rows.add(new String[]{});
                    rows.add(new String[]{"Timeline Test Results:"});
                    rows.add(new String[]{"Test No.", "Test Start Time:", "Test End Time:", "Low HR", "Resting HR", "Max HR"});
                    for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                        String recordingStartTimestamp = childSnapshot.child("recordingStartTime").getValue(String.class);
                        String recordingStopTimestamp = childSnapshot.child("recordingStopTime").getValue(String.class);
                        Integer low = childSnapshot.child("low").getValue(Integer.class);
                        Integer resting = childSnapshot.child("resting").getValue(Integer.class);
                        Integer max = childSnapshot.child("max").getValue(Integer.class);

                        rows.add(new String[]{
                                String.valueOf(testNo),
                                convertToTimestamp1(recordingStartTimestamp),
                                convertToTimestamp1(recordingStopTimestamp),
                                String.valueOf(low),
                                String.valueOf(resting),
                                String.valueOf(max)
                        });

                        testNo++;
                    }


                    String timestamp = new SimpleDateFormat("yyyy_MM_dd_hh_mm", Locale.US).format(new Date());
                    String filename = "employee_data_" + timestamp + ".csv";

                    try {
                        // Create the CSV file in the app's cache directory
                        File cacheDir = getCacheDir();
                        File csvFile = new File(cacheDir, filename);

                        // Write the data to the CSV file
                        FileWriter writer = new FileWriter(csvFile);
                        CSVWriter csvWriter = new CSVWriter(writer);
                        csvWriter.writeAll(rows);
                        csvWriter.close();

                        // Share the CSV file using Intent
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/csv");
                        Uri fileUri = FileProvider.getUriForFile(EmployeeRecord.this, getPackageName() + ".fileprovider", csvFile);
                        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                        startActivity(Intent.createChooser(shareIntent, "Export CSV"));

                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(EmployeeRecord.this, "Failed to export data to CSV.", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle the error case
                Toast.makeText(EmployeeRecord.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String convertToTimestamp(float hoursSinceCustomEpoch) {
        long milliseconds = (long) (hoursSinceCustomEpoch * 60 * 60 * 1000);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US);
        return dateFormat.format(new Date(milliseconds));
    }
    private String convertToTimestamp1(String timestamp) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date date = inputFormat.parse(timestamp);
            SimpleDateFormat outputFormat = new SimpleDateFormat("MM/dd/yy hh:mm a", Locale.US);
            outputFormat.setTimeZone(TimeZone.getDefault()); // Set the correct timezone
            return outputFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return "";
    }







}