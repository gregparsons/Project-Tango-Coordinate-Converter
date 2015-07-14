package com.swimr.MathUtils;

/**
 * Created by aaa on 7/13/15.
 */
public class GpMath {
	/**
	 * Rotate a two-dimensional point/vector by theta degrees to the clockwise.
	 * @return
	 */
	public static Double[] rotate2dClockwise(Double startx, Double starty, Double hdg){
		//https://en.wikipedia.org/wiki/Rotation_matrix
		// Theta = negative heading (aka clockwise rotation)
		Double theta = Math.toRadians(-hdg);

		//2D rotation matrix
		Double[] newValue  =  new Double[2];
		newValue[0] = startx * (Math.cos(theta)) + starty * (-Math.sin(theta));
		newValue[1] = startx * (Math.sin(theta)) + starty * (Math.cos(theta));
		//System.out.println("startx: " + startx + ", starty: " + starty + ", hdg: " + hdg + "\nnewx: " + newValue[0] + ", newy: " + newValue[1]);

		return newValue;

	}

	/**
	 * Helper to make sure azimuth values stay between 0 and 359.
	 * @param heading
	 * @return
	 */
	public static double fixHeadingBetween0and359(double heading) {
		// Make sure bearing stays between 0-359
		if(heading<0) {
			heading += 360;
		}
		else if(heading>=360) {
			heading -= 360;
		}
		return heading;
	}








	// matrix-vector multiplication (y = A x X)
	public static float[] vectorCrossProduct(float[] r, float[] p) {

		float newX = r[0]*p[0] + r[3]*p[1] + r[6]*p[2];
		float newY = r[1]*p[0] + r[4]*p[1] + r[7]*p[2];
		float newZ = r[2]*p[0] + r[5]*p[1] + r[8]*p[2];

		float[] returnVector = new float[3];
		returnVector[0] = newX;
		returnVector[1] = newY;
		returnVector[2] = newZ;

		return returnVector;
	}


}
