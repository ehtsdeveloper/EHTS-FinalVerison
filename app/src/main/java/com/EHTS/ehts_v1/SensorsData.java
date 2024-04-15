package com.EHTS.ehts_v1;

public class SensorsData {
    private Double resting;
    private Double low;
    private Double max;
    private String recordingStartTimeStamp;
    private String recordingStopTimeStamp;
    private String key;

    public SensorsData(Double resting, Double low, Double max, String recordingStartTimeStamp, String recordingStopTimeStamp) {
        this.resting = resting;
        this.low = low;
        this.max = max;
        this.recordingStartTimeStamp = recordingStartTimeStamp;
        this.recordingStopTimeStamp = recordingStopTimeStamp;
    }

    public SensorsData() {}

    public Double getResting() {
        return resting;
    }

    public void setResting(Double resting) {
        this.resting = resting;
    }

    public Double getLow() {
        return low;
    }

    public void setLow(Double low) {
        this.low = low;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    //temp for test
    public Double getOxy() {
        double Oxygen = 98;
        return Oxygen;
    }

    public String getRecordingStartTimeStamp() {
        return recordingStartTimeStamp;
    }

    public void setRecordingStartTimeStamp(String recordingStartTimeStamp) {
        this.recordingStartTimeStamp = recordingStartTimeStamp;
    }

    public String getRecordingStopTimeStamp() {
        return recordingStopTimeStamp;
    }

    public void setRecordingStopTimeStamp(String recordingStopTimeStamp) {
        this.recordingStopTimeStamp = recordingStopTimeStamp;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
