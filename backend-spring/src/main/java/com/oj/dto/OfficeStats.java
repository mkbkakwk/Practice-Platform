package com.oj.dto;

public class OfficeStats {
    private long totalAnswered;
    private long correctCount;
    private double accuracy;
    // breakdown by app type
    private long wordAnswered;
    private long wordCorrect;
    private long excelAnswered;
    private long excelCorrect;
    private long pptAnswered;
    private long pptCorrect;
    public long getTotalAnswered() { return totalAnswered; }
    public void setTotalAnswered(long totalAnswered) { this.totalAnswered = totalAnswered; }
    public long getCorrectCount() { return correctCount; }
    public void setCorrectCount(long correctCount) { this.correctCount = correctCount; }
    public double getAccuracy() { return accuracy; }
    public void setAccuracy(double accuracy) { this.accuracy = accuracy; }
    public long getWordAnswered() { return wordAnswered; }
    public void setWordAnswered(long wordAnswered) { this.wordAnswered = wordAnswered; }
    public long getWordCorrect() { return wordCorrect; }
    public void setWordCorrect(long wordCorrect) { this.wordCorrect = wordCorrect; }
    public long getExcelAnswered() { return excelAnswered; }
    public void setExcelAnswered(long excelAnswered) { this.excelAnswered = excelAnswered; }
    public long getExcelCorrect() { return excelCorrect; }
    public void setExcelCorrect(long excelCorrect) { this.excelCorrect = excelCorrect; }
    public long getPptAnswered() { return pptAnswered; }
    public void setPptAnswered(long pptAnswered) { this.pptAnswered = pptAnswered; }
    public long getPptCorrect() { return pptCorrect; }
    public void setPptCorrect(long pptCorrect) { this.pptCorrect = pptCorrect; }
}
