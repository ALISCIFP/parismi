/*******************************************************************************
 * Parismi v0.1
 * Copyright (c) 2009-2015 Cinquin Lab.
 * All rights reserved. This code is made available under a dual license:
 * the two-clause BSD license or the GNU Public License v2.
 ******************************************************************************/
/*
 * ===========================================================
 * JFreeChart : a free chart library for the Java(tm) platform
 * ===========================================================
 * 
 * (C) Copyright 2000-2008, by Object Refinery Limited and Contributors.
 * 
 * Project Info: http://www.jfree.org/jfreechart/index.html
 * 
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 * USA.
 * 
 * [Java is a trademark or registered trademark of Sun Microsystems, Inc.
 * in the United States and other countries.]
 * 
 * ---------------------
 * HistogramDataset.java
 * ---------------------
 * (C) Copyright 2003-2008, by Jelai Wang and Contributors.
 * 
 * Original Author: Jelai Wang (jelaiw AT mindspring.com);
 * Contributor(s): David Gilbert (for Object Refinery Limited);
 * Cameron Hayne;
 * Rikard Bj?rklind;
 * 
 * Changes
 * -------
 * 06-Jul-2003 : Version 1, contributed by Jelai Wang (DG);
 * 07-Jul-2003 : Changed package and added Javadocs (DG);
 * 15-Oct-2003 : Updated Javadocs and removed array sorting (JW);
 * 09-Jan-2004 : Added fix by "Z." posted in the JFreeChart forum (DG);
 * 01-Mar-2004 : Added equals() and clone() methods and implemented
 * Serializable. Also added new addSeries() method (DG);
 * 06-May-2004 : Now extends AbstractIntervalXYDataset (DG);
 * 15-Jul-2004 : Switched getX() with getXValue() and getY() with
 * getYValue() (DG);
 * 20-May-2005 : Speed up binning - see patch 1026151 contributed by Cameron
 * Hayne (DG);
 * 08-Jun-2005 : Fixed bug in getSeriesKey() method (DG);
 * 22-Nov-2005 : Fixed cast in getSeriesKey() method - see patch 1329287 (DG);
 * ------------- JFREECHART 1.0.x ---------------------------------------------
 * 03-Aug-2006 : Improved precision of bin boundary calculation (DG);
 * 07-Sep-2006 : Fixed bug 1553088 (DG);
 * 22-May-2008 : Implemented clone() method override (DG);
 */

// Modified by Olivier Cinquin

package pipeline.GUI_utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.general.SeriesChangeEvent;
import org.jfree.data.general.SeriesChangeListener;
import org.jfree.data.statistics.HistogramBin;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.statistics.HistogramType;
import org.jfree.data.xy.IntervalXYDataset;
import org.jfree.util.ObjectUtilities;
import org.jfree.util.PublicCloneable;

import pipeline.misc_util.Utils;
import pipeline.misc_util.Utils.LogLevel;

/**
 * A dataset that can be used for creating histograms. This is extended from the original jFreeChart version to
 * implement FreeChartSeriesCollectionManipulator, in order to show XYScatterPlotView a somewhat-unified view
 * of collections of series to be plotted.
 * This collection can add a XYSeriesReflection as an element; it takes the x values as
 * the values to form a histogram with, and ignores other values. It listens to that series, and automatically
 * recomputes the histogram when it is modified. At some point we will need to find a way to do that more efficiently.
 *
 */
// This could also extend AbstractIntervalXYDataset

public class BetterHistogramDataset extends HistogramDataset implements IntervalXYDataset, Cloneable, PublicCloneable,
		Serializable, FreeChartSeriesCollectionManipulator {

	/** For serialization. */
	private static final long serialVersionUID = -6341668077370231153L;

	/** A list of maps. */
	private List<Map<Comparable<?>, Object>> list1;

	/** The histogram type. */
	private HistogramType type;

	/**
	 * Creates a new (empty) dataset with a default type of {@link HistogramType}.FREQUENCY.
	 */
	public BetterHistogramDataset() {
		this.list1 = new ArrayList<>();
		this.type = HistogramType.FREQUENCY;
	}

	/**
	 * Returns the histogram type.
	 *
	 * @return The type (never <code>null</code>).
	 */
	@Override
	public HistogramType getType() {
		return this.type;
	}

	/**
	 * Sets the histogram type and sends a {@link DatasetChangeEvent} to all
	 * registered listeners.
	 *
	 * @param type
	 *            the type (<code>null</code> not permitted).
	 */
	@Override
	public void setType(HistogramType type) {
		if (type == null) {
			throw new IllegalArgumentException("Null 'type' argument");
		}
		this.type = type;
		notifyListeners(new DatasetChangeEvent(this, this));
	}

	/**
	 * Adds a series to the dataset, using the specified number of bins.
	 *
	 * @param key
	 *            the series key (<code>null</code> not permitted).
	 * @param seriesForValues
	 *            XYSeries containing the values in the "X" part of the series.
	 * @param bins
	 *            the number of bins (must be at least 1).
	 */
	void addSeries(Comparable<?> key, XYSeriesE seriesForValues, int bins) {
		// defer argument checking...
		double[] values = seriesForValues.getXValues();
		double minimum = getMinimum(values);
		double maximum = getMaximum(values);
		addSeries(key, values, bins, minimum, maximum, null);
		list1.get(list1.size() - 1).put("XYSeries", seriesForValues);
		seriesForValues.addChangeListener(new ChangeListener(seriesForValues, bins));
	}

	private class ChangeListener implements SeriesChangeListener {
		XYSeriesE seriesToWatch;
		private int bins;

		public ChangeListener(XYSeriesE seriesToWatch, int bins) {
			this.seriesToWatch = seriesToWatch;
			this.bins = bins;
		}

		@Override
		public void seriesChanged(SeriesChangeEvent event) {
			for (Map<Comparable<?>, Object> map : list1) {
				if (map.get("XYSeries") != null) {
					if (map.get("XYSeries") == seriesToWatch) {
						double[] values = seriesToWatch.getXValues();
						if (values.length == 0)
							return;
						double minimum = getMinimum(values);
						double maximum = getMaximum(values);
						addSeries((Comparable<?>) map.get("key"), values, bins, minimum, maximum, map);
						fireDatasetChanged();
						return;
					}
				}
			}
		}
	}

	/**
	 * Adds a series to the dataset, using the specified number of bins.
	 *
	 * @param key
	 *            the series key (<code>null</code> not permitted).
	 * @param values
	 *            the values (<code>null</code> not permitted).
	 * @param bins
	 *            the number of bins (must be at least 1).
	 */
	@Override
	// Can't use Comparable<?> because that won't override supertype
			public
			void addSeries(@SuppressWarnings("rawtypes") Comparable key, double[] values, int bins) {
		// defer argument checking...
		double minimum = getMinimum(values);
		double maximum = getMaximum(values);
		addSeries(key, values, bins, minimum, maximum, null);
	}

	/**
	 * Adds a series to the dataset. Any data value less than minimum will be
	 * assigned to the first bin, and any data value greater than maximum will
	 * be assigned to the last bin. Values falling on the boundary of
	 * adjacent bins will be assigned to the higher indexed bin.
	 *
	 * @param key
	 *            the series key (<code>null</code> not permitted).
	 * @param values
	 *            the raw observations.
	 * @param bins
	 *            the number of bins (must be at least 1).
	 * @param minimum
	 *            the lower bound of the bin range.
	 * @param maximum
	 *            the upper bound of the bin range.
	 */
	void addSeries(Comparable<?> key, double[] values, int bins, double minimum, double maximum,
			Map<Comparable<?>, Object> preexistingMap) {

		if (key == null) {
			throw new IllegalArgumentException("Null 'key' argument.");
		}
		if (values == null) {
			throw new IllegalArgumentException("Null 'values' argument.");
		} else if (bins < 1) {
			throw new IllegalArgumentException("The 'bins' value must be at least 1.");
		}
		double binWidth = (maximum - minimum) / bins;

		double lower = minimum;
		double upper;
		List<HistogramBin> binList = new ArrayList<>(bins);
		for (int i = 0; i < bins; i++) {
			HistogramBin bin;
			// make sure bins[bins.length]'s upper boundary ends at maximum
			// to avoid the rounding issue. the bins[0] lower boundary is
			// guaranteed start from min
			if (i == bins - 1) {
				bin = new HistogramBin(lower, maximum);
			} else {
				upper = minimum + (i + 1) * binWidth;
				bin = new HistogramBin(lower, upper);
				lower = upper;
			}
			binList.add(bin);
		}
		// fill the bins
		for (double value : values) {
			int binIndex = bins - 1;
			if (value < maximum) {
				double fraction = (value - minimum) / (maximum - minimum);
				if (fraction < 0.0) {
					fraction = 0.0;
				}
				binIndex = (int) (fraction * bins);
				// rounding could result in binIndex being equal to bins
				// which will cause an IndexOutOfBoundsException - see bug
				// report 1553088
				if (binIndex >= bins) {
					binIndex = bins - 1;
				}
			}
			HistogramBin bin = binList.get(binIndex);
			bin.incrementCount();
		}
		// generic map for each series
		Map<Comparable<?>, Object> map;
		if (preexistingMap == null) {
			map = new HashMap<>();
			list1.add(map);
		} else
			map = preexistingMap;
		map.put("key", key);
		map.put("bins", binList);
		map.put("values.length", new Integer(values.length));
		map.put("bin width", new Double(binWidth));
		// THE CALLER WILL NEED TO ADD THE XY SERIES THE DATA IS DERIVED FROM
	}

	/**
	 * Returns the minimum value in an array of values.
	 *
	 * @param values
	 *            the values (<code>null</code> not permitted and
	 *            zero-length array not permitted).
	 *
	 * @return The minimum value.
	 */
	private static double getMinimum(double[] values) {
		if (values == null || values.length < 1) {
			throw new IllegalArgumentException("Null or zero length 'values' argument.");
		}
		double min = Double.MAX_VALUE;
		for (double value : values) {
			if (value < min) {
				min = value;
			}
		}
		return min;
	}

	/**
	 * Returns the maximum value in an array of values.
	 *
	 * @param values
	 *            the values (<code>null</code> not permitted and
	 *            zero-length array not permitted).
	 *
	 * @return The maximum value.
	 */
	private static double getMaximum(double[] values) {
		if (values == null || values.length < 1) {
			throw new IllegalArgumentException("Null or zero length 'values' argument.");
		}
		double max = -Double.MAX_VALUE;
		for (double value : values) {
			if (value > max) {
				max = value;
			}
		}
		return max;
	}

	/**
	 * Returns the bins for a series.
	 *
	 * @param series
	 *            the series index (in the range <code>0</code> to <code>getSeriesCount() - 1</code>).
	 *
	 * @return A list of bins.
	 *
	 * @throws IndexOutOfBoundsException
	 *             if <code>series</code> is outside the
	 *             specified range.
	 */
	@SuppressWarnings("all")
	List<HistogramBin> getBins(int series) {
		return (List<HistogramBin>) list1.get(series).get("bins");
	}

	/**
	 * Returns the total number of observations for a series.
	 *
	 * @param series
	 *            the series index.
	 *
	 * @return The total.
	 */
	private int getTotal(int series) {
		return (Integer) list1.get(series).get("values.length");
	}

	/**
	 * Returns the bin width for a series.
	 *
	 * @param series
	 *            the series index (zero based).
	 *
	 * @return The bin width.
	 */
	private double getBinWidth(int series) {
		return (Double) list1.get(series).get("bin width");
	}

	/**
	 * Returns the number of series in the dataset.
	 *
	 * @return The series count.
	 */
	@Override
	public int getSeriesCount() {
		return this.list1.size();
	}

	/**
	 * Returns the key for a series.
	 *
	 * @param series
	 *            the series index (in the range <code>0</code> to <code>getSeriesCount() - 1</code>).
	 *
	 * @return The series key.
	 *
	 * @throws IndexOutOfBoundsException
	 *             if <code>series</code> is outside the
	 *             specified range.
	 */
	@Override
	public Comparable<?> getSeriesKey(int series) {
		return (Comparable<?>) list1.get(series).get("key");
	}

	/**
	 * Returns the number of data items for a series.
	 *
	 * @param series
	 *            the series index (in the range <code>0</code> to <code>getSeriesCount() - 1</code>).
	 *
	 * @return The item count.
	 *
	 * @throws IndexOutOfBoundsException
	 *             if <code>series</code> is outside the
	 *             specified range.
	 */
	@Override
	public int getItemCount(int series) {
		return getBins(series).size();
	}

	/**
	 * Returns the X value for a bin. This value won't be used for plotting
	 * histograms, since the renderer will ignore it. But other renderers can
	 * use it (for example, you could use the dataset to create a line
	 * chart).
	 *
	 * @param series
	 *            the series index (in the range <code>0</code> to <code>getSeriesCount() - 1</code>).
	 * @param item
	 *            the item index (zero based).
	 *
	 * @return The start value.
	 *
	 * @throws IndexOutOfBoundsException
	 *             if <code>series</code> is outside the
	 *             specified range.
	 */
	@Override
	public Number getX(int series, int item) {
		List<HistogramBin> bins = getBins(series);
		HistogramBin bin = bins.get(item);
		double x = (bin.getStartBoundary() + bin.getEndBoundary()) / 2.;
		return new Double(x);
	}

	/**
	 * Returns the y-value for a bin (calculated to take into account the
	 * histogram type).
	 *
	 * @param series
	 *            the series index (in the range <code>0</code> to <code>getSeriesCount() - 1</code>).
	 * @param item
	 *            the item index (zero based).
	 *
	 * @return The y-value.
	 *
	 * @throws IndexOutOfBoundsException
	 *             if <code>series</code> is outside the
	 *             specified range.
	 */
	@Override
	public Number getY(int series, int item) {
		List<HistogramBin> bins = getBins(series);
		HistogramBin bin = bins.get(item);
		double total = getTotal(series);
		double binWidth = getBinWidth(series);

		if (this.type == HistogramType.FREQUENCY) {
			return new Double(bin.getCount());
		} else if (this.type == HistogramType.RELATIVE_FREQUENCY) {
			return new Double(bin.getCount() / total);
		} else if (this.type == HistogramType.SCALE_AREA_TO_1) {
			return new Double(bin.getCount() / (binWidth * total));
		} else { // pretty sure this shouldn't ever happen
			throw new IllegalStateException();
		}
	}

	/**
	 * Returns the start value for a bin.
	 *
	 * @param series
	 *            the series index (in the range <code>0</code> to <code>getSeriesCount() - 1</code>).
	 * @param item
	 *            the item index (zero based).
	 *
	 * @return The start value.
	 *
	 * @throws IndexOutOfBoundsException
	 *             if <code>series</code> is outside the
	 *             specified range.
	 */
	@Override
	public Number getStartX(int series, int item) {
		List<HistogramBin> bins = getBins(series);
		HistogramBin bin = bins.get(item);
		return new Double(bin.getStartBoundary());
	}

	/**
	 * Returns the end value for a bin.
	 *
	 * @param series
	 *            the series index (in the range <code>0</code> to <code>getSeriesCount() - 1</code>).
	 * @param item
	 *            the item index (zero based).
	 *
	 * @return The end value.
	 *
	 * @throws IndexOutOfBoundsException
	 *             if <code>series</code> is outside the
	 *             specified range.
	 */
	@Override
	public Number getEndX(int series, int item) {
		List<HistogramBin> bins = getBins(series);
		HistogramBin bin = bins.get(item);
		return new Double(bin.getEndBoundary());
	}

	/**
	 * Returns the start y-value for a bin (which is the same as the y-value,
	 * this method exists only to support the general form of the {@link IntervalXYDataset} interface).
	 *
	 * @param series
	 *            the series index (in the range <code>0</code> to <code>getSeriesCount() - 1</code>).
	 * @param item
	 *            the item index (zero based).
	 *
	 * @return The y-value.
	 *
	 * @throws IndexOutOfBoundsException
	 *             if <code>series</code> is outside the
	 *             specified range.
	 */
	@Override
	public Number getStartY(int series, int item) {
		return getY(series, item);
	}

	/**
	 * Returns the end y-value for a bin (which is the same as the y-value,
	 * this method exists only to support the general form of the {@link IntervalXYDataset} interface).
	 *
	 * @param series
	 *            the series index (in the range <code>0</code> to <code>getSeriesCount() - 1</code>).
	 * @param item
	 *            the item index (zero based).
	 *
	 * @return The Y value.
	 *
	 * @throws IndexOutOfBoundsException
	 *             if <code>series</code> is outside the
	 *             specified range.
	 */
	@Override
	public Number getEndY(int series, int item) {
		return getY(series, item);
	}

	/**
	 * Tests this dataset for equality with an arbitrary object.
	 *
	 * @param obj
	 *            the object to test against (<code>null</code> permitted).
	 *
	 * @return A boolean.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof BetterHistogramDataset)) {
			return false;
		}
		BetterHistogramDataset that = (BetterHistogramDataset) obj;
		if (!ObjectUtilities.equal(this.type, that.type)) {
			return false;
		}
		return ObjectUtilities.equal(this.list1, that.list1);
	}

	/**
	 * Returns a clone of the dataset.
	 *
	 * @return A clone of the dataset.
	 *
	 * @throws CloneNotSupportedException
	 *             if the object cannot be cloned.
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
		/*
		 * BetterHistogramDataset clone = (BetterHistogramDataset) super.clone();
		 * int seriesCount = getSeriesCount();
		 * clone.list = new java.util.ArrayList(seriesCount);
		 * for (int i = 0; i < seriesCount; i++) {
		 * clone.list.add(new HashMap((Map) this.list.get(i)));
		 * }
		 * return clone;
		 */
	}

	@Override
	public void addSeries(Object series) {
		if (!(series instanceof XYSeriesE)) {
			Utils.log("series to add is not of expected type: " + series + "; ignoring", LogLevel.DEBUG);
			return;
		}
		int newIndex = 0;
		if (list1 != null)
			newIndex = list1.size();
		addSeries("Histogram " + newIndex, (XYSeriesE) series, 30);
		fireDatasetChanged();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public List getSeries() {
		return list1;
	}

	@Override
	public void removeAllSeries() {
		list1.clear();
	}

	@Override
	public int hashCode() {
		throw new RuntimeException("Not implemented");
	}

}