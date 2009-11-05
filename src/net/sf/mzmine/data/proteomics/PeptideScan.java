/*
 * Copyright 2006-2009 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.data.proteomics;

import java.util.TreeSet;
import java.util.Vector;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.util.CollectionUtils;
import net.sf.mzmine.util.Range;
import net.sf.mzmine.util.ScanUtils;

public class PeptideScan implements Scan {
	
	private PeptideIdentityDataFile dataFile;
	private RawDataFile rawDataFile;
	private int scanNumber;
	private Vector<Peptide> peptides;
	//private int queryNumber;
	private int msLevel;
	private int parentScan;
	private int fragmentScans[];
	private DataPoint dataPoints[];
	private double precursorMZ;
	private int precursorCharge;
	private double retentionTime;
	private Range mzRange;
	private DataPoint basePeak;
	private double totalIonCurrent;
	private boolean centroided;

	/**
	 * Constructor 
	 */
	public PeptideScan(PeptideIdentityDataFile dataFile, String rawDataFile, int queryNumber) {

		if (dataPoints != null)
			setDataPoints(dataPoints);
		else
			throw new IllegalArgumentException(
					"Missing data points for query #" + queryNumber);

		this.dataFile = dataFile;
		this.peptides = new Vector<Peptide>();
		//this.queryNumber = queryNumber;
			
	}


	public void setRawDataFile(RawDataFile rawDataFile){
		this.rawDataFile = rawDataFile;
	}
	
	
	/**
	 * @return Returns scan datapoints
	 */
	public DataPoint[] getDataPoints() {
		return dataPoints;
	}

	/**
	 * @return Returns scan datapoints within a given range
	 */
	public DataPoint[] getDataPointsByMass(Range mzRange) {

		int startIndex, endIndex;
		for (startIndex = 0; startIndex < dataPoints.length; startIndex++) {
			if (dataPoints[startIndex].getMZ() >= mzRange.getMin())
				break;
		}

		for (endIndex = startIndex; endIndex < dataPoints.length; endIndex++) {
			if (dataPoints[endIndex].getMZ() > mzRange.getMax())
				break;
		}

		DataPoint pointsWithinRange[] = new DataPoint[endIndex - startIndex];

		// Copy the relevant points
		System.arraycopy(dataPoints, startIndex, pointsWithinRange, 0, endIndex
				- startIndex);

		return pointsWithinRange;
	}

	/**
	 * @return Returns scan datapoints over certain intensity
	 */
	public DataPoint[] getDataPointsOverIntensity(double intensity) {
		int index;
		Vector<DataPoint> points = new Vector<DataPoint>();

		for (index = 0; index < dataPoints.length; index++) {
			if (dataPoints[index].getIntensity() >= intensity)
				points.add(dataPoints[index]);
		}

		DataPoint pointsOverIntensity[] = points.toArray(new DataPoint[0]);

		return pointsOverIntensity;
	}

	/**
	 * @param mzValues
	 *            m/z values to set
	 * @param intensityValues
	 *            Intensity values to set
	 */
	public void setDataPoints(DataPoint[] dataPoints) {

		this.dataPoints = dataPoints;

		// find m/z range and base peak
		if (dataPoints.length > 0) {

			basePeak = dataPoints[0];
			mzRange = new Range(dataPoints[0].getMZ(), dataPoints[0].getMZ());

			for (DataPoint dp : dataPoints) {

				if (dp.getIntensity() > basePeak.getIntensity())
					basePeak = dp;

				mzRange.extendRange(dp.getMZ());

				totalIonCurrent += dp.getIntensity();

			}

		} else {
			// Empty scan, so no m/z range or base peak
			mzRange = new Range(0, 0);
			basePeak = null;
			totalIonCurrent = 0;
		}

	}

	/**
	 * @see net.sf.mzmine.data.Scan#getNumberOfDataPoints()
	 */
	public int getNumberOfDataPoints() {
		return dataPoints.length;
	}

	/**
	 * @see net.sf.mzmine.data.Scan#getScanNumber()
	 */
	public int getScanNumber() {
		return scanNumber;
	}

	/**
	 * @param scanNumber
	 *            The scanNumber to set.
	 */
	public void setScanNumber(int scanNumber) {
		this.scanNumber = scanNumber;
	}

	/**
	 * @see net.sf.mzmine.data.Scan#getMSLevel()
	 */
	public int getMSLevel() {
		return msLevel;
	}

	/**
	 * @param msLevel
	 *            The msLevel to set.
	 */
	public void setMSLevel(int msLevel) {
		this.msLevel = msLevel;
	}

	/**
	 * @see net.sf.mzmine.data.Scan#getPrecursorMZ()
	 */
	public double getPrecursorMZ() {
		return precursorMZ;
	}

	/**
	 * @param precursorMZ
	 *            The precursorMZ to set.
	 */
	public void setPrecursorMZ(double precursorMZ) {
		this.precursorMZ = precursorMZ;
	}

	/**
	 * @return Returns the precursorCharge.
	 */
	public int getPrecursorCharge() {
		return precursorCharge;
	}

	/**
	 * @param precursorCharge
	 *            The precursorCharge to set.
	 */
	public void setPrecursorCharge(int precursorCharge) {
		this.precursorCharge = precursorCharge;
	}

	/**
	 * @see net.sf.mzmine.data.Scan#getScanAcquisitionTime()
	 */
	public double getRetentionTime() {
		return retentionTime;
	}

	/**
	 * @param retentionTime
	 *            The retentionTime to set.
	 */
	public void setRetentionTime(double retentionTime) {
		this.retentionTime = retentionTime;
	}

	/**
	 * @see net.sf.mzmine.data.Scan#getMZRangeMax()
	 */
	public Range getMZRange() {
		return mzRange;
	}

	/**
	 * @see net.sf.mzmine.data.Scan#getBasePeakMZ()
	 */
	public DataPoint getBasePeak() {
		return basePeak;
	}

	/**
	 * @see net.sf.mzmine.data.Scan#getParentScanNumber()
	 */
	public int getParentScanNumber() {
		return parentScan;
	}

	/**
	 * @param parentScan
	 *            The parentScan to set.
	 */
	public void setParentScanNumber(int parentScan) {
		this.parentScan = parentScan;
	}

	/**
	 * @see net.sf.mzmine.data.Scan#getFragmentScanNumbers()
	 */
	public int[] getFragmentScanNumbers() {
		return fragmentScans;
	}

	/**
	 * @param fragmentScans
	 *            The fragmentScans to set.
	 */
	public void setFragmentScanNumbers(int[] fragmentScans) {
		this.fragmentScans = fragmentScans;
	}

	public void addFragmentScan(int fragmentScan) {
		TreeSet<Integer> fragmentsSet = new TreeSet<Integer>();
		if (fragmentScans != null) {
			for (int frag : fragmentScans)
				fragmentsSet.add(frag);
		}
		fragmentsSet.add(fragmentScan);
		fragmentScans = CollectionUtils.toIntArray(fragmentsSet);
	}

	/**
	 * @see net.sf.mzmine.data.Scan#isCentroided()
	 */
	public boolean isCentroided() {
		return centroided;
	}

	/**
	 * @param centroided
	 *            The centroided to set.
	 */
	public void setCentroided(boolean centroided) {
		this.centroided = centroided;
	}

	public double getTIC() {
		return totalIonCurrent;
	}

	public String toString() {
		return ScanUtils.scanToString(this);
	}

	public RawDataFile getDataFile() {
		return rawDataFile;
	}
	
	public PeptideIdentityDataFile getPeptideDataFile() {
		return dataFile;
	}

	public void setPeptides(Peptide peptide) {
		peptides.add(peptide);
	}
	
	public Vector<Peptide> getPeptides() {
		return peptides;
	}
	
}