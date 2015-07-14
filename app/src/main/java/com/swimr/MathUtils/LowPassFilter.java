package com.swimr.MathUtils;

/**
 * Created by aaa on 7/12/15.
 */
public class LowPassFilter {

	static final float ALPHA = 0.15f;

	static public float[] lowPassFilterVector(float[] input, float[] output){

		if(output == null || output.length < input.length)
			return input;

		for(int i=0; i<input.length; i++){
			output[i] = output[i] + ALPHA * (input[i] - output[i]);
		}
		return output;
	}

	static public float lowPassFilterFloat(float input, float output){

		output = output + ALPHA * (input - output);
		return output;
	}


}
