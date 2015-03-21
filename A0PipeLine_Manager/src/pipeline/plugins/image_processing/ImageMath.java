/*******************************************************************************
 * Parismi v0.1
 * Copyright (c) 2009-2015 Cinquin Lab.
 * All rights reserved. This code is made available under a dual license:
 * the two-clause BSD license or the GNU Public License v2.
 ******************************************************************************/
package pipeline.plugins.image_processing;

/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

import java.util.HashMap;
import java.util.Map;

import pipeline.PreviewType;
import pipeline.data.IPluginIOStack;
import pipeline.data.InputOutputDescription;
import pipeline.data.PluginIOHyperstack;
import pipeline.data.PluginIOImage;
import pipeline.data.PluginIOImage.PixelType;
import pipeline.misc_util.IntrospectionParameters.ParameterInfo;
import pipeline.misc_util.IntrospectionParameters.ParameterType;
import pipeline.misc_util.ProgressReporter;
import pipeline.misc_util.Utils;
import pipeline.plugins.AuxiliaryInputOutputPlugin;
import pipeline.plugins.ThreeDPlugin;

/**
 * For now does a multiplication in a clunky way, but extend to other types later (and need to deal with proper pairing
 * of inputs and channel selection).
 */

public class ImageMath extends ThreeDPlugin implements AuxiliaryInputOutputPlugin {

	@Override
	public String operationName() {
		return "ImageMath";
	}

	@Override
	public String version() {
		return "1.0";
	}

	@Override
	public int getFlags() {
		return SAME_AS_FLOAT + ONLY_FLOAT_INPUT + ONE_OUTPUT_CHANNEL_PER_INPUT_CHANNEL;
	}

	@ParameterInfo(userDisplayName = "Operation", changeTriggersUpdate = false, changeTriggersLiveUpdates = false,
			stringChoices = { "Multiplication", "Weighted addition" }, stringValue = "Multiplication",
			editable = false, noErrorIfMissingOnReload = true)
	@ParameterType(parameterType = "ComboBox", printValueAsString = true)
	private String operation;

	@ParameterInfo(userDisplayName = "Weight", changeTriggersUpdate = false, changeTriggersLiveUpdates = false,
			floatValue = 0, permissibleFloatRange = { -10, 0 }, noErrorIfMissingOnReload = true)
	private float weight;

	@ParameterInfo(userDisplayName = "Offset", changeTriggersUpdate = false, changeTriggersLiveUpdates = false,
			floatValue = 0, permissibleFloatRange = { -1000, 10000 }, noErrorIfMissingOnReload = true)
	private float offset;

	private boolean cancelled;

	@Override
	public void runChannel(final IPluginIOStack input, final IPluginIOStack output, final ProgressReporter p,
			PreviewType previewType, boolean inputHasChanged) throws InterruptedException {

		cancelled = false;

		IPluginIOStack auxStack = null;

		PluginIOImage auxInput = (PluginIOImage) pluginInputs.get("Input 2");
		if (auxInput instanceof PluginIOHyperstack)
			auxStack = ((PluginIOHyperstack) auxInput).getChannels().values().iterator().next();
		else if (auxInput instanceof IPluginIOStack)
			auxStack = (IPluginIOStack) auxInput;
		if (auxStack == null)
			throw new RuntimeException("Auxiliary input " + auxInput + " missing or not of the right kind");

		if ((auxStack.getWidth() != input.getWidth()) || (auxStack.getHeight() != input.getHeight())) {
			throw new RuntimeException("Dimension mismatch");
		}

		progressSetIndeterminateThreadSafe(p, true);
		progressSetValueThreadSafe(p, 0);
		indeterminateProgress = true;

		nCpus = Runtime.getRuntime().availableProcessors();
		if (input.getDepth() < nCpus)
			nCpus = input.getDepth();

		input.computePixelArray();
		auxStack.computePixelArray();
		output.computePixelArray();

		final Object[] inputSlices = input.getStackPixelArray();
		final Object[] outputSlices = output.getStackPixelArray();
		final Object[] auxSlices = auxStack.getStackPixelArray();

		slice_registry.set(0);
		threads = newThreadArray();

		final int depth = input.getDepth();

		for (int ithread = 0; ithread < nCpus; ithread++) {
			threads[ithread] = new Thread("Image math worker thread") {
				@Override
				public void run() {
					try {

						for (int z = slice_registry.getAndIncrement(); z < depth; z = slice_registry.getAndIncrement()) {

							float[] inputArray = (float[]) inputSlices[z];
							float[] outputArray = (float[]) outputSlices[z];
							float[] auxArray = (float[]) auxSlices[z];

							for (int i = 0; i < inputArray.length; i++) {
								switch (operation) {
									case "Multiplication":
										outputArray[i] = inputArray[i] * auxArray[i];
										break;
									case "Weighted addition":
										outputArray[i] = inputArray[i] + auxArray[i] * weight + offset;
										break;
									default:
										throw new IllegalStateException("Unknown opearation " + operation);
								}
							}

							int our_progress = ((int) (100.0 * ((z)) / (depth)));
							if (our_progress > p.getValue())
								progressSetValueThreadSafe(p, our_progress); // not perfect but at least does not
																				// required synchronization
							if (indeterminateProgress) {
								progressSetIndeterminateThreadSafe(p, false);
								indeterminateProgress = false;
							}
							if (Thread.interrupted()) {
								cancelled = true;
							}
							if (cancelled)
								return;
						}
					} catch (Exception e) {
						Utils.printStack(e);
					}
				}
			};
		}
		startAndJoin(threads);

		if (cancelled)
			throw new InterruptedException();
	}

	@Override
	public Map<String, InputOutputDescription> getInputDescriptions() {
		HashMap<String, InputOutputDescription> result = new HashMap<>();
		result.put("Default source", new InputOutputDescription(null, null, new PixelType[] { PixelType.FLOAT_TYPE },
				InputOutputDescription.NOT_SPECIFIED, InputOutputDescription.NOT_SPECIFIED, false, false));
		return result;
	}

	@Override
	public Map<String, InputOutputDescription> getOutputDescriptions() {
		HashMap<String, InputOutputDescription> result = new HashMap<>();
		result.put("Default destination",
				new InputOutputDescription(null, null, null, InputOutputDescription.KEEP_IN_RAM,
						InputOutputDescription.SAME_XY_MATCH_SELECTED_CHANNELS, true, false));
		return result;
	}

	@Override
	public String[] getInputLabels() {
		return new String[] { "Input 2" };
	}

	@Override
	public String[] getOutputLabels() {
		return new String[] {};
	}

}